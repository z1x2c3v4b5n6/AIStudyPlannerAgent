package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OfficialDocuments {
    private static final Map<String,List<String>> ALLOWED = Map.of(
            "docs.spring.io", List.of("/spring-framework/","/spring-boot/","/spring-ai/"),
            "docs.oracle.com", List.of("/en/java/"),
            "dev.java", List.of("/learn/"));
    private static final int MAX_BYTES = 512 * 1024;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final Semaphore concurrent = new Semaphore(2);
    private final boolean enabled;

    OfficialDocuments(@Value("${app.knowledge.online-enabled:false}") boolean enabled) { this.enabled=enabled; }
    boolean enabled() { return enabled; }

    static URI validate(String value) {
        try {
            var uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getPort()!=-1 || uri.getRawUserInfo()!=null
                    || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !uri.getPath().equals(uri.getRawPath())
                    || uri.getPath().contains("//") || !uri.normalize().equals(uri)
                    || !ALLOWED.getOrDefault(uri.getHost(),List.of()).stream().anyMatch(uri.getPath()::startsWith))
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    record Captured(String title, String text) {}

    private record Payload(String text, String contentType) {}

    Captured fetch(KnowledgeHub.Source source) {
        var page=download(source.url());
        return page.contentType().startsWith("text/html") ? cleanHtml(page.text(),source.name())
                : new Captured(source.name(),checked(page.text()));
    }

    String catalog(String url) {
        var page=download(url);
        if(!page.contentType().startsWith("text/html")) throw unavailable();
        return page.text();
    }

    private Payload download(String url) {
        if (!enabled) throw new FetchFailure("DISABLED");
        if (!concurrent.tryAcquire()) throw new FetchFailure("CAPACITY");
        try {
            var request = HttpRequest.newBuilder(validate(url)).GET().timeout(Duration.ofSeconds(6))
                    .header("Accept", "text/html,text/plain,text/markdown")
                    .header("User-Agent","AIStudyPlannerKnowledgeHub/1.0").build();
            var content = http.sendAsync(request, info -> {
                String kind = info.headers().firstValue("Content-Type").orElse("").split(";",2)[0].toLowerCase(Locale.ROOT).strip();
                if (info.statusCode()!=200) throw new FetchFailure("HTTP_"+info.statusCode());
                if (!List.of("text/html","text/plain","text/markdown").contains(kind)) throw new FetchFailure("CONTENT_TYPE");
                if (info.headers().firstValueAsLong("Content-Length").orElse(0)>MAX_BYTES)
                    throw new FetchFailure("CONTENT_SIZE");
                return limitedBody();
            });
            HttpResponse<byte[]> result;
            try { result = content.get(7,TimeUnit.SECONDS); }
            catch (TimeoutException | InterruptedException failure) {
                content.cancel(true);
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new FetchFailure("INTERRUPTED");
                }
                throw new FetchFailure("TIMEOUT");
            }
            String contentType=result.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            String text=decode(result.body(),contentType);
            return new Payload(text,contentType);
        } catch (ExecutionException failure) {
            throw new FetchFailure(transportCategory(failure));
        } finally {
            concurrent.release();
        }
    }

    static Captured cleanHtml(String html, String fallback) {
        var doc = Jsoup.parse(html);
        var section = doc.selectFirst("main, article, #content");
        if (section==null) section=doc.body();
        section.select("script,style,nav,header,footer,aside,form,iframe,noscript,template,[hidden],[aria-hidden=true]").remove();
        var output=new StringBuilder();
        for(var block:section.select("h1,h2,h3,h4,h5,h6,p,li,pre,table")) {
            if (block.parent()!=null && block.parent().closest("li,pre,table")!=null) continue;
            switch(block.normalName()) {
                case "pre" -> output.append("```\n").append(block.wholeText()).append("\n```");
                case "table" -> {
                    for (Element row:block.select("tr")) output.append(row.children().eachText().stream()
                            .collect(java.util.stream.Collectors.joining(" | "))).append('\n');
                }
                case "li" -> output.append("- ").append(block.text());
                default -> {
                    if(block.normalName().startsWith("h")) output.append("#".repeat(Integer.parseInt(block.normalName().substring(1)))).append(' ');
                    output.append(block.text());
                }
            }
            output.append("\n\n");
            if(output.length()>100000) throw unavailable();
        }
        String title=doc.title().isBlank()?fallback:doc.title();
        return new Captured(EvidenceText.shorten(title.strip(),200),checked(output.toString()));
    }

    private static String checked(String text) {
        if (text.isBlank() || text.length()>100000) throw unavailable();
        return text.strip();
    }

    private static HttpResponse.BodySubscriber<byte[]> limitedBody() {
        return new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<byte[]> result = new CompletableFuture<>();
            private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            private Flow.Subscription subscription;
            @Override public CompletionStage<byte[]> getBody() { return result; }
            @Override public void onSubscribe(Flow.Subscription request) { subscription=request; request.request(1); }
            @Override public void onNext(List<ByteBuffer> parts) {
                for(var part:parts) {
                    if (part.remaining()>MAX_BYTES-bytes.size()) {
                        subscription.cancel();
                        result.completeExceptionally(new FetchFailure("CONTENT_SIZE"));
                        return;
                    }
                    var chunk=new byte[part.remaining()];
                    part.get(chunk);
                    bytes.writeBytes(chunk);
                }
                subscription.request(1);
            }
            @Override public void onError(Throwable error) { result.completeExceptionally(error); }
            @Override public void onComplete() { result.complete(bytes.toByteArray()); }
        };
    }

    static String decode(byte[] bytes,String contentType) {
        var declared=java.util.regex.Pattern.compile("(?i);\\s*charset\\s*=\\s*["']?([^;"'\\s]+)").matcher(contentType);
        try {
            Charset charset=declared.find()?Charset.forName(declared.group(1)):StandardCharsets.UTF_8;
            if (!List.of(StandardCharsets.UTF_8,StandardCharsets.US_ASCII).contains(charset))
                throw new FetchFailure("ENCODING");
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException | CharacterCodingException failure) {
            throw new FetchFailure("ENCODING");
        }
    }

    static String transportCategory(Throwable failure) {
        for(Throwable current=failure;current!=null;current=current.getCause()){
            if(current instanceof FetchFailure known) return known.category();
            if(current instanceof javax.net.ssl.SSLException) return "TLS";
            if(current instanceof java.net.UnknownHostException || current instanceof java.nio.channels.UnresolvedAddressException)
                return "DNS";
            if(current instanceof HttpTimeoutException || current instanceof TimeoutException) return "TIMEOUT";
            // The JDK CONNECT error has no structured status accessor.
            if(current instanceof java.io.IOException && current.getMessage()!=null
                    && current.getMessage().contains("407")) return "PROXY_AUTH";
        }
        return "TRANSPORT";
    }

    static final class FetchFailure extends BusinessException {
        private final String category;
        FetchFailure(String category) {
            super(ErrorCode.KNOWLEDGE_SOURCE_UNAVAILABLE);
            this.category=category;
        }
        String category(){return category;}
    }

    private static BusinessException unavailable() { return new BusinessException(ErrorCode.KNOWLEDGE_SOURCE_UNAVAILABLE); }
}

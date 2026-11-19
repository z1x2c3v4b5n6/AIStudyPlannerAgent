package com.yhk.aistudyplanner.knowledge.hub;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class EvidenceText {
    private static final Pattern TERMS = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9][a-z0-9.+-]+");
    record Chunk(int ordinal, String heading, int line, String excerpt) {}

    static List<Chunk> split(String title, String text) {
        var chunks = new ArrayList<Chunk>();
        var buffered = new StringBuilder();
        String heading = title;
        int start = 1;
        boolean fence = false;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean section = !fence && line.matches("#{1,6} .+");
            if ((section || buffered.length() + line.length() > 1200) && !buffered.isEmpty()) {
                append(chunks, heading, start, buffered.toString());
                buffered.setLength(0);
            }
            if (section) heading = shorten(line.replaceFirst("^#+ ", ""), 200);
            if (buffered.isEmpty()) start = i + 1;
            if (line.stripLeading().startsWith("```")) fence = !fence;
            if (line.length() > 1200) {
                for (int offset = 0; offset < line.length();) {
                    String part = shorten(line.substring(offset), 1200);
                    append(chunks, heading, start, part);
                    offset += part.length();
                }
            } else buffered.append(line).append('\n');
        }
        append(chunks, heading, start, buffered.toString());
        return List.copyOf(chunks);
    }

    private static void append(List<Chunk> chunks, String heading, int line, String text) {
        if (!text.isBlank()) chunks.add(new Chunk(chunks.size() + 1, heading, line, text.strip()));
    }

    static String shorten(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max);
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    static List<String> terms(String value) {
        return TERMS.matcher(normalize(value)).results().flatMap(match -> {
            String term = match.group();
            if (term.codePoints().allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                    && term.length() > 4) {
                return java.util.stream.IntStream.range(0, term.length() - 1)
                        .filter(index -> !Character.isSurrogate(term.charAt(index)) && !Character.isSurrogate(term.charAt(index + 1)))
                        .mapToObj(index -> term.substring(index, index + 2));
            }
            return java.util.stream.Stream.of(term);
        }).filter(term -> !List.of("how", "the", "for", "and", "latest", "最新", "如何", "什么", "请问").contains(term))
                .distinct().limit(8).toList();
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}

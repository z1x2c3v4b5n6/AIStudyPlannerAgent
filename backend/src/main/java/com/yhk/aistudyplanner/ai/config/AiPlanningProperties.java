package com.yhk.aistudyplanner.ai.config;
import jakarta.validation.constraints.*;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.validation.annotation.Validated;

@Validated @ConfigurationProperties(prefix="app.ai")
public class AiPlanningProperties {
 private boolean enabled=false;@NotBlank private String baseUrl="https://api.deepseek.com";@NotBlank private String completionsPath="/chat/completions";private String apiKey="";@NotBlank private String model="deepseek-v4-flash";@DecimalMin("0.0")@DecimalMax("2.0") private double temperature=.2;@Min(1) private int maxTokens=2500;@Min(1)@Max(300) private int timeoutSeconds=45;
 public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String v){baseUrl=v;}public String getCompletionsPath(){return completionsPath;}public void setCompletionsPath(String v){completionsPath=v;}public String getApiKey(){return apiKey;}public void setApiKey(String v){apiKey=v;}public String getModel(){return model;}public void setModel(String v){model=v;}public double getTemperature(){return temperature;}public void setTemperature(double v){temperature=v;}public int getMaxTokens(){return maxTokens;}public void setMaxTokens(int v){maxTokens=v;}public int getTimeoutSeconds(){return timeoutSeconds;}public void setTimeoutSeconds(int v){timeoutSeconds=v;}
 public boolean configured(){return enabled&&apiKey!=null&&!apiKey.isBlank();}
}

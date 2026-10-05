package dev.pt.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "risk.ai")
public record AiSettings(String apiKey, String model, String baseUrl) {}

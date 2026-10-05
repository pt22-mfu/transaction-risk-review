package dev.pt.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({Policy.class, AiSettings.class})
public class RiskApplication {
    public static void main(String[] args) { SpringApplication.run(RiskApplication.class, args); }
}

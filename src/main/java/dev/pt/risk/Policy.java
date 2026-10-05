package dev.pt.risk;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configurable demonstration rules, not a bank policy or a calibrated fraud model. */
@Validated
@ConfigurationProperties(prefix = "risk.policy")
public record Policy(
        @Min(2) int burstCount, @Min(1) int burstMinutes,
        @DecimalMin("1.01") BigDecimal unusualMultiplier,
        @DecimalMin("0.01") BigDecimal largeAmount,
        @Min(1) int minimumHistory,
        @DecimalMin("0.01") BigDecimal rapidIncomingMinimum,
        @DecimalMin("0.01") @DecimalMax("1") BigDecimal rapidOutflowRatio,
        @Min(1) int rapidMinutes, @Min(1) int dormantDays) {}

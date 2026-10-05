package dev.pt.risk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class Models {
    private Models() {}
    public record Account(String id, String displayName, String scenario, String description) {}
    public record Transaction(String id, String accountId, OffsetDateTime occurredAt,
                              String direction, BigDecimal amount, String counterparty, String description) {}
    public record Finding(String ruleId, String title, int points, String explanation,
                          List<String> transactionIds, String alternativeExplanation) {}
    public record Analysis(String accountId, OffsetDateTime asOf, OffsetDateTime windowStart,
                           int riskScore, String priority, int historicalOutgoingCount,
                           BigDecimal historicalMedian, BigDecimal incomingTotal, BigDecimal outgoingTotal,
                           List<Finding> findings, String scope) {}
    public record Review(String id, String accountId, String status, String note, OffsetDateTime createdAt) {}
    public record AccountDetail(Account account, List<Transaction> transactions, Analysis analysis, List<Review> reviews) {}
    public record Explanation(String source, String summary, List<String> nextSteps, String limitation) {}
}

package dev.pt.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import dev.pt.risk.Models.*;

@Service
public class RiskEngine {
    private final Policy policy;
    public RiskEngine(Policy policy) { this.policy = policy; }

    public Analysis analyze(String accountId, List<Transaction> input, OffsetDateTime asOf) {
        OffsetDateTime start = asOf.minusHours(24);
        List<Transaction> all = input.stream().filter(t -> t.accountId().equals(accountId))
                .filter(t -> !t.occurredAt().isAfter(asOf))
                .sorted(Comparator.comparing(Transaction::occurredAt).thenComparing(Transaction::id)).toList();
        List<Transaction> history = all.stream().filter(t -> t.occurredAt().isBefore(start)).toList();
        List<Transaction> baseline = history.stream().filter(t -> !t.occurredAt().isBefore(start.minusDays(30)))
                .filter(t -> t.direction().equals("OUT")).toList();
        List<Transaction> window = all.stream().filter(t -> !t.occurredAt().isBefore(start)).toList();
        List<Transaction> outgoing = window.stream().filter(t -> t.direction().equals("OUT")).toList();
        BigDecimal median = median(baseline);
        List<Finding> findings = new ArrayList<>();

        // Sliding event-time window. Each rule contributes its score at most once.
        List<Transaction> burst = List.of();
        for (int i = 0; i < outgoing.size(); i++) {
            int j = i;
            while (j < outgoing.size() && !outgoing.get(j).occurredAt().isAfter(outgoing.get(i).occurredAt().plusMinutes(policy.burstMinutes()))) j++;
            if (j - i >= policy.burstCount()) { burst = outgoing.subList(i, j); break; }
        }
        if (!burst.isEmpty()) findings.add(finding("BURST", "Transfer burst", 25,
                burst.size() + " outgoing transfers within " + policy.burstMinutes() + " minutes.", burst,
                "A planned batch of supplier payments may explain this activity."));

        if (baseline.size() >= policy.minimumHistory()) {
            List<Transaction> unusual = outgoing.stream().filter(t -> t.amount().compareTo(policy.largeAmount()) >= 0)
                    .filter(t -> t.amount().compareTo(median.multiply(policy.unusualMultiplier())) > 0).toList();
            if (!unusual.isEmpty()) findings.add(finding("AMOUNT", "Amount above baseline", 20,
                    "Transfers exceed " + policy.unusualMultiplier() + "x the previous 30-day outgoing median of THB " + median + ".", unusual,
                    "A legitimate one-off purchase can be much larger than normal spending."));
        }

        Set<String> known = new HashSet<>();
        history.stream().filter(t -> t.direction().equals("OUT")).forEach(t -> known.add(t.counterparty()));
        List<Transaction> newRecipients = outgoing.stream().filter(t -> t.amount().compareTo(policy.largeAmount()) >= 0)
                .filter(t -> !known.contains(t.counterparty())).toList();
        if (!newRecipients.isEmpty()) findings.add(finding("RECIPIENT", "Large transfer to new recipient", 15,
                "Recipients have not appeared in the supplied history before this review window; amounts are at least THB " + policy.largeAmount() + ".", newRecipients,
                "The supplied history may be incomplete, or these may be new suppliers."));

        // Stop each inbound window at the next inbound deposit. This avoids counting
        // another deposit's outflow twice as evidence for the first deposit.
        List<Transaction> rapid = new ArrayList<>();
        for (int i = 0; i < window.size(); i++) {
            Transaction deposit = window.get(i);
            if (!deposit.direction().equals("IN") || deposit.amount().compareTo(policy.rapidIncomingMinimum()) < 0) continue;
            List<Transaction> after = new ArrayList<>();
            for (int j = i + 1; j < window.size(); j++) {
                Transaction t = window.get(j);
                if (t.occurredAt().isAfter(deposit.occurredAt().plusMinutes(policy.rapidMinutes()))) break;
                if (t.direction().equals("IN")) break;
                // Equal timestamps do not establish an ordering and are not evidence of 'after'.
                if (t.direction().equals("OUT") && t.occurredAt().isAfter(deposit.occurredAt())) after.add(t);
            }
            if (sum(after).compareTo(deposit.amount().multiply(policy.rapidOutflowRatio())) >= 0) {
                rapid.add(deposit); rapid.addAll(after); break;
            }
        }
        if (!rapid.isEmpty()) findings.add(finding("RAPID", "Rapid movement of incoming funds", 25,
                "At least " + policy.rapidOutflowRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                + "% of an incoming deposit was transferred out within " + policy.rapidMinutes() + " minutes, before another deposit.", rapid,
                "A scheduled payment funded by a recent deposit can produce the same pattern."));

        if (!window.isEmpty() && !outgoing.isEmpty() && !history.isEmpty()) {
            Transaction prior = history.get(history.size() - 1);
            Transaction resumed = window.get(0);
            if (Duration.between(prior.occurredAt(), resumed.occurredAt()).compareTo(Duration.ofDays(policy.dormantDays())) >= 0)
                findings.add(finding("DORMANT", "Activity after a long gap", 15,
                        "No supplied transactions for at least " + policy.dormantDays() + " days before activity resumed.",
                        List.of(prior, resumed, outgoing.get(0)).stream().distinct().toList(),
                        "A seasonal account or incomplete records may explain the gap."));
        }
        int score = Math.min(100, findings.stream().mapToInt(Finding::points).sum());
        return new Analysis(accountId, asOf, start, score, score >= 50 ? "HIGH" : score >= 20 ? "MEDIUM" : "LOW",
                baseline.size(), median, sum(window.stream().filter(t -> t.direction().equals("IN")).toList()), sum(outgoing),
                List.copyOf(findings), "Synthetic demo; a heuristic review score, not a fraud probability. No triggered rules does not establish safety.");
    }

    private static Finding finding(String id, String title, int points, String explanation, List<Transaction> ts, String alternative) {
        return new Finding(id, title, points, explanation, ts.stream().map(Transaction::id).distinct().toList(), alternative);
    }
    private static BigDecimal sum(List<Transaction> ts) { return ts.stream().map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static BigDecimal median(List<Transaction> ts) {
        if (ts.isEmpty()) return BigDecimal.ZERO;
        List<BigDecimal> values = ts.stream().map(Transaction::amount).sorted().toList();
        int mid = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(mid) : values.get(mid - 1).add(values.get(mid)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}

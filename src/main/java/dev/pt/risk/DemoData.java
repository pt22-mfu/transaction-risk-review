package dev.pt.risk;

import dev.pt.risk.Models.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoData implements ApplicationRunner {
    private final RiskRepository repository;
    private final boolean seed;
    public DemoData(RiskRepository repository, @Value("${risk.seed}") boolean seed) { this.repository = repository; this.seed = seed; }
    @Override public void run(ApplicationArguments args) { if (seed) repository.seed(accounts(), transactions()); }
    public static List<Account> accounts() {
        return List.of(
            new Account("DEMO-001", "Everyday account", "Routine spending", "Salary, groceries and regular bills. No configured rules are expected to trigger."),
            new Account("DEMO-002", "Small business account", "Planned supplier payment", "A legitimate large payment to a new supplier triggers review. This demonstrates a false-positive scenario, not a known fraud case."),
            new Account("DEMO-003", "Reactivated account", "Transfer burst", "After a long activity gap, an incoming deposit is quickly distributed to five new recipients. Fictional activity designed to exercise multiple rules.")
        );
    }
    public static List<Transaction> transactions() {
        List<Transaction> ts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String day = String.format("2026-09-%02dT09:00:00Z", 15 + i * 2);
            ts.add(tx("R-H" + i, "DEMO-001", day, "OUT", String.valueOf(300 + i * 50), "GROCERY", "Regular groceries"));
            ts.add(tx("B-H" + i, "DEMO-002", day, "OUT", String.valueOf(900 + i * 100), "SUPPLIER-OLD", "Regular supplies"));
        }
        ts.add(tx("R-101", "DEMO-001", "2026-10-05T07:00:00Z", "IN", "28000", "EMPLOYER", "Monthly salary"));
        ts.add(tx("R-102", "DEMO-001", "2026-10-05T08:00:00Z", "OUT", "550", "GROCERY", "Groceries"));
        ts.add(tx("R-103", "DEMO-001", "2026-10-05T09:15:00Z", "OUT", "1200", "UTILITIES", "Electricity bill"));
        ts.add(tx("B-101", "DEMO-002", "2026-10-05T07:00:00Z", "IN", "75000", "CLIENT", "Client payment"));
        ts.add(tx("B-102", "DEMO-002", "2026-10-05T10:00:00Z", "OUT", "30000", "SUPPLIER-NEW", "Planned equipment purchase"));
        ts.add(tx("S-H01", "DEMO-003", "2026-08-20T09:00:00Z", "OUT", "700", "KNOWN-SHOP", "Prior activity"));
        ts.add(tx("S-101", "DEMO-003", "2026-10-05T09:00:00Z", "IN", "100000", "EXTERNAL-SENDER", "Incoming transfer"));
        for (int i = 0; i < 5; i++) ts.add(tx("S-" + (102 + i), "DEMO-003", String.format("2026-10-05T09:%02d:00Z", 2 + i * 2),
                "OUT", "18000", "NEW-RECIPIENT-" + (i + 1), "Outgoing transfer"));
        return List.copyOf(ts);
    }
    private static Transaction tx(String id, String account, String time, String direction, String amount, String party, String description) {
        return new Transaction(id, account, OffsetDateTime.parse(time), direction, new BigDecimal(amount), party, description);
    }
}

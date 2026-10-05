package dev.pt.risk;

import dev.pt.risk.Models.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Repository
public class RiskRepository {
    private final JdbcTemplate jdbc;
    public RiskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Account> accounts() {
        return jdbc.query("SELECT * FROM risk_demo.accounts ORDER BY id", (r,n) ->
                new Account(r.getString("id"), r.getString("display_name"), r.getString("scenario"), r.getString("description")));
    }
    public Account account(String id) {
        return accounts().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }
    public List<Transaction> transactions(String id) {
        return jdbc.query("SELECT * FROM risk_demo.transactions WHERE account_id = ? ORDER BY occurred_at, id", (r,n) ->
                new Transaction(r.getString("id"), r.getString("account_id"), r.getObject("occurred_at", OffsetDateTime.class),
                        r.getString("direction"), r.getBigDecimal("amount"), r.getString("counterparty"), r.getString("description")), id);
    }
    public List<Review> reviews(String id) {
        return jdbc.query("SELECT * FROM risk_demo.reviews WHERE account_id = ? ORDER BY created_at DESC", (r,n) ->
                new Review(r.getString("id"), r.getString("account_id"), r.getString("status"), r.getString("note"),
                        r.getObject("created_at", OffsetDateTime.class)), id);
    }
    public OffsetDateTime reviewSnapshot(String id, OffsetDateTime fallback) {
        List<OffsetDateTime> result = jdbc.query("SELECT review_as_of FROM risk_demo.imports WHERE account_id = ?",
                (r,n) -> r.getObject("review_as_of",OffsetDateTime.class),id);
        return result.isEmpty() ? fallback : result.get(0);
    }
    @Transactional
    public void importAccount(Account account, List<Transaction> transactions, OffsetDateTime snapshot) {
        jdbc.update("INSERT INTO risk_demo.accounts VALUES (?, ?, ?, ?)", account.id(), account.displayName(), account.scenario(), account.description());
        for (Transaction t : transactions) jdbc.update("INSERT INTO risk_demo.transactions VALUES (?, ?, ?, ?, ?, ?, ?)",
                t.id(),t.accountId(),t.occurredAt(),t.direction(),t.amount(),t.counterparty(),t.description());
        jdbc.update("INSERT INTO risk_demo.imports VALUES (?, ?, ?, ?)",account.id(),snapshot,transactions.size(),OffsetDateTime.now());
    }
    public Review addReview(String accountId, String status, String note) {
        account(accountId);
        Review review = new Review(UUID.randomUUID().toString(), accountId, status, note.trim(), OffsetDateTime.now());
        jdbc.update("INSERT INTO risk_demo.reviews(id, account_id, status, note, created_at) VALUES (?, ?, ?, ?, ?)",
                review.id(), review.accountId(), review.status(), review.note(), review.createdAt());
        return review;
    }
    @Transactional
    public void seed(List<Account> accounts, List<Transaction> transactions) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM risk_demo.accounts", Integer.class);
        if (count != null && count > 0) return;
        for (Account a : accounts) jdbc.update("INSERT INTO risk_demo.accounts VALUES (?, ?, ?, ?)", a.id(), a.displayName(), a.scenario(), a.description());
        for (Transaction t : transactions) jdbc.update("INSERT INTO risk_demo.transactions VALUES (?, ?, ?, ?, ?, ?, ?)",
                t.id(), t.accountId(), t.occurredAt(), t.direction(), t.amount(), t.counterparty(), t.description());
    }
}

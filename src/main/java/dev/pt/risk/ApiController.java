package dev.pt.risk;

import dev.pt.risk.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final RiskRepository repository;
    private final RiskEngine engine;
    private final ExplanationService explanations;
    private final Policy policy;
    private final OffsetDateTime asOf;
    private final String reviewerToken;
    private final CsvImportService importer;
    public ApiController(RiskRepository repository, RiskEngine engine, ExplanationService explanations, Policy policy,
                         @Value("${risk.as-of}") String asOf, @Value("${risk.reviewer-token}") String reviewerToken, CsvImportService importer) {
        this.repository = repository; this.engine = engine; this.explanations = explanations; this.policy = policy;
        this.asOf = OffsetDateTime.parse(asOf); this.reviewerToken = reviewerToken;
        this.importer=importer;
    }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "ok"); }
    @GetMapping("/meta") public Map<String,Object> meta() {
        return Map.of("asOf", asOf, "policy", policy, "aiConfigured", explanations.configured(),
                "reviewerConfigured", !reviewerToken.isBlank(), "dataMode", "SYNTHETIC", "version", "0.2.0");
    }
    @GetMapping("/accounts") public List<Map<String,Object>> accounts() {
        return repository.accounts().stream().map(a -> {
            Analysis analysis = analyze(a.id());
            List<Review> reviews = repository.reviews(a.id());
            return Map.<String,Object>of("account", a, "analysis", analysis,
                    "status", reviews.isEmpty() ? "PENDING_REVIEW" : reviews.get(0).status());
        }).toList();
    }
    @GetMapping("/accounts/{id}") public AccountDetail detail(@PathVariable String id) {
        Account account = repository.account(id);
        return new AccountDetail(account, repository.transactions(id), analyze(id), repository.reviews(id));
    }
    @GetMapping("/accounts/{id}/analysis") public Analysis analysis(@PathVariable String id) { repository.account(id); return analyze(id); }
    @PostMapping("/accounts/{id}/explanation") public Explanation explanation(@PathVariable String id,
            @RequestHeader(value="X-Reviewer-Token", required=false) String token) {
        repository.account(id);
        // No-key mode is a clearly labelled template. Live paid/provider calls require a token.
        if (explanations.configured()) authorize(token);
        return explanations.explain(analyze(id));
    }
    public record ReviewInput(@NotBlank @Pattern(regexp="PENDING_REVIEW|EXPLAINED|ESCALATED") String status,
                              @NotBlank @Size(max=2000) String note) {}
    @PostMapping("/accounts/{id}/reviews") @ResponseStatus(HttpStatus.CREATED)
    public Review review(@PathVariable String id, @Valid @RequestBody ReviewInput input,
            @RequestHeader(value="X-Reviewer-Token", required=false) String token) {
        authorize(token); return repository.addReview(id, input.status(), input.note());
    }
    @GetMapping("/reports/daily") public Map<String,Object> daily(@RequestHeader(value="X-Reviewer-Token", required=false) String token) {
        authorize(token); return Map.of("asOf", asOf, "accounts", accounts(), "scope", "Fictional account snapshots; see each analysis.asOf for its review cutoff. Not a live bank feed.");
    }
    @PostMapping(value="/imports",consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
    public CsvImportService.Imported importCsv(@RequestParam String displayName, @RequestParam boolean syntheticConfirmed,
            @RequestParam MultipartFile file, @RequestHeader(value="X-Reviewer-Token", required=false) String token) {
        authorize(token);return importer.importFile(displayName,syntheticConfirmed,file);
    }
    private Analysis analyze(String id) { return engine.analyze(id, repository.transactions(id), repository.reviewSnapshot(id,asOf)); }
    private void authorize(String supplied) {
        if (reviewerToken.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Reviewer access is not configured");
        if (supplied == null || !MessageDigest.isEqual(reviewerToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reviewer token required");
    }
}

package dev.pt.risk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "risk.reviewer-token=test-only-token", "risk.ai.api-key="})
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;
    @Test void listsThreeSeededAccounts() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }
    @Test void detailIncludesEvidenceAndHistory() throws Exception {
        mvc.perform(get("/api/accounts/DEMO-003")).andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.riskScore").value(80)).andExpect(jsonPath("$.transactions.length()").value(7));
    }
    @Test void unknownAccountReturns404() throws Exception { mvc.perform(get("/api/accounts/NO-SUCH-ID")).andExpect(status().isNotFound()); }
    @Test void noKeyExplanationIsClearlyLabeled() throws Exception {
        mvc.perform(post("/api/accounts/DEMO-003/explanation")).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE_BASED"));
    }
    @Test void cannotSaveReviewWithoutToken() throws Exception {
        mvc.perform(post("/api/accounts/DEMO-001/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EXPLAINED\",\"note\":\"Synthetic test\"}")).andExpect(status().isUnauthorized());
    }
    @Test void savesReviewWithTokenAndRetrievesAuditHistory() throws Exception {
        mvc.perform(post("/api/accounts/DEMO-001/reviews").header("X-Reviewer-Token","test-only-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EXPLAINED\",\"note\":\"Routine fictional activity checked.\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EXPLAINED"));
        mvc.perform(get("/api/accounts/DEMO-001")).andExpect(jsonPath("$.reviews[0].note").value("Routine fictional activity checked."));
    }
    @Test void validatesStatusAndBlankNote() throws Exception {
        mvc.perform(post("/api/accounts/DEMO-001/reviews").header("X-Reviewer-Token","test-only-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"FRAUD\",\"note\":\" \"}")).andExpect(status().isBadRequest());
    }
    @Test void rejectsOversizedNote() throws Exception {
        mvc.perform(post("/api/accounts/DEMO-001/reviews").header("X-Reviewer-Token","test-only-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"EXPLAINED\",\"note\":\"" + "x".repeat(2001) + "\"}")).andExpect(status().isBadRequest());
    }
    @Test void dailyReportRequiresAccess() throws Exception {
        mvc.perform(get("/api/reports/daily")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/daily").header("X-Reviewer-Token","test-only-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(3));
    }
    @Test void metadataDoesNotExposeToken() throws Exception {
        mvc.perform(get("/api/meta")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewerConfigured").value(true))
                .andExpect(jsonPath("$.reviewerToken").doesNotExist());
    }
}

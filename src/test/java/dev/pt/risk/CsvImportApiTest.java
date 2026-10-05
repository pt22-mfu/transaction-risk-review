package dev.pt.risk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:import-api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "risk.reviewer-token=import-test-token","risk.ai.api-key="})
@AutoConfigureMockMvc
class CsvImportApiTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    private static final String HEADER="transaction_id,occurred_at,direction,amount,counterparty,description\n";
    private MockMultipartFile file(String rows) { return new MockMultipartFile("file","demo.csv","text/csv",(HEADER+rows).getBytes(StandardCharsets.UTF_8)); }
    @BeforeEach void removeImportedAccounts() {
        for(String table:new String[]{"reviews","imports","transactions"})jdbc.update("DELETE FROM risk_demo."+table+" WHERE account_id LIKE 'CUSTOM-%'");
        jdbc.update("DELETE FROM risk_demo.accounts WHERE id LIKE 'CUSTOM-%'");
    }
    @Test void unauthorizedImportCreatesNothing() throws Exception {
        mvc.perform(multipart("/api/imports").file(file("T1,2027-01-04T09:00:00Z,OUT,100,A,\n"))
                .param("displayName","Demo").param("syntheticConfirmed","true")).andExpect(status().isUnauthorized());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM risk_demo.accounts",Integer.class));
    }
    @Test void rejectsBadBatchWithoutPartialData() throws Exception {
        mvc.perform(multipart("/api/imports").file(file("T1,2027-01-04T09:00:00Z,OUT,100,A,\nT2,2027-01-04T09:00:00Z,OUT,0,A,\n"))
                .header("X-Reviewer-Token","import-test-token").param("displayName","Demo").param("syntheticConfirmed","true"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.issues[0]").exists());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM risk_demo.accounts",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM risk_demo.transactions WHERE account_id LIKE 'CUSTOM-%'",Integer.class));
    }
    @Test void requiresFictionalDataConfirmation() throws Exception {
        mvc.perform(multipart("/api/imports").file(file("T1,2027-01-04T09:00:00Z,OUT,100,A,\n"))
                .header("X-Reviewer-Token","import-test-token").param("displayName","Demo").param("syntheticConfirmed","false"))
                .andExpect(status().isBadRequest());
    }
    @Test void importedAccountUsesOwnSnapshotAndAppearsInReport() throws Exception {
        String body=mvc.perform(multipart("/api/imports").file(file("T1,2027-01-04T09:00:00Z,OUT,15000,NewDemo,\n"))
                .header("X-Reviewer-Token","import-test-token").param("displayName","New fictional account").param("syntheticConfirmed","true"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importedRows").value(1)).andReturn().getResponse().getContentAsString();
        String id=json.readTree(body).get("accountId").asText();
        mvc.perform(get("/api/accounts/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.analysis.asOf").value("2027-01-04T09:00:00Z"))
                .andExpect(jsonPath("$.analysis.outgoingTotal").value(15000)).andExpect(jsonPath("$.analysis.riskScore").value(15));
        mvc.perform(get("/api/reports/daily").header("X-Reviewer-Token","import-test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accounts.length()").value(4));
    }
}

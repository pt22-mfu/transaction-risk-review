package dev.pt.risk;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvImportServiceTest {
    private final CsvImportService service = new CsvImportService(null);
    private static final String HEADER = "transaction_id,occurred_at,direction,amount,counterparty,description\n";
    private CsvImportService.Parsed parse(String rows) { return service.parse("CUSTOM-TEST", (HEADER+rows).getBytes(StandardCharsets.UTF_8)); }
    private void rejects(String rows) { assertThrows(CsvImportService.InvalidCsv.class, () -> parse(rows)); }
    @Test void parsesQuotedCommaMultilineThaiAndTimezone() {
        var result=parse("T1,2027-01-04T16:00:00+07:00,in,123.45,\"ร้านค้า, demo\",\"first\nsecond\"\n");
        var t=result.transactions().get(0);
        assertEquals("ร้านค้า, demo",t.counterparty()); assertEquals("first\nsecond",t.description());
        assertEquals("2027-01-04T09:00Z",result.snapshot().toString()); assertEquals("IN",t.direction());
        assertEquals("CUSTOM-TEST-T1",t.id()); assertEquals("123.45",t.amount().toPlainString());
    }
    @Test void usesLatestTimestampEvenIfFileIsUnsorted() {
        var result=parse("T1,2027-01-04T09:00:00Z,OUT,100,A,\nT2,2026-12-01T09:00:00Z,OUT,100,A,\n");
        assertEquals("2027-01-04T09:00Z",result.snapshot().toString());
    }
    @Test void acceptsUtf8Bom() {
        assertEquals(1,service.parse("A",("\uFEFF"+HEADER+"T1,2027-01-04T09:00:00Z,OUT,100,A,\n").getBytes(StandardCharsets.UTF_8)).transactions().size());
    }
    @Test void rejectsDuplicateIds() { rejects("T1,2027-01-04T09:00:00Z,OUT,100,A,\nT1,2027-01-04T10:00:00Z,OUT,100,B,\n"); }
    @Test void rejectsTimestampWithoutTimezone() { rejects("T1,2027-01-04T09:00:00,OUT,100,A,\n"); }
    @Test void rejectsNonPositiveAndMalformedAmounts() {
        for(String a:new String[]{"0","-1","1.234","1e3","THB100","10000000000000000"}) rejects("T1,2027-01-04T09:00:00Z,OUT,"+a+",A,\n");
    }
    @Test void rejectsUnexpectedAndDuplicateHeaders() {
        for(String h:new String[]{HEADER.replace("description","extra"),HEADER.replace("description","counterparty")})
            assertThrows(CsvImportService.InvalidCsv.class,()->service.parse("A",h.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsInvalidEncodingAndNoRecords() {
        assertThrows(CsvImportService.InvalidCsv.class,()->service.parse("A",new byte[]{(byte)0xc3,0x28}));
        rejects("");
    }
    @Test void rejectsOverOneThousandRecords() {
        StringBuilder rows=new StringBuilder();
        for(int i=0;i<1001;i++)rows.append("T").append(i).append(",2027-01-04T09:00:00Z,OUT,100,A,\n");
        rejects(rows.toString());
    }
    @Test void listsBadRowsAndRejectsEntireBatch() {
        var error=assertThrows(CsvImportService.InvalidCsv.class,()->parse("T1,2027-01-04T09:00:00Z,OUT,100,A,\nT2,2027-01-04T09:00:00Z,SEND,100,A,\nT3,2027-01-04T09:00:00Z,OUT,0,A,\n"));
        assertEquals(2,error.issues().size()); assertTrue(error.issues().get(0).contains("Record 2"));
    }
}

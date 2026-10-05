package dev.pt.risk;

import dev.pt.risk.Models.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvImportService {
    public static final Set<String> HEADERS = Set.of("transaction_id","occurred_at","direction","amount","counterparty","description");
    private final RiskRepository repository;
    public CsvImportService(RiskRepository repository) { this.repository = repository; }
    public record Parsed(List<Transaction> transactions, OffsetDateTime snapshot) {}
    public record Imported(String accountId, int importedRows, OffsetDateTime asOf) {}
    public static class InvalidCsv extends RuntimeException {
        private final List<String> issues;
        public InvalidCsv(List<String> issues) { super("The file was not imported. Correct the listed issues and try again."); this.issues=List.copyOf(issues); }
        public List<String> issues() { return issues; }
    }
    public Imported importFile(String name, boolean syntheticConfirmed, MultipartFile file) {
        if (!syntheticConfirmed) throw invalid("Confirm that this file contains fictional demo data only.");
        if (name == null || name.isBlank() || name.strip().length()>80) throw invalid("Account display name must contain 1-80 characters.");
        if (file.isEmpty() || file.getSize()>1024*1024) throw invalid("Choose a non-empty UTF-8 CSV file no larger than 1 MB.");
        String id="CUSTOM-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
        try {
            Parsed parsed = parse(id,file.getBytes());
            repository.importAccount(new Account(id,name.strip(),"Imported fictional history",
                    "User-supplied fictional transactions. The review snapshot is the latest timestamp in this import; historical coverage depends on this file."),
                    parsed.transactions(),parsed.snapshot());
            return new Imported(id,parsed.transactions().size(),parsed.snapshot());
        } catch (IOException e) { throw invalid("Could not read the uploaded file."); }
    }
    public Parsed parse(String accountId, byte[] bytes) {
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { throw invalid("The file must be encoded as UTF-8."); }
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        List<Transaction> ts=new ArrayList<>(); List<String> issues=new ArrayList<>();Set<String> ids=new HashSet<>();
        CSVFormat format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
        try(CSVParser parser=format.parse(new StringReader(text))) {
            if(!parser.getHeaderMap().keySet().equals(HEADERS))throw invalid("Required headers: transaction_id,occurred_at,direction,amount,counterparty,description (no extra columns).");
            int count=0;
            for(CSVRecord row:parser) {
                if(++count>1000)throw invalid("At most 1,000 transactions can be imported in one file.");
                try {
                    if(!row.isConsistent())throw new IllegalArgumentException("column count does not match the header");
                    String rawId=row.get("transaction_id").strip();
                    if(!rawId.matches("[A-Za-z0-9_-]{1,40}"))throw new IllegalArgumentException("transaction_id must contain 1-40 letters, digits, hyphens or underscores");
                    if(!ids.add(rawId))throw new IllegalArgumentException("duplicate transaction_id within the file");
                    OffsetDateTime at;
                    try{at=OffsetDateTime.parse(row.get("occurred_at").strip()).withOffsetSameInstant(ZoneOffset.UTC);}
                    catch(Exception e){throw new IllegalArgumentException("occurred_at must be an ISO timestamp with timezone, such as 2026-10-05T09:00:00Z");}
                    if(at.getYear()<2000 || at.getYear()>2100)throw new IllegalArgumentException("timestamp year must be between 2000 and 2100");
                    String direction=row.get("direction").strip().toUpperCase(Locale.ROOT);
                    if(!Set.of("IN","OUT").contains(direction))throw new IllegalArgumentException("direction must be IN or OUT");
                    String rawAmount=row.get("amount").strip();
                    if(!rawAmount.matches("[0-9]{1,16}(\\.[0-9]{1,2})?"))throw new IllegalArgumentException("amount must be a positive decimal with at most 16 integer digits and two decimal places; no commas or currency symbol");
                    BigDecimal amount=new BigDecimal(rawAmount);
                    if(amount.signum()<=0)throw new IllegalArgumentException("amount must be greater than zero");
                    String party=row.get("counterparty").strip(), description=row.get("description").strip();
                    if(party.isEmpty() || party.length()>80)throw new IllegalArgumentException("counterparty must contain 1-80 characters");
                    if(description.length()>300)throw new IllegalArgumentException("description must not exceed 300 characters");
                    ts.add(new Transaction(accountId+"-"+rawId,accountId,at,direction,amount,party,description));
                }catch(IllegalArgumentException e){if(issues.size()<10)issues.add("Record "+row.getRecordNumber()+": "+e.getMessage()+".");}
            }
        }catch(InvalidCsv e){throw e;}
        catch(IOException|IllegalArgumentException|UncheckedIOException e){throw invalid("CSV structure is invalid. Check quotes, commas and the header row.");}
        if(!issues.isEmpty())throw new InvalidCsv(issues);
        if(ts.isEmpty())throw invalid("The file contains no transaction records.");
        OffsetDateTime snapshot=ts.stream().map(Transaction::occurredAt).max(OffsetDateTime::compareTo).orElseThrow();
        return new Parsed(List.copyOf(ts),snapshot);
    }
    private static InvalidCsv invalid(String issue){return new InvalidCsv(List.of(issue));}
}

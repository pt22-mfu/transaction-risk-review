package dev.pt.risk;

import static org.assertj.core.api.Assertions.*;
import dev.pt.risk.Models.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
    static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-10-05T12:00:00Z");
    static final Policy POLICY = new Policy(5,10,new BigDecimal("5"),new BigDecimal("10000"),5,new BigDecimal("20000"),new BigDecimal("0.8"),30,30);
    final RiskEngine engine = new RiskEngine(POLICY);
    private Transaction tx(String id, String time, String direction, String amount) {
        return new Transaction(id,"A",OffsetDateTime.parse(time),direction,new BigDecimal(amount),"P-"+id,"Synthetic");
    }
    private boolean has(List<Transaction> ts, String rule) { return engine.analyze("A",ts,AS_OF).findings().stream().anyMatch(f -> f.ruleId().equals(rule)); }

    @Test void routineScenarioTriggersNoRules() {
        Analysis a = engine.analyze("DEMO-001",DemoData.transactions(),AS_OF);
        assertThat(a.riskScore()).isZero(); assertThat(a.findings()).isEmpty();
        assertThat(a.historicalOutgoingCount()).isEqualTo(8);
        assertThat(a.historicalMedian()).isEqualByComparingTo("475");
    }
    @Test void plannedPurchaseShowsPossibleFalsePositive() {
        Analysis a = engine.analyze("DEMO-002",DemoData.transactions(),AS_OF);
        assertThat(a.priority()).isEqualTo("MEDIUM"); assertThat(a.riskScore()).isEqualTo(35);
        assertThat(a.findings()).extracting(Finding::ruleId).containsExactly("AMOUNT","RECIPIENT");
    }
    @Test void burstScenarioProducesTraceableEvidence() {
        Analysis a = engine.analyze("DEMO-003",DemoData.transactions(),AS_OF);
        assertThat(a.priority()).isEqualTo("HIGH"); assertThat(a.riskScore()).isEqualTo(80);
        assertThat(a.findings()).extracting(Finding::ruleId).containsExactly("BURST","RECIPIENT","RAPID","DORMANT");
        assertThat(a.findings().get(0).transactionIds()).containsExactly("S-102","S-103","S-104","S-105","S-106");
    }
    @Test void excludesFutureAndOtherAccountData() {
        List<Transaction> ts = new ArrayList<>(DemoData.transactions());
        ts.add(new Transaction("FUTURE","DEMO-001",AS_OF.plusSeconds(1),"OUT",new BigDecimal("999999"),"NEW","Synthetic"));
        assertThat(engine.analyze("DEMO-001",ts,AS_OF).riskScore()).isZero();
    }
    @Test void reviewWindowIncludesExactStart() {
        assertThat(has(List.of(tx("T","2026-10-04T12:00:00Z","OUT","10000")),"RECIPIENT")).isTrue();
        assertThat(has(List.of(tx("T","2026-10-04T11:59:59Z","OUT","10000")),"RECIPIENT")).isFalse();
    }
    @Test void burstUsesElapsedMinutesNotMinuteBuckets() {
        List<Transaction> ts = new ArrayList<>();
        for (int i=0;i<5;i++) ts.add(tx("T"+i,AS_OF.minusMinutes(12).plusMinutes(i*3).toString(),"OUT","100"));
        assertThat(has(ts,"BURST")).isFalse();
        ts.set(4,tx("T4",AS_OF.minusMinutes(2).toString(),"OUT","100"));
        assertThat(has(ts,"BURST")).isTrue();
    }
    @Test void outgoingBeforeDepositDoesNotCountAsRapid() {
        List<Transaction> ts = List.of(tx("O","2026-10-05T08:59:00Z","OUT","19000"),tx("I","2026-10-05T09:00:00Z","IN","20000"));
        assertThat(has(ts,"RAPID")).isFalse();
    }
    @Test void simultaneousDepositAndTransferHaveNoProvenOrder() {
        assertThat(has(List.of(tx("I","2026-10-05T09:00:00Z","IN","20000"),tx("O","2026-10-05T09:00:00Z","OUT","19000")),"RAPID")).isFalse();
    }
    @Test void anotherDepositStopsAttributionOfLaterOutflow() {
        assertThat(has(List.of(tx("I1","2026-10-05T09:00:00Z","IN","20000"),tx("I2","2026-10-05T09:05:00Z","IN","100"),
                tx("O","2026-10-05T09:10:00Z","OUT","19000")),"RAPID")).isFalse();
    }
    @Test void rapidRuleHonorsExactThresholdAndDeadline() {
        assertThat(has(List.of(tx("I","2026-10-05T09:00:00Z","IN","20000"),tx("O","2026-10-05T09:30:00Z","OUT","16000")),"RAPID")).isTrue();
        assertThat(has(List.of(tx("I","2026-10-05T09:00:00Z","IN","20000"),tx("O","2026-10-05T09:30:01Z","OUT","16000")),"RAPID")).isFalse();
        assertThat(has(List.of(tx("I","2026-10-05T09:00:00Z","IN","20000"),tx("O","2026-10-05T09:30:00Z","OUT","15999.99")),"RAPID")).isFalse();
    }
    @Test void noHistoryCannotEstablishDormancyOrAmountBaseline() {
        Analysis a = engine.analyze("A",List.of(tx("T","2026-10-05T09:00:00Z","OUT","10000")),AS_OF);
        assertThat(a.findings()).extracting(Finding::ruleId).containsExactly("RECIPIENT");
    }
    @Test void knownRecipientIsNotNewAndRulesScoreOnce() {
        List<Transaction> ts = new ArrayList<>();
        ts.add(new Transaction("H","A",AS_OF.minusDays(2),"OUT",new BigDecimal("100"),"KNOWN","Synthetic"));
        for(int i=0;i<10;i++) ts.add(new Transaction("T"+i,"A",AS_OF.minusMinutes(i),"OUT",new BigDecimal("10000"),"KNOWN","Synthetic"));
        Analysis a = engine.analyze("A",ts,AS_OF);
        assertThat(a.findings()).extracting(Finding::ruleId).containsExactly("BURST");
        assertThat(a.riskScore()).isEqualTo(25);
    }
    @Test void dormancyIsMeasuredAtResumptionNotAtAnalysisTime() {
        List<Transaction> ts = List.of(tx("H","2026-09-05T12:01:00Z","OUT","100"),tx("R","2026-10-04T12:00:00Z","IN","100"),
                tx("O","2026-10-05T11:00:00Z","OUT","100"));
        assertThat(has(ts,"DORMANT")).isFalse();
    }
}

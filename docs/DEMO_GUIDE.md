# RiskDesk: a three-minute demonstration

## Opening
“I built a Java and TypeScript transaction-review prototype. The Java rules detect defined patterns and link each finding to transaction evidence. An optional LLM explains those findings, while a person records the final review decision.”

## Show the workflow
1. **Everyday account:** score 0. Explain that no rule triggered on this supplied history, rather than claiming the account is safe.
2. **Small business account:** score 35. Open the legitimate explanation. A planned supplier payment shows why a large payment and new recipient need context.
3. **Reactivated account:** score 80. Open “View evidence transactions.” Show the transfer burst, fund-movement timing and earlier inactivity.
4. **Review explanation:** generate the explanation. Without a provider key, the result is labelled “Rule-based template.” With a configured key and reviewer access, Gemini generates a validated explanation from the findings.
5. **Human review:** enter reviewer access, add a fictional rationale and save. Open Review history to show the appended record.
6. **Import CSV:** download the sample, name the new account and upload it. Its own latest transaction defines the review snapshot. A malformed file is rejected before account creation.

## Explain your engineering choices
- Java 17 / Spring Boot REST API; the same server serves the React/TypeScript dashboard.
- BigDecimal for amounts; explicit timezone-aware timestamps for event windows.
- Pure rule engine separated from database, CSV validation, provider calls and presentation.
- JDBC supports local H2 and Supabase PostgreSQL. The schema has no direct browser database access.
- CSV validation precedes transactional writes. Account-specific snapshots keep new imports independent of seeded dates.
- An inactive n8n workflow consumes the protected reporting endpoint and prepares a daily digest.
- 39 automated tests cover rule boundaries, API access, CSV handling and simulated provider responses. Desktop/mobile browser checks exercised the real built JAR.

## State the limits clearly
The score is a configurable review priority, not a fraud probability or bank policy. This is fictional data, with no automatic blocking or real bank connection. Reviewer access is a shared demo token; a production system would need individual identities and stronger audit controls.

Only claim integrations you have actually configured and demonstrated. Supabase, live Gemini, hosted n8n and public hosting remain setup steps until verified with your accounts.

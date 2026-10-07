# Verification of RiskDesk 0.2.0

Updated 07 October 2026. Historical frontend baseline: commit `3a1a8f2`. [Full PDF reference](RiskDesk_Technical_Documentation.pdf), page 20.

The 39 Java tests below are recorded MVP results. No new Maven run is claimed for this documentation update; strict TypeScript checking and the production frontend build were rerun for the UI redesign.

## 07 October finance-dashboard UI verification
- TypeScript strict checking and production frontend build passed.
- Chromium widths 1440, 1366, 1024, 768, 390, 375 and 320 pixels checked across the review, policy, automation and CSV import views. No page-wide horizontal overflow or JavaScript runtime errors.
- Search, empty search results, priority filters, account switching, no-findings state, legitimate explanations, linked evidence and complete supplied history checked.
- All four account tabs and arrow-key tab navigation checked.
- Reviewer dialog checked for keyboard focus containment, Escape, close control and focus restoration. Background content is inert while open; page scrolling is restored on close.
- AI output and saving/retrieving an escalated review were checked with **mock API responses**. These checks do not claim a new Gemini call or a write to hosted Supabase.
- New desktop and mobile screenshots rendered and visually inspected. Screenshots use controlled fictional fixtures.
- The reviewer decision form is below the evidence panel; it may require scrolling on desktop. This replaces the earlier three-column layout.
- Backend, database schema, deterministic rules and authentication endpoints were not changed by this UI refresh. GitHub Actions runs the existing full Java verification on push.

## Completed in the original MVP build workspace
- Java 17 compilation and Maven verify passed.
- 39 automated tests passed: 13 rule-engine tests, 10 original Spring API tests, 10 CSV parser tests, 4 import API tests and 2 simulated provider-response tests.
- TypeScript strict checking and production frontend build passed.
- The executable JAR served the real dashboard and REST API in a Chromium browser.
- All three seeded accounts returned expected scores: 0, 35 and 80.
- Browser interaction checks passed for linked evidence, full history, scenario switching, template explanation, token entry, review saving and audit history, rule policy, automation view, priority filter, search and empty search result.
- Browser CSV checks passed: sample download, invalid header feedback, no partially created account, successful import, automatic selection and expected score 80.
- Parser checks covered UTF-8/BOM, Thai text, quoted commas, multiline text, duplicate IDs, invalid amounts, missing timezone, invalid headers, row limits and rejection of the entire batch.
- Desktop (1440 x 1000) and mobile (390 x 844) screenshots of the dashboard and CSV import screen were rendered and reviewed.
- Mobile page had no horizontal overflow; no browser JavaScript runtime errors were recorded.
- The n8n Code node's digest transformation was executed against the real Java report response and returned the high-priority synthetic account.

## 06 October UI checks (historical)
- Built React/TypeScript frontend exercised in Chromium against the existing Java backend.
- Account switching, linked evidence, rule explanation, reviewer access, review saving/history, policy, automation, search, priority filters and CSV validation/import checked.
- Widths 320, 390, 768, 1024, 1280, 1440 and 1920 pixels checked for page and tab-bar overflow.
- At 1440 x 1000 the Save review action was within the initial viewport; desktop/mobile dashboard and import screenshots reviewed.
- No browser runtime errors recorded.

## Live deployment evidence
- Owner confirmed the Render deployment and the redesigned UI.
- Supabase PostgreSQL configured; account reads, fictional imports and saved review history demonstrated.
- Live Gemini output displayed "AI generated" on a seeded and an imported account.
- A saved pending-review note was independently retrieved through the account API.
- The initial deployment encountered a PostgreSQL connection failure and subsequently recovered; the definitive root cause was not established.

## Remaining separate checks
- Controlled restart/redeploy test to explicitly verify imported data and review durability.
- Hosted n8n import/activation, scheduled execution and chosen report destination. The export remains inactive.
- Windows launcher execution. Its PowerShell script was prepared; the build workspace used Java 17 on Linux.
- Production identity, access/audit controls, scale testing and evaluation on representative labelled data are outside this demo's acceptance scope.

## Dataset semantics
Seeded data uses a fixed fictional snapshot. Imported accounts use the latest transaction timestamp from each fictional CSV. Expected behaviour is checked against handcrafted scenarios, not labelled real fraud data. No accuracy, recall or fraud-prevention performance claim is made.

The final reference-inspired theme uses a teal–blue backdrop, rounded floating workspace, and contrasting incoming/outgoing summary cards. The desktop/mobile interaction checks below were repeated after this CSS-only theme update.

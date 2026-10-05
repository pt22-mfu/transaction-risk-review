# Verification of MVP 0.2.0

## Completed in the build workspace
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

## Not yet verified
- Connection to the owner's Supabase database (no credentials supplied).
- Real Gemini response, account quota or model availability (no API key supplied). Provider tests used a local simulated HTTP server.
- n8n import/activation, hosted scheduling or notification delivery. The export remains inactive.
- Render Docker build, public URL, cloud cold starts and deployment environment variables.
- Windows launcher execution. Its PowerShell script was prepared; testing here used Java 17 on Linux.

## Dataset semantics
Seeded data uses a fixed fictional snapshot. Imported accounts use the latest transaction timestamp from each fictional CSV. Expected behaviour is checked against handcrafted scenarios, not labelled real fraud data. No accuracy, recall or fraud-prevention performance claim is made.

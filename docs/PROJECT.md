# Transaction Risk Review Assistant

Author: Phyo Thant Kyaw (PT), Mae Fah Luang University

## Current build and full reference
- Application: v0.2.0; redesigned frontend baseline: commit `3a1a8f2`.
- Documentation: v1.0, 06 October 2026.
- [Live demo](https://transaction-risk-review.onrender.com).
- [24-page PDF handbook](RiskDesk_Technical_Documentation.pdf): detailed rule boundaries, database diagram, API examples, CSV contract, environment settings and troubleshooting.
- Render + Supabase and live Gemini have been demonstrated. The inactive n8n export is included; hosted execution remains a separate setup step.

## Problem
Reviewers need to prioritize unusual transaction activity and understand the evidence behind each signal. A large payment alone does not establish fraud. The prototype combines deterministic Java rules with optional LLM explanations and a human review workflow.

## Scope
Three seeded fictional accounts plus validated CSV imports; five configurable rules; THB only. Seeded reviews end 5 October 2026 at 12:00 UTC. Each imported account uses its latest transaction as the end of its 24-hour review window. Read-only account evidence is public. CSV imports, review updates, report access and live AI calls require the owner's reviewer token. This simple token is a demo access gate, not a complete identity or role system.

## Architecture
React/TypeScript -> Spring Boot REST API -> JDBC -> Supabase PostgreSQL (or local H2).
The Java application serves the built frontend, so deployment uses one app URL.
The rule engine computes signals using BigDecimal amounts and event timestamps.
Gemini receives computed summaries and evidence IDs; it cannot write review decisions.
n8n can consume the protected reporting endpoint. A workflow export is included; activation and live execution are separate steps.

## Acceptance criteria
- Everyday account has score 0 and no configured rules triggered.
- Planned supplier payment has score 35 (medium) and two findings; a legitimate explanation remains visible.
- Reactivated account has score 80 (high) and four findings with exact evidence IDs.
- Transactions after the snapshot or belonging to other accounts do not affect analysis.
- Every rule contributes points at most once in a review.
- Account changes cannot display the previous account's asynchronously returned AI explanation.
- No API key means an explicitly labelled rule-based template, never a fake AI result.
- Invalid/failed AI output falls back to a labelled rule explanation.
- Review status and notes are validated and appended to an audit history.
- Public requests cannot import accounts, mutate reviews or trigger live LLM calls without reviewer access.
- Invalid CSV records reject the complete batch before writes; database writes occur in one transaction.
- Uploaded histories have independent review cutoffs and appear in the dashboard and reporting endpoint.

## Important limits
Risk score is a hand-designed priority score, not a calibrated fraud probability. Correlated rules may count overlapping activity. Baselines depend on the supplied history, not an actual bank ledger. Rapid outflow is a temporal pattern, not proof that a specific deposit funded particular transfers. No balances, device/IP telemetry, KYC, external watchlists, verified scam labels or real bank policy are available.

All seeded data is fictional. There is no automatic blocking, refund, account freezing or transfer capability. Production use would require actual authorization controls, tenant isolation, review governance and validation on representative data.

## Next increments
1. Controlled restart verification of Supabase-backed persistence, plus a database-aware readiness endpoint.
2. Hosted n8n schedule and chosen report destination.
3. Reviewer identities, permissions, actor attribution and tamper-evident audit controls.
4. Expanded scenario tests and measured false-positive behaviour.

## API
| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| GET | /api/health | Public | App liveness |
| GET | /api/meta | Public | Snapshot, policy, provider availability |
| GET | /api/accounts | Public | Accounts, scores and latest review status |
| GET | /api/accounts/{id} | Public | History, findings and review notes |
| GET | /api/accounts/{id}/analysis | Public | Recompute deterministic rule findings |
| POST | /api/accounts/{id}/explanation | Token if AI configured | Explain rule findings |
| POST | /api/accounts/{id}/reviews | Token | Append validated review decision |
| POST | /api/imports | Token | Validate CSV and create fictional account |
| GET | /api/reports/daily | Token | Snapshot report for n8n |

Reviewer header: `X-Reviewer-Token`. Notes are visible to all demo visitors; use fictional information only.

`/api/health` is a liveness response: it does not query the database or AI provider. Verify `/api/accounts` and `/api/meta` after deployment. Review records are append-only through the application API; a database administrator can still change them. AI explanations and unsaved notes are not persisted.

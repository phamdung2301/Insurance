# Daily Reports — Insurance Policy Management System (Team 1)

---

## Daily Report - 15 Oct

**Done:**
     • Excel Pricing Matrix (Apache POI): Template generation, base-rate upload/parse, and premium calculation API.
     • MongoDB Aggregation Analytics: 4 pipelines (status counts, premium volume, top policies, monthly trends).
     • Benchmark Engine: 50,000-policy generator and explain(executionStats) before/after index comparison engine.

**In Progress:**
     • UI Analytics Reports Dashboard (Recharts) with VietQR payment modal.
     • UI Benchmark Performance Testing View (winningPlan, query times).

**Next:**
     • Document Generation (OpenPDF): COI and Policy Summary Schedule PDF exports.
     • Premium calculation Excel export with formulas and digital stamp.
     • UI 4-Step Buy Insurance Wizard.

**Blockers:**
     • N/A

---

## Daily Report - 16 Oct

**Done:**
     • Document Generation (OpenPDF): Certificate of Insurance (COI) and Policy Summary Schedule PDF exports.
     • Excel Export: Premium calculation breakdown (.xlsx) with formulas and digital stamp.
     • UI 4-Step E-Commerce Buy Insurance Wizard with live premium estimation and Draft/Quoted submission.
     • Index Audit Controller & redundant compound index deduplication engine.
     • Quality: Unit & integration tests for endorsement, state machine, rate limiting, and security.
     • Fixes & Security Round 2: OAuth2 failure handler + blank-screen fix, locked public endpoints, BE-FE contract audit, env-based config extraction, endorsement redesign, version snapshot fix (PRs #26–#33).

**In Progress:**
     • FE/BE deployment split (Vercel + Render) and Google OAuth absolute-URL fix (PRs #34–#36).

**Next:**
     • Deploy frontend to Vercel and backend to Render.
     • E2E lifecycle test (Draft → Quoted → Bound → Active → Endorse); security penetration & rate-limiting validation.
     • Finalize Postman Collection, README (11 sections), benchmark report, presentation deck, and release packaging.

**Blockers:**
     • N/A

---

## Daily Report - 19 Oct

**Done:**
     • End-to-end Integration Testing: Complete policy lifecycle (Draft → Quoted → Bound → Active → Endorse).
     • Security Penetration & Rate-Limiting Validation: OTP brute-force prevention, unauthorized endpoint access.
     • Regression Fixes + Release Packaging: v1.0 build tagged and submitted.
     • Hot-fix: Premium formula correction (PR #40).

**In Progress:**
     • Finalize Postman Collection (10 API folders, environments, auth tokens).
     • Comprehensive README (11 sections, EN) and operations/handover guide.

**Next:**
     • Performance Benchmark Report (index vs no-index on 50,000 documents).
     • Acceptance test evidence consolidation (95/95 cases); DB design doc review.
     • Presentation outline (10 slides) + live demo script.

**Blockers:**
     • N/A

---

## Daily Report - 20 Oct

**Done:**
     • Postman Collection: Finalized 10 API folders with environments, auth tokens, and sample requests.
     • Documentation: Comprehensive README (11 sections, EN) covering setup, architecture, state machine, and benchmark.

**In Progress:**
     • Performance Benchmark Report: drafting index vs no-index explain() analysis on 50,000 documents.

**Next:**
     • Acceptance test evidence consolidation (95/95 cases) and operational handover guide.
     • DB design doc & data dictionary review against the final MongoDB implementation.

**Blockers:**
     • N/A

---

## Daily Report - 21 Oct

**Done:**
     • Performance Benchmark Report: Completed before/after index explain() analysis on 50,000 documents.
     • Acceptance Test Evidence: Consolidated 95/95 test case verification and user manual.
     • Database Design: Reviewed DB design document & data dictionary against the final MongoDB implementation.

**In Progress:**
     • Project Presentation Outline: 10-slide deck structure and live demo walkthrough script.

**Next:**
     • Final regression pass, cleanup, and release packaging.
     • Hot-fix round for review feedback.

**Blockers:**
     • N/A

---

## Daily Report - 22 Oct

**Done:**
     • Presentation: Project presentation outline, 10-slide deck structure, and live demo walkthrough script.
     • Hot-fixes: Premium formula correction (PR #40) and Sidebar update.
     • Handover Guide: Operational handover report finalized.

**In Progress:**
     • Phase 2 scoping: Requirement analysis for the SaaS upgrade (claims workflow, price negotiation, payment gateway, multi-tenant architecture).

**Next:**
     • Submit v1.0 and kick off Phase 2.
     • Create the Phase 2 tracking file (task breakdown, requirement tracking, burndown dashboard).

**Blockers:**
     • N/A

---

## Daily Report - 23 Oct

**Done:**
     • v1.0 Submitted: Release packaged and delivered.
     • Phase 2 Scoping: Completed requirement analysis for claims workflow, price negotiation v2, payment gateway (VNPay first), and multi-tenant architecture.
     • Tracking: Created the Phase 2 tracking file with day-by-day task breakdown.

**In Progress:**
     • Payment gateway research: VNPay sandbox vs SePay bank-transfer + HMAC webhook comparison.

**Next:**
     • VNPay sandbox integration with IPN notifications for policy activation.
     • Claims intake API (staff-only) design and implementation.

**Blockers:**
     • N/A

---

## Daily Report - 26 Oct

**Done:**
     • Payment Gateway (VNPay sandbox): Gateway integration with IPN notifications for policy activation.

**In Progress:**
     • Claims Intake API: Staff-only intake endpoint + ACTIVE-policy dropdown + amount overflow fix.
     • Negotiation v2: Role-based RBAC (CUSTOMER/AGENT/UNDERWRITER) design.

**Next:**
     • Negotiation v2 UI: Offer events in chat, auto-refresh, reopen after decline.
     • Transaction ACID: Compensation strategy for failed multi-write flows.

**Blockers:**
     • N/A

---

## Daily Report - 27 Oct

**Done:**
     • Claims Intake API: Staff-only intake endpoint + ACTIVE-policy dropdown + amount overflow fix.

**In Progress:**
     • Negotiation v2 UI: Offer events in chat, auto-refresh, reopen after decline.
     • Vite dev proxy: /underwriter route for local development.

**Next:**
     • Transaction ACID: Compensation for failed multi-write flows (webhook, endorsement, negotiation confirm, best-effort ledger).
     • Payment gateway evaluation: VNPay vs SePay bank-transfer + HMAC webhook.

**Blockers:**
     • N/A

---

## Daily Report - 28 Oct

**Done:**
     • Transaction ACID: Compensation for failed multi-write flows — webhook, endorsement, negotiation confirm, best-effort ledger.

**In Progress:**
     • Negotiation v2 UI polish: Offer events in chat, auto-refresh, reopen after decline.
     • Payment gateway evaluation: VNPay vs SePay bank-transfer + HMAC webhook.

**Next:**
     • Finalize the gateway decision and implement the SePay migration.
     • SaaS Phase 1: Agent tenant model foundation.

**Blockers:**
     • Local MongoDB standalone does not support multi-document transactions — resolved by switching to a single-node replica set (rs0).

---

## Daily Report - 29 Oct

**Done:**
     • Negotiation v2 UI: Offer events in chat, auto-refresh, reopen after decline; /underwriter route added to the Vite dev proxy.

**In Progress:**
     • Payment gateway evaluation: VNPay vs SePay bank-transfer + HMAC webhook (transfer content + VietQR).

**Next:**
     • Finalize the gateway decision and implement the SePay migration.
     • SaaS Phase 1: Agent tenant model, dual-write agentId, migration script, TenantContext.

**Blockers:**
     • N/A

---

## Daily Report - 30 Oct

**Done:**
     • Gateway Decision: Switched to SePay bank-transfer + HMAC webhook (transfer content + VietQR); ACID tests finalized.

**In Progress:**
     • SePay implementation: HMAC webhook with auto-activation (QUOTED/BOUND → ACTIVE), dropping the FINANCE manual confirm.
     • SaaS Phase 1: Agent tenant model preparation.

**Next:**
     • SaaS Phase 1: Agent tenant model, dual-write agentId, migration script, TenantContext.
     • Benchmark 50,000 policies regenerated in SaaS tenant shape.

**Blockers:**
     • N/A

---

## Daily Report - 02 Nov

**Done:**
     • Payment: Replaced VNPay with SePay bank-transfer + HMAC webhook; verified webhook auto-activates policy (QUOTED/BOUND → ACTIVE); dropped FINANCE manual confirm.

**In Progress:**
     • SaaS Phase 1: Agent tenant model, dual-write agentId, migration script, TenantContext.
     • Benchmark: 50,000 policies regenerated in SaaS tenant shape.

**Next:**
     • SaaS Phase 2 Track B: Scope cutover to agentId, fail-closed tenant guards.
     • Track C: Storefront /a/<slug> + AgentPicker + mandatory-agent rule.

**Blockers:**
     • N/A

---

## Daily Report - 03 Nov

**Done:**
     • SaaS Phase 1: Agent tenant model, dual-write agentId, migration script, TenantContext.
     • Benchmark: 50,000 policies regenerated in SaaS tenant shape + tenant query.

**In Progress:**
     • SaaS Phase 2 Track B: Scope cutover to agentId, fail-closed agent-without-tenant, tenant status guards (SUSPENDED/CLOSED).

**Next:**
     • Track C: Storefront /a/<slug> + AgentPicker + mandatory-agent rule (410 for dead links).
     • Admin: Agent/slug management and tenant-user assignment.

**Blockers:**
     • N/A

---

## Daily Report - 04 Nov

**Done:**
     • SaaS Phase 2 Track B: Scope cutover to agentId, fail-closed agent-without-tenant, tenant status guards (SUSPENDED/CLOSED).
     • Track C: Storefront /a/<slug> + AgentPicker + mandatory-agent rule (Q3), dead links return 410.
     • Admin: Agent/slug management and tenant-user assignment.
     • Stabilization: Finance-role scoping, whitescreen route fix, locationId typing, admin slug gate.

**In Progress:**
     • Ops toggles: MAIL_ENABLED / MAIL_SMTP_ENABLED with OTP-in-log fallback.
     • Simulated payment hook: POST /payments/{txnRef}/simulate (toggle-gated).

**Next:**
     • Payment auto-activation: Drop FINANCE manual confirm, verified webhook activates directly.
     • Hardening: Claim workflow per new business rules + tenant-scoped staff roles.

**Blockers:**
     • N/A

---

## Daily Report - 05 Nov

**Done:**
     • Ops Toggles: MAIL_ENABLED / MAIL_SMTP_ENABLED with OTP-in-log fallback; in-app notifications unaffected.
     • Simulated Payment Hook: POST /payments/{txnRef}/simulate (toggle-gated, demand-side only).
     • Vite Dev Proxy: /payments + /pending-endorsements.
     • Payment Auto-Activation: Verified webhook activates policy directly; dropped FINANCE manual confirm.
     • Hardening Branch Started: Claim workflow per new business rules (UW approves/rejects, FINANCE pays, ADMIN override) + tenant-scoped staff roles.

**In Progress:**
     • Error contract: ApiResponse.code + ErrorCode + Vietnamese message standardization.
     • Data-integrity guards: Server-side pricing, insured.email lock, password/OTP policies.

**Next:**
     • UX hardening: Dead-code cleanup, timeline, documents center, renewal alerts, notifications.
     • Seed demo tenant-shaped + agent-link flow + mandatory terms consent.

**Blockers:**
     • N/A

---

## Daily Report - 06 Nov

**Done:**
     • Error Contract: ApiResponse.code + ErrorCode; 500 returns generic message, details only in logs; ~50 messages localized to Vietnamese.
     • Data-Integrity Guards: Server-authoritative pricing, insured.email lock for customers, password policy 8–50, OTP 10/day/email, rate-limit trust-proxy + UPLOAD tier, seed gate.
     • UX Hardening: ~3.1k dead lines removed, showToast + lightbox 401 fixes, Vietnamese timeline, documents center, renewal alerts, notifications upgrade.
     • Seed Demo: Tenant-shaped data (2 agencies, 9 users, 6 policies, 4 claims, negotiation).
     • Agent-Link Flow: POST /user/agent-link one-time binding (idempotent), FE auto-call after login.
     • Mandatory Terms Consent: Wizard requires read + agree before quote; audit trail termsAcceptedAt/Version.
     • Quality: 525 tests passing (100% BUILD SUCCESS); frontend build passing.
     • Tracking: Phase 2 burndown dashboard markdown with charts.

**In Progress:**
     • PR review & merge: Hardening branch into main.
     • Test-case document: 68 cases across 9 modules for QA execution.

**Next:**
     • Merge hardening PR + verify main (test + build).
     • Flows 4–5: Direct purchase without agent, ADMIN agent re-assignment.
     • Claim storage migration to S3/R2; refresh token; legal terms finalization; docs/Postman sync.

**Blockers:**
     • N/A

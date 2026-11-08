# Daily Reports — Insurance Policy Management System (Team 1)

---

## Daily Report - 15-16 Oct

**Done:**
     • Excel Pricing Matrix (Apache POI): Template generation, rate upload/parse, and premium calculation API.
     • MongoDB Aggregation Analytics: 4 pipelines (status counts, premium volume, top policies, monthly trends) with Recharts dashboards.
     • Benchmark Engine: 50,000-policy generator and explain(executionStats) before/after index comparison; UI performance testing view.
     • Document Generation (OpenPDF): Certificate of Insurance (COI) and Policy Summary Schedule PDF exports.
     • Excel Export: Premium calculation breakdown (.xlsx) with formulas and digital stamp.
     • UI 4-Step Buy Wizard: Live premium estimation, draft/quoted submission.
     • Index Audit Controller: Redundant compound index deduplication engine.
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

## Daily Report - 19-23 Oct

**Done:**
     • End-to-end Integration Testing: Complete policy lifecycle (Draft → Quoted → Bound → Active → Endorse).
     • Security Penetration & Rate-Limiting Validation: OTP brute-force prevention, unauthorized endpoint access.
     • Postman Collection: Finalized 10 API folders with environments, auth tokens, and sample requests.
     • Documentation: Comprehensive README (11 sections, EN) + operations/handover guide; acceptance test evidence (95/95 test cases).
     • Performance Benchmark Report: Index vs no-index explain() metrics on 50,000 documents; DB design doc review.
     • Presentation: 10-slide deck structure and live demo script; regression fixes + final release packaging.
     • Hot-fixes: Premium formula correction and Sidebar update (PRs #39, #40).

**In Progress:**
     • Phase 2 planning: requirement analysis & scoping for the SaaS upgrade (claims workflow, price negotiation, payment gateway, multi-tenant architecture).

**Next:**
     • Payment gateway integration (sandbox first); negotiation v2 with role-based RBAC; claims intake API; transaction ACID compensation.

**Blockers:**
     • N/A

---

## Daily Report - 26-30 Oct

**Done:**
     • Payment Gateway (VNPay sandbox): Gateway integration with IPN notifications for policy activation (merged 04/11).
     • Phase 2 Design: Claims intake API design and transaction compensation strategy for multi-write flows.

**In Progress:**
     • Negotiation v2: Role-based RBAC (CUSTOMER/AGENT/UNDERWRITER) with chat offer events, auto-refresh, reopen.
     • Claims: Staff-only intake API + ACTIVE-policy dropdown + amount overflow fix.
     • Payment decision: Evaluate VNPay vs SePay bank-transfer + HMAC webhook approach.

**Next:**
     • Replace VNPay with SePay (transfer content + VietQR + webhook auto-activation).
     • SaaS Phase 1: Agent tenant foundation; transaction ACID compensation.

**Blockers:**
     • N/A

---

## Daily Report - 02-06 Nov

**Done:**
     • Payment: Replaced VNPay with SePay bank-transfer + HMAC webhook; verified webhook auto-activates policy (QUOTED/BOUND → ACTIVE) (04/11).
     • Negotiation v2: /underwriter dev proxy, offer events in chat, auto-refresh, reopen (04/11).
     • Claims: Staff-only intake API, ACTIVE-policy dropdown, amount overflow fix (04/11).
     • Transaction ACID: Compensation for failed multi-write flows — webhook, endorsement, negotiation confirm, best-effort ledger (04/11).
     • SaaS Phase 1: Agent tenant model, dual-write agentId, migration script, TenantContext (04/11).
     • SaaS Phase 2 Track B/C: Scope cutover to agentId, fail-closed tenant guards, storefront /a/<slug> + AgentPicker, admin agent & tenant-user management (05/11).
     • Benchmark: 50,000 policies regenerated in SaaS tenant shape (05/11).
     • Stabilization: Finance-role scoping, whitescreen route fix, locationId typing, admin slug gate (05/11).
     • Ops Toggles: MAIL_ENABLED / MAIL_SMTP_ENABLED with OTP-in-log fallback (06/11).
     • Simulated Payment Hook: POST /payments/{txnRef}/simulate, toggle-gated for review/demo (06/11).
     • Vite Dev Proxy: /payments + /pending-endorsements (06/11).
     • Payment Auto-Activation: Verified webhook activates policy directly; dropped FINANCE manual confirm (06/11).

**In Progress:**
     • Hardening branch (PR open): Claim workflow per new business rules (UW approves/rejects, FINANCE pays, ADMIN override), tenant-scoped staff roles, unified error contract (ApiResponse.code + ErrorCode), Vietnamese message standardization, data-integrity guards (server-side pricing, insured.email lock, password/OTP policies, rate-limit hardening), UX hardening (~3.1k dead lines removed), demo seed, agent-link flow, mandatory terms consent.
     • Documentation: Claims/Mail/SaaS handbook + Phase 2 dashboard markdown with burndown charts.

**Next:**
     • Merge hardening PR + verify main (test + build).
     • Flows 4–5: Direct purchase without agent, ADMIN agent re-assignment.
     • Claim storage migration to S3/R2; refresh token + HttpOnly cookie; legal terms finalization; docs/Postman sync.

**Blockers:**
     • N/A

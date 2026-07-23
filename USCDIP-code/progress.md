# Progress Log

## 2026-07-14

### Session objective
Implement the PDF-defined 3D visualization optimization in `USCDIP-code`, using `knight-L/sc-datav` as the code-structure reference.

### Completed
- Read the `planning-with-files` skill and adopted its persistent planning workflow.
- Read the PDF skill and established text extraction plus rendered-page visual inspection as the review method.
- Confirmed the requested delivery folder contains separate `frontend` and `backend` projects.
- Searched the workspace for the referenced PDF; no matching local file was found yet.
- Created `task_plan.md`, `findings.md`, and `progress.md` in the delivery root.
- Cloned the reference repository to a temporary read-only location and recorded its exact commit.
- Inspected the existing 3D route and confirmed it already contains a lightweight Three.js GIS scene with API loading and 2D fallback.
- Generated and persisted a project-specific UI/UX design system.
- Identified the reference repository's reusable `map/panel/store` decomposition and its 3D layers, controls, projections, labels, heatmaps, animation, and shader patterns.
- Wrote `docs/3d-visualization-architecture.md` with reference-to-target mappings, frontend boundaries, backend API coverage, rendering constraints, and PDF-dependent decisions.
- Located `docs/ChatGPT - WHD.pdf`, extracted all 24 pages, and converted the requirements into `docs/3d-visualization-requirements.md`.
- Rendered all 24 PDF pages with native Poppler and visually reviewed them in four contact sheets; no visual-only requirement was missed.
- Added and validated a national province-level GeoJSON asset with 34 named regions plus one boundary/island feature.
- Installed frontend dependencies and established a clean baseline: typecheck passed and production build passed.
- Completed three read-only code audits covering authentication/routing, permissions/business data, and GIS/Three.js lifecycle.
- Added typed national overview models, deterministic Mock data, adapter boundary, unified Mercator projection, map configuration, and recursive Three.js disposal utility.
- New national map feature modules pass `npm run typecheck`.

### In progress
- Implementing safe post-login redirect handling, `/home`, `authOnly`, and legacy 3D route compatibility.
- Building the decomposed China 3D scene and national overview dashboard.

### Tests
- Baseline `npm run typecheck`: PASS.
- Baseline `npm run build`: PASS (existing 517.56 kB 3D placeholder chunk warning).

### Errors
- A broad recursive PDF search timed out; targeted searches completed successfully.
- The linked ChatGPT conversation is not readable anonymously, and two read-only browser connection attempts failed with the same runtime initialization conflict.
- A direct PDF upload or local PDF path is required before converting the specification into an acceptance checklist and safely starting implementation.
- The previous PDF blocker is resolved; the file is now present locally.
- Initial text extraction hit a GBK console encoding error and succeeded after forcing UTF-8 output.
- The bundled `pdftoppm` wrapper currently points to a missing executable path; a fallback renderer is being located.
- Resolved PDF rendering by invoking the bundled native Poppler executable directly.
- The first explorer dispatch used incompatible options; the corrected read-only dispatch succeeded.
- The first ECharts unpkg asset URL returned 404; the validated DataV full-boundary dataset replaced it.

### Final implementation
- Added authenticated `/home` routing, safe post-login redirects, query-preserving legacy 3D redirects, and deterministic logout to `/portal`.
- Replaced the old 3D placeholder with a decomposed national dashboard and a pure Three.js scene boundary.
- Added province extrusion, outlines, flylines, city nodes, pulse rings, scan light, hover/click selection, camera focus, layer controls, responsive resize, and complete disposal.
- Added centralized deterministic national Mock data behind `NationalOverviewDataAdapter`; existing authorized-scope APIs remain clearly separated from nationwide totals.
- Added real GIS object context loading through the existing API and projected its 3D beacon with the shared `projectLngLat()` function.
- Corrected the active emergency seed account so the Portal credential hint is operational.

### Final verification
- `npm run typecheck`: PASS.
- `npm run build`: PASS; national view is split from the Three.js and GeoJSON payloads. A non-blocking Vite chunk-size advisory remains for the lazy Three.js map chunk.
- `mvn test`: PASS, 130 tests, 0 failures, 0 errors, 0 skipped.
- Playwright: PASS for anonymous `/home`, emergency login, authenticated Portal redirect, safe redirect rejection/acceptance, legacy route query preservation, 2D route, logout, permission differences, GIS context marker, province selection, WebGL context-loss recovery, and 375/768/1024/1440 resize checks.
- Browser console: 0 errors and 0 warnings in the final normal and recovery flows.
- Canvas evidence: desktop 835x784 and mobile 360x471 captures are nonblank, with 757 and 520 sampled colors respectively.
- Resource cleanup: Three.js Canvas count becomes 0 after routing to a non-map page; the 2D GIS route contains only its Leaflet canvas.
- External OIDC provider was not available locally; callback state, safe redirect, duplicate callback, and cleanup paths were verified by code inspection and build/type checks.

## 2026-07-16 Account management

### Session objective
Add an end-to-end account management capability to the existing Vue and Spring Boot application.

### In progress
- Located the existing account, role, permission, data-scope, emergency-login, and platform-admin authorization foundations.
- Defined the initial account-management scope and security boundaries.
- Added account list/options/create/update/password-reset DTOs, service, controller, repository lookups, audit events, validation, and self-protection rules.
- Backend compilation after the account-management implementation: PASS (350 source files).

### Errors
- The first account integration-test compile failed because two single-line JSON payloads used Java text-block delimiters; changed them to ordinary escaped strings before rerunning.

### Final implementation
- Added platform-admin-only account list, option metadata, create, update, enable/disable, and password-reset APIs under `/api/auth/admin/accounts`.
- Reused the existing user, role, data-scope, emergency credential, session revocation, and security-audit models instead of introducing parallel identity storage.
- Added strong-password validation, immutable unique usernames, role/scope validation, self-disable protection, and protection against removing the current administrator's platform-admin role.
- Added `/mgmt/accounts`, typed frontend API bindings, route role enforcement, management-platform navigation, responsive account metrics/list/filter/editor views, and accessible password forms.

### Final verification
- `mvn test`: PASS, 138 tests, 0 failures, 0 errors, 0 skipped.
- `AccountManagementIntegrationTest`: PASS, including administrator authorization, non-admin denial, create/login/disable/session-revoke/enable/reset flows, duplicate usernames, weak passwords, and self-protection.
- `npm run typecheck`: PASS after the final accessibility adjustment.
- `npm run build`: PASS; account management remains a lazy-loaded 20.88 kB route chunk. The existing non-blocking Three.js chunk-size advisory remains.
- Playwright desktop and 375 px mobile checks: PASS, no horizontal page overflow, account creation and disable flows work, disabled credentials return HTTP 401, and the final browser console has 0 errors and 0 warnings.
- Live service checks: frontend `/mgmt/accounts` and backend `/api/platforms` both return HTTP 200.

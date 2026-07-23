# 3D Visualization Component Optimization Plan

## Goal
Use `knight-L/sc-datav` as the structural reference and implement every requirement from the referenced PDF in `E:\Whd\USCDIP\USCDIP-code`, preserving `frontend` and `backend` as the delivery boundaries.

## Requirements
- [x] Extract and record every functional and visual requirement from the PDF.
- [x] Analyze the relevant `sc-datav` architecture, components, dependencies, and interaction patterns.
- [x] Map the requirements onto the existing Vue frontend and Spring Boot backend.
- [x] Implement all required frontend and backend changes only under `USCDIP-code`.
- [x] Verify builds, tests, API behavior, and the rendered desktop/mobile experience.

## Phases

### Phase 1: Evidence collection
Status: complete

- Locate and extract the referenced PDF, including visual inspection.
- Fetch the reference repository and inventory its relevant code structure.
- Inventory the existing GIS/3D implementation in `USCDIP-code`.

### Phase 2: Requirement and architecture mapping
Status: complete

- Convert PDF content into an acceptance checklist.
- Record reference-to-target module mappings and implementation decisions.

### Phase 3: Implementation
Status: complete

- Implement frontend 3D visualization components and interactions.
- Implement or extend backend APIs and data contracts required by the PDF.
- Keep changes scoped to `frontend`, `backend`, and project planning records.

### Phase 4: Verification
Status: complete

- Run focused and full builds/tests proportional to the changes.
- Start the application and verify the complete browser flow on desktop and mobile.
- Inspect screenshots and canvas pixels for rendering, framing, overlap, and blank states.

### Phase 5: Completion audit
Status: complete

- Prove each PDF requirement against current files, commands, tests, APIs, or screenshots.
- Document final changed files, verification evidence, and any residual risks.

### Phase 6: Account management
Status: complete

- [x] Inventory the existing user, role, permission, data-scope, authentication, and audit model.
- [x] Add platform-admin-only account list, metadata, create, status, role/scope, and password-reset APIs.
- [x] Add the account management route, service/types, management UI, and project-wide navigation entry.
- [x] Verify authorization, validation, self-protection, login behavior, responsive layout, and builds/tests.

## Decisions
| Decision | Rationale |
|---|---|
| Keep `sc-datav` outside the delivery folder as a read-only reference | Prevents third-party source from being mixed into the user's standalone project. |
| Treat the PDF as the authoritative functional specification | The repository supplies patterns and structure, while the PDF defines requested behavior. |
| Reuse the existing user/role/data-scope model for account management | Avoids introducing a parallel identity model and keeps authorization behavior consistent. |
| Restrict all account writes to `PLATFORM_ADMIN` | Account lifecycle and role assignment are security-sensitive administrative operations. |

## Errors Encountered
| Error | Attempt | Resolution |
|---|---:|---|
| Initial recursive PDF listing timed out after 20 seconds | 1 | Replaced it with targeted filename and recent-file searches. |
| No matching local PDF filename was found | 1 | Investigating the conversation attachment URI and local app storage. |
| Anonymous access to the ChatGPT conversation returned only a login page | 1 | Attempted a read-only connection to the user's existing browser session. |
| Chrome browser runtime could not initialize because `process` was already defined as non-configurable | 2 | Stopped retrying the same method; request a direct PDF attachment or local file path. |
| PDF text extraction initially failed on a GBK console encoding error | 1 | Re-ran with `PYTHONIOENCODING=utf-8`; all 24 pages extracted successfully. |
| Bundled `pdftoppm.cmd` pointed to a missing path | 1 | Searching bundled native dependencies and Python renderers for a visual-review fallback. |
| First explorer spawn used an incompatible full-history fork plus explicit agent type | 1 | Re-spawned without full-history fork; three read-only explorers started successfully. |
| ECharts 5.6 unpkg path did not contain `map/json/china.json` | 1 | Switched to the DataV national full-boundary GeoJSON endpoint and validated the result. |
| A stale/anonymous Portal session requested protected todo APIs | 1 | Added a no-token fast path and gated todo loading on an authenticated user. |
| Seeded `bg_active_hz` password hash did not match the Portal credential hint | 1 | Replaced only that seed hash with a verified BCrypt hash for `BreakGlass123!`. |
| Three.js emitted a deprecated `THREE.Clock` warning | 1 | Switched animation timing to the `requestAnimationFrame` timestamp. |
| WebGL fault recovery retained an outer error and warned during second context loss | 1 | Cleared the message on `ready` and skipped forced context loss when already lost. |
| Account integration test used Java text-block delimiters for single-line JSON | 1 | Replaced the two invalid text blocks with escaped ordinary strings. |

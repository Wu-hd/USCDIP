# Findings

## Specification
- The authoritative specification is referenced as `3D可视化组件优化` via `chatgpt-conversation://6a55ebfb-97f4-83ea-a07a-2cfe4e0c9e47`.
- No PDF with a matching filename was found under `E:\Whd\USCDIP` during the initial targeted search.
- Direct web access to the conversation URL reaches a login page and exposes no conversation or PDF content.
- The local browser bridge failed to initialize twice with the same runtime conflict, so the PDF cannot currently be read from the linked conversation.
- The PDF is now available as `docs/ChatGPT - WHD.pdf` (24 pages, 1,793,366 bytes).
- All PDF text was extracted successfully; the authoritative acceptance checklist is stored in `docs/3d-visualization-requirements.md`.
- The requested product is a new authenticated national overview at `/home`, not an enhanced placeholder route.
- `/home` must be authentication-only and must not require MGMT, EMGC, or DIAG permissions; individual platform and module entries retain their existing permission checks.
- The requested China map includes Polygon/MultiPolygon extrusion, province boundaries, outline glow, scan light, city nodes, pulse rings, fly lines, hover, selection, tooltip, focus camera, OrbitControls, auto-rotation, and reset view.
- Nationwide aggregates may use deterministic centralized mock data behind an adapter; existing real APIs must be reused and no backend URL may be invented.
- The old `/mgmt/gis/3d` path becomes a query-preserving redirect to `/home`; `/mgmt/gis` remains the detailed 2D GIS page.
- WebGL/GeoJSON errors must degrade only the map surface, not log the user out or redirect away from the home page.

## Target project
- Delivery root: `E:\Whd\USCDIP\USCDIP-code`.
- Frontend boundary: `frontend` (Vue 3, Vite, Pinia).
- Backend boundary: `backend` (Spring Boot, Java 17).
- Existing frontend includes `GisMapView.vue` and `Gis3DPlaceholderView.vue`, indicating an intended but incomplete 3D GIS surface.
- Existing backend includes GIS DTOs, services, controllers, repositories, and integration tests.
- `frontend/package.json` already includes `three@^0.184.0` and `lucide-vue-next`.
- `Gis3DPlaceholderView.vue` is a functional Three.js scene rather than an empty page: it loads bbox/object GIS data, parses POINT/LINESTRING WKT, renders a grid and object meshes, handles resize, and falls back to the 2D route when WebGL or data is unavailable.
- The current 3D scene is still explicitly scoped as a placeholder: it auto-rotates the entire scene, lacks direct orbit/pan/zoom controls, layer visibility controls, object picking, tooltips, camera presets, scene metrics, and a production-grade operational side panel.
- The `/mgmt/gis/3d` route is permission-protected and already preserves bbox/object query context.
- Baseline frontend dependency installation, `npm run typecheck`, and `npm run build` all pass before feature implementation.
- Baseline production build warns that the existing `Gis3DPlaceholderView` chunk is 517.56 kB; keeping the new Three.js home route lazy-loaded and decomposed remains important.
- Existing frontend has no automated unit/e2e test framework; verification must combine typecheck/build with real browser flows and backend tests where applicable.
- Current OIDC callback stores tokens then waits 900 ms and always navigates to `/portal`; processed callbacks remain on the callback page.
- Current emergency login loads the user but remains on `/portal`; current logout clears local state but does not navigate.
- The existing `redirect` query is written by the route guard but never consumed, so safe post-login restoration does not exist yet.
- `RoutePermissionMeta` in API types does not augment Vue Router; a real module augmentation file is required for `authOnly`.
- Current 3D teardown disposes only the renderer and Canvas, not geometry/material/texture/controls/context listeners; the old 3-second 2D fallback conflicts with the PDF.

## National GeoJSON
- Added `frontend/src/assets/geo/china.json` from the DataV national full-boundary dataset.
- Validation: 582,522 bytes, `FeatureCollection`, 35 features, both Polygon and MultiPolygon geometry.
- The data includes 34 named province-level regions and one unnamed `100000_JD` MultiPolygon boundary/island feature; named features should drive province interaction while the extra feature may contribute only to national outline rendering.
- Province properties provide `adcode` and generally `center`/`centroid`; missing centroids must fall back to geometry-derived centers.

## Real business data boundary
- Reusable authorized-scope APIs exist for devices, alerts, incidents, work orders, notifications, platforms, menus, and current user.
- Their totals are filtered by current permissions/data scope and must not overwrite nationwide Mock KPIs.
- Existing alert API cannot precisely query active-processing status; national active-alert totals remain stable Mock until a real aggregate contract exists.
- Home-page real business modules should be permission-gated and isolated with `Promise.allSettled()` so one platform's failure or missing permission does not blank the entire home page.
- Platform cards should use `checkPlatformRoute`; module shortcuts should use `checkPermissions` with the same permission pairs as route meta.

## Reference project
- Repository: `https://github.com/knight-L/sc-datav`.
- Cloned read-only reference at `E:\Whd\USCDIP\.codex_tmp\sc-datav-reference`.
- Reference commit: `c1aebf1d9ceb6067d41cb6fd7440b00ec2d71f8e`.
- Detailed structure and reusable patterns are pending focused inspection.
- Stack: React 19, Three.js 0.183, React Three Fiber/Drei, ECharts 6, d3-geo, Zustand, GSAP, and styled-components.
- Visualization pages follow a repeated `demo + map + panel + store` organization.
- Map rendering is decomposed into focused modules such as base geometry, shape UV mapping, boundary shader, labels/tooltips, heatmap textures, fly lines, bars/cones, lights, decorative base, and cloud effects.
- The scenes use geographic projection, OrbitControls, reusable animation hooks/state, instance rendering, shader materials, and HTML overlays.
- The reusable architectural idea is more valuable than its React implementation: preserve Vue/Three.js in the target while adopting scene decomposition and operational overlays.
- The reference repository is Apache-2.0 licensed. No source code has been copied; only architectural patterns have been recorded.
- A target mapping and proposed module boundary document now exists at `docs/3d-visualization-architecture.md`.

## UX system
- `ui-ux-pro-max` design system was generated at `design-system/uscdip-3d-gis/MASTER.md`.
- Applicable guidance: technical dark dashboard, minimal glow, clear focus states, 150-300 ms interaction feedback, visible loading states, no horizontal overflow, responsive checks at 375/768/1024/1440 px, and reduced-motion support.
- The generated marketing-style horizontal journey recommendation is not applicable to this operational GIS surface; the existing dense work-focused shell remains the governing layout pattern.

## Working rules
- Record discoveries after no more than two search/view operations.
- All implementation changes must stay inside `USCDIP-code`.
- Temporary reference material may live under `E:\Whd\USCDIP\.codex_tmp` and is not part of the deliverable.
- PDF acceptance status is tracked in `docs/3d-visualization-requirements.md` and must be audited item by item before completion.
- All 24 PDF pages were rendered at 90 DPI and reviewed in four contact sheets; the visual document contains continuous text/code requirements and no additional diagrams, screenshots, or hidden visual acceptance details.
- Native Poppler was found at the bundled runtime path and successfully produced 24 PNG pages despite the broken wrapper command.

## Final evidence
- The completed scene is split into Vue host components, a framework-neutral `NationalMapScene`, projection/config/types, deterministic data adapters, and a recursive Three.js disposer.
- The real GIS object detail endpoint returns `EPSG:4490` anchor coordinates; `/home` now turns authorized `objectType/objectId` context into a projected 3D beacon without inventing an API.
- Anonymous `/home` reaches `/portal`; authenticated `/portal` reaches the safe internal target or `/home`; external and callback redirects are rejected.
- `bg_active_hz / BreakGlass123!` now matches the seed BCrypt hash and was verified after a clean backend restart.
- A BREAK_GLASS_COMMAND user reaches `/home` without MGMT, while MGMT and DIAG cards and their modules remain disabled.
- WebGL context-loss injection leaves the user on `/home`, preserves non-map content, exposes retry/2D options, and recreates exactly one canvas with no warning.
- Desktop/mobile screenshots and canvas captures are stored under `output/playwright`; sampled canvas colors prove nonblank rendering.
- Final build/test state: frontend typecheck PASS, frontend production build PASS, backend 130/130 tests PASS, browser normal flow 0 errors/0 warnings.
- External OIDC was unavailable locally, so the provider round trip remains the only unexecuted environmental integration; its callback and redirect code paths passed static/type/build inspection.

## Account management feature
- The backend already has `UserAccountEntity`, `UserRoleEntity`, `UserDataScopeEntity`, role/permission repositories, and platform-admin checks in auth administration controllers.
- There is no complete account lifecycle API or frontend account management route yet.
- The first release will cover list/search, create, role assignment, primary/data scope assignment, enable/disable, and local password reset.
- Account writes must be platform-admin-only, must not expose password hashes, and must prevent an administrator from disabling their own active account.
- Local login credentials are stored in `emergency_account`, while identity and authorization live in `user_account`, `rbac_user_role`, and `user_data_scope`; account creation must update all four models atomically.
- The new backend contract uses `/api/auth/admin/accounts`, requires `PLATFORM_ADMIN + ENTRY:MGMT`, treats usernames as immutable, and revokes sessions after security-sensitive changes.
- The completed implementation reuses the existing identity, RBAC, data-scope, local credential, token, and audit tables; no second account system was introduced.
- Full regression testing increased the suite from 130 to 138 passing tests, including three end-to-end account-management integration tests.
- Browser verification confirmed the management view at 1440 px and 375 px with no page-level horizontal overflow, working create/disable flows, and a clean console after accessibility adjustments.

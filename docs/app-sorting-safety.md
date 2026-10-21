# App sorting and home-screen compaction: safety contract

Target: Lawnchair 16, `feature/amoled-pure-colors`. Do not change Quickstep or navigation.

## Verified integration points

- `lawnchair/src/app/lawnchair/preferences/PreferenceManager.kt`: persistent drawer sort preference.
- `lawnchair/src/app/lawnchair/ui/preferences/destinations/AppDrawerPreferences.kt`: drawer sort UI.
- `src/com/android/launcher3/allapps/AlphabeticalAppsList.java`: drawer list sorting; the private-space stream must stay separate.
- `lawnchair/src/app/lawnchair/ui/preferences/destinations/HomeScreenPreferences.kt`: home actions and lock state.
- `src/com/android/launcher3/model/ModelWriter.java`: persistent item movement.

## Home-screen compaction preconditions

1. **Explicit user action only**. Never compact automatically on startup, device rotation, profile switch or grid resize.
2. **No deletion**. Preserve item IDs, intents, user handles, titles, folders and their contents.
3. **Fixed obstacles**. Keep widgets, folder icons, dock/hotseat items, promise/restore items and other non-standard items in their original cells. Compact only ordinary desktop app icons into verified free cells.
4. **Geometry**. Read each screen's actual cell dimensions and spans; never assume a phone-sized grid on tablets/foldables. Do not move between profiles.
5. **Collision prevention**. Compute occupancy of all items before planning; require every destination to be in bounds, free and unique.
6. **Atomicity and rollback**. Construct and validate the complete plan before any write; persist via the model writer only after an undo/recovery strategy has been implemented and tested.
7. **Lock and concurrency**. Respect home-screen lock and avoid updates while workspace/model is loading, dragging or transitioning.
8. **Confirmation**. Show affected icon count and warn that layout positions will change. Provide undo or a layout backup before enabling.
9. **No-op**. Empty desktops and already compacted layouts must not write to the database.

## Drawer follow-up

- Keep `default` identical to existing A–Z ordering and retain profile isolation.
- Implement usage-based ranking only with a user-visible usage-access explanation, explicit consent and graceful A–Z fallback when access is denied.
- Custom order needs a durable per-user component-key sequence, a reorder UI, and deterministic insertion for newly installed apps.
- Locale-specific grouping and fast-scroll labels must be validated for non-alphabetical modes.
- Avoid repeated synchronous package-manager calls during app-list updates; cache installation metadata and invalidate on package changes.

## Validation before any APK build

- Static checks: Kotlin/Java imports, preference keys, all mode values and callbacks.
- Unit tests: stable ordering, duplicate package activities, multi-user/private space, uninstalled apps, missing permissions.
- Compaction tests: mixed widgets/folders/icons, sparse screens, tablets, locked desktop, rollback and idempotence.
- Only after the above passes: a single APK build for device testing.

## Current status

The drawer UI exposes default, Z–A and recent-install modes. **Usage-based/custom sorting and home compaction are not yet implemented.** This document is a guardrail, not a claim of feature completion.

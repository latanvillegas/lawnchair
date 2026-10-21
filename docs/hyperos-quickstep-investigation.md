# HyperOS 3 gesture navigation: provider-selection investigation

## Verified on Redmi Note 13 Pro 4G (23117RA68G), Android 16 / API 36, HyperOS OS3.0

With Xiaomi launcher and gestures:
- `settings get secure navigation_mode` = `2`
- `settings get global force_fsg_nav_bar` = `1`

With `app.lawnchair.nightly` selected as HOME:
- `navigation_mode` = `0`
- `force_fsg_nav_bar` = `0`
- `cmd package query-services --brief -a android.intent.action.QUICKSTEP_SERVICE` lists both `com.miui.home/.recents.TouchInteractionService` and `app.lawnchair.nightly/com.android.quickstep.TouchInteractionService`.
- SystemUI dump: `mRecentsComponentName=ComponentInfo{com.miui.home/com.miui.home.recents.RecentsActivity}`.
- SystemUI dump: `mQuickStepIntent=Intent { act=android.intent.action.QUICKSTEP_SERVICE xflg=0x4 pkg=com.miui.home }`.
- SystemUI dump: `mIsGestureNavigation=false`.
- Activity service dump: Xiaomi Quickstep has a connection from `com.android.systemui`; Lawnchair Quickstep connections observed from `app.lawnchair.nightly`, not SystemUI.

## Source mechanism

AOSP `OverviewProxyService` derives the recents component from framework resource `com.android.internal.R.string.config_recentsComponentName`, then binds a Quickstep intent restricted to that component's package. The actual HyperOS framework implementation has not been decompiled or inspected, so its precise policy is still unverified.

Source: https://android.googlesource.com/platform/frameworks/base/+/4fd8e1246d9b503f602412021df52e87e418ed1c/packages/SystemUI/src/com/android/systemui/recents/OverviewProxyService.java

The project's `quickstep/AndroidManifest.xml` already declares exported `com.android.quickstep.TouchInteractionService` with `android.permission.STATUS_BAR_SERVICE` and `android.intent.action.QUICKSTEP_SERVICE`. Registration alone does not make it the provider chosen by SystemUI.

## Compatibility constraints

- No change to Quickstep manifest, SystemUI, navigation settings, or launcher selection on Lenovo or any device.
- No automatic writes to `navigation_mode`, `force_fsg_nav_bar`, or overlay state.
- No claim that `WRITE_SECURE_SETTINGS` grants control of SystemUI's recents provider.
- Do not treat a generic navigation overlay as proof that native OEM gestures work.
- Third-party root/LSPosed hooking projects for HyperOS 3 exist, but are not suitable as an unconditional Lawnchair patch.

## Next research question

Determine whether the OEM's `config_recentsComponentName` and gesture restriction can be changed using a supported, unprivileged API. If not, classify native gestures with third-party HOME as firmware-limited and consider an explicit, opt-in external integration only. Preserve existing Lenovo behavior.

## Diagnostic

Run `tools/diagnose-navigation-quickstep.sh` from Termux with Shizuku rish. It is read-only.

#!/data/data/com.termux/files/usr/bin/bash
# Read-only navigation/Quickstep compatibility diagnostic.
# Run on-device in Termux after Shizuku rish is configured.
set -u

RISH="${RISH:-rish}"
if ! command -v "$RISH" >/dev/null 2>&1; then
  echo "rish not found. Configure Shizuku for Termux first." >&2
  exit 1
fi

shell() { "$RISH" -c "$1"; }
echo "=== DEVICE ==="
shell 'getprop ro.product.manufacturer'
shell 'getprop ro.product.model'
shell 'getprop ro.build.version.release'
shell 'getprop ro.build.version.sdk'
shell 'getprop ro.mi.os.version.name'

echo "=== HOME RESOLUTION ==="
shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

echo "=== NAVIGATION SETTINGS (READ ONLY) ==="
shell 'settings get secure navigation_mode'
shell 'settings get secure system_navigation_keys_enabled'
shell 'settings get global force_fsg_nav_bar'

echo "=== QUICKSTEP SERVICE CANDIDATES ==="
shell 'cmd package query-services --brief -a android.intent.action.QUICKSTEP_SERVICE'

echo "=== SYSTEMUI ACTUAL PROVIDER ==="
shell 'dumpsys activity service com.android.systemui/.SystemUIService' \
  | grep -i -E 'mRecentsComponentName=|mQuickStepIntent=|mIsGestureNavigation=|mNavigationMode=' \
  | head -25 || true

echo "=== NAVIGATION OVERLAYS (INFORMATIONAL) ==="
shell 'cmd overlay list' \
  | grep -i -E 'navbar|navigation|gesture|threebutton' \
  | head -30 || true

echo "=== END: NO SETTINGS WERE CHANGED ==="

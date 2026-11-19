#!/system/bin/sh
# Runs as root in the background on every boot.
MODDIR=${0%/*}
PKG=com.websnake.recentstv

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
rm -f "$MODDIR/bootcount"

# Usage access fills the row with apps used lately that own no task. The app checks the app-op itself, which a
# granted PACKAGE_USAGE_STATS permission doesn't set; through Shizuku the app sets it, here root does.
appops set --user 0 "$PKG" GET_USAGE_STATS allow

# The remote trigger (Settings → Open with: Hold Home by default) is the app's accessibility service. The app turns
# it on itself when it runs; this covers the first boot, before it ever has.
SVC="$PKG/$PKG.trigger.TriggerService"
CUR=$(settings get secure enabled_accessibility_services)
case "$CUR" in
    *"$SVC"*) ;;
    ""|null) settings put secure enabled_accessibility_services "$SVC" ;;
    *) settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
esac
settings put secure accessibility_enabled 1

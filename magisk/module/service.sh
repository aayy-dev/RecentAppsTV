#!/system/bin/sh
# Runs as root in the background on every boot.
MODDIR=${0%/*}
PKG=com.websnake.recentstv

# Boot guard, second half: post-fs-data.sh only counts boots. A crash loop of the system server (a permission the
# system refuses, say) restarts it inside one boot and never reaches that script again. Three restarts before boot
# completes disable the module and reboot without it.
LAST=$(pidof system_server)
RESTARTS=0
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
    NOW=$(pidof system_server)
    if [ -n "$NOW" ] && [ "$NOW" != "$LAST" ]; then
        [ -n "$LAST" ] && RESTARTS=$((RESTARTS + 1))
        LAST=$NOW
    fi
    if [ "$RESTARTS" -ge 3 ]; then
        touch "$MODDIR/disable"
        rm -f "$MODDIR/bootcount"
        reboot
        exit 0
    fi
done
rm -f "$MODDIR/bootcount"

# Usage access fills the row with apps used lately that own no task. The app checks the app-op itself, which a
# granted PACKAGE_USAGE_STATS permission doesn't set; through Shizuku the app sets it, here root does.
appops set --user 0 "$PKG" GET_USAGE_STATS allow

# The remote trigger (Customize -> Open with: Hold Home by default) is the app's accessibility service. Turn it on
# once, here, for a first boot before the app ever ran; after that it is the user's: if they switch it off in
# Accessibility it stays off (the app turns it on again only when they pick a trigger in Customize).
if [ ! -f "$MODDIR/a11y-done" ]; then
    SVC="$PKG/$PKG.trigger.TriggerService"
    CUR=$(settings get secure enabled_accessibility_services)
    case "$CUR" in
        *"$SVC"*) ;;
        ""|null) settings put secure enabled_accessibility_services "$SVC" ;;
        *) settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
    esac
    settings put secure accessibility_enabled 1
    touch "$MODDIR/a11y-done"
fi

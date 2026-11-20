#!/system/bin/sh
# Runs early in every boot, before the package manager starts.
MODDIR=${0%/*}

# Magisk serves the module's files with a 1970 timestamp, so the package manager would keep its cached parse of
# the old APK after an upgrade and miss new components or permissions.
rm -f /data/system/package_cache/*/RecentAppsTV-* /data/system/package_cache/*/RecentAppsTVFrameworkOverlay*

# Boot guard: service.sh clears the count once boot completes. Three boots in a row that never get there
# disable the module, so the next boot runs without it. (service.sh also watches for the system server
# restarting inside one boot, which this script never sees.)
COUNT=$(cat "$MODDIR/bootcount" 2>/dev/null)
COUNT=$((${COUNT:-0} + 1))
if [ "$COUNT" -ge 3 ]; then
    touch "$MODDIR/disable"
    rm -f "$MODDIR/bootcount"
else
    echo "$COUNT" > "$MODDIR/bootcount"
fi

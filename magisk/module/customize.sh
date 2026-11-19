#!/system/bin/sh
# Runs once at install. $MODPATH is the module's new folder.
PKG=com.websnake.recentstv

# A normal install (the Shizuku build, or a sideloaded APK) is signed with another key and lives in /data/app.
# Next to the system copy it would either shadow it (no privileges) or be wiped by the system at boot, settings
# included. Ask for it to be removed first.
if command -v pm >/dev/null 2>&1; then
    case "$(pm path "$PKG" 2>/dev/null)" in
        *"/data/app/"*)
            ui_print "! Recent Apps TV is installed as a normal app."
            ui_print "! Uninstall it first, then install this module again:"
            ui_print "!   adb uninstall $PKG"
            abort "! Aborted, nothing was changed"
            ;;
    esac
fi
ui_print "- Recent Apps TV goes in as a privileged system app"
ui_print "- Reboot to finish"

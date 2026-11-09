set -e
mkdir -p apk
cd apk
gh release download siste -R "$GITHUB_REPOSITORY" -p "*.apk" --clobber
rm -f vakt.apk meldinger.apk skjermtid.apk
for a in *.apk; do adb install -r -g "$a" || echo "kunne ikke installere $a"; done
cd ..
adb shell pm grant me.arnee.verktoy android.permission.WRITE_SECURE_SETTINGS || true
adb shell appops set me.arnee.verktoy SYSTEM_ALERT_WINDOW allow || true
adb shell pm grant me.arnee.gratone android.permission.WRITE_SECURE_SETTINGS || true
adb shell cmd role add-role-holder android.app.role.SMS me.arnee.sms || true
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

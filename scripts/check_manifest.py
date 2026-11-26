#!/usr/bin/env python3
"""Fails the build when the final manifest asks for more than Lirnyk v1 needs.

Checks the merged manifest of the release build (what dependencies add counts too) against:
  * an allowlist of permissions: any new permission must be added here on purpose, with a reason;
  * a denylist of components that make Play Protect block sideloaded installs or that are sensitive
    (notification listener, accessibility, device admin, VPN, input method, ...).

Usage: python3 scripts/check_manifest.py <merged AndroidManifest.xml> [<aapt2 permissions dump>]
"""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"

ALLOWED_PERMISSIONS = {
    "android.permission.READ_MEDIA_AUDIO": "list music (Android 13+)",
    "android.permission.READ_EXTERNAL_STORAGE": "list music (Android 12 and older, maxSdkVersion 32)",
    "android.permission.FOREGROUND_SERVICE": "background playback",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK": "background playback (Android 14+)",
    "android.permission.POST_NOTIFICATIONS": "playback notification",
    "android.permission.WAKE_LOCK": "keep playing with the screen off",
    "android.permission.RECEIVE_BOOT_COMPLETED": "continue music after the head unit starts (user setting)",
    # Added by androidx.core for receivers registered as not exported; signature-level, private to the app.
    "app.lirnyk.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION": "androidx private receiver permission",
    "app.lirnyk.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION": "androidx private receiver permission (debug)",
    "app.lirnyk.remote.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION": "androidx private receiver permission (Spotify prototype)",
}

# Play Protect blocks internet-sideloaded apps that declare these (developers.google.com/android/play-protect/warning-dev-guidance),
# or they are sensitive and not needed by a music player.
FORBIDDEN_BIND_PERMISSIONS = {
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_DEVICE_ADMIN",
    "android.permission.BIND_VPN_SERVICE",
    "android.permission.BIND_INPUT_METHOD",
    "android.permission.BIND_AUTOFILL_SERVICE",
    "android.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE",
}
FORBIDDEN_ACTIONS = {
    "android.service.notification.NotificationListenerService",
    "android.accessibilityservice.AccessibilityService",
    "android.app.action.DEVICE_ADMIN_ENABLED",
    "android.provider.Telephony.SMS_RECEIVED",
    "android.net.VpnService",
}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    root = ET.parse(sys.argv[1]).getroot()
    errors = []

    declared = set()
    for tag in ("uses-permission", "uses-permission-sdk-23"):
        for el in root.iter(tag):
            declared.add(el.get(A + "name"))
    if len(sys.argv) > 2:
        # aapt2 "dump permissions" of the final APK: lines like  uses-permission: name='x'
        for line in open(sys.argv[2], encoding="utf-8"):
            if "uses-permission" in line and "name='" in line:
                declared.add(line.split("name='", 1)[1].split("'", 1)[0])
    for p in sorted(declared - set(ALLOWED_PERMISSIONS)):
        errors.append(f"permission not on the allowlist: {p}")

    app = root.find("application")
    for comp in ("service", "receiver", "activity", "provider"):
        for el in (app.iter(comp) if app is not None else []):
            name = el.get(A + "name")
            perm = el.get(A + "permission")
            if perm in FORBIDDEN_BIND_PERMISSIONS:
                errors.append(f"{comp} {name} is protected by {perm}")
            for action in el.iter("action"):
                if action.get(A + "name") in FORBIDDEN_ACTIONS:
                    errors.append(f"{comp} {name} handles {action.get(A + 'name')}")
            if el.get(A + "isAccessibilityTool") == "true":
                errors.append(f"{comp} {name} claims isAccessibilityTool")

    print("permissions:")
    for p in sorted(declared):
        print(f"  {p} — {ALLOWED_PERMISSIONS.get(p, 'NOT ALLOWED')}")
    if errors:
        for e in errors:
            print(f"::error::{e}")
        return 1
    print("manifest check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())

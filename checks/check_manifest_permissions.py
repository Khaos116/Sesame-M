"""Cheap static guard: any android.permission.* referenced by Java/Kotlin source that requires a
manifest <uses-permission> declaration must actually have one, so requesting it doesn't crash.
Currently only checks REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (the one that has actually broken),
not a general permission scanner.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
permission_util = (ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/PermissionUtil.java").read_text(encoding="utf-8")

assert "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in permission_util, \
    "PermissionUtil no longer requests battery-optimization exemption; check is stale"
assert 'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />' in manifest, \
    "AndroidManifest.xml is missing REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — " \
    "requesting it without this declaration throws SecurityException/crashes on the settings button"
print("PASS: battery-optimization permission declared for the code that requests it")

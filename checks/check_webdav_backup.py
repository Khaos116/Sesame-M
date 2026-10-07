"""Guard the user-requested removal of WebDAV from the shipped app."""
from pathlib import Path

main = Path(__file__).resolve().parent.parent / "app/src/main"
for path in main.rglob("*"):
    if path.is_file() and path.suffix in {".java", ".kt", ".xml", ".json", ".txt"}:
        assert "webdav" not in path.read_text(encoding="utf-8").lower(), f"WebDAV remains in {path}"
print("PASS: no WebDAV entry, activity, client or app resource remains")

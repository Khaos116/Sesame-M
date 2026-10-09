"""Prevent the removed planting flowers module from returning to source or task/UI registration."""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "app/src/main/java"
assert not any(file.is_file() for file in source.rglob("*PlantingFlowers*")), "removed implementation restored"
for file in source.rglob("*"):
    if file.suffix in {".java", ".kt"}:
        text = file.read_text(encoding="utf-8")
        assert "plantingFlowers" not in text and "PlantingFlowers" not in text, file
print("PASS planting flowers removed: no implementation, task registration or UI entry")

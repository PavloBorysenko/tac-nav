#!/usr/bin/env python3
"""Check audit.mjs lists wp-browser-sensor and local-code-review as recommended sensors."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

EVALS_DIR = Path(__file__).resolve().parent
SKILL_DIR = EVALS_DIR.parent
AUDIT = SKILL_DIR / "scripts" / "audit.mjs"
CATALOG = SKILL_DIR / "references" / "skill-catalog.json"
PROVISION = SKILL_DIR / "references" / "audit-provision.md"
SENSORS = ("wp-browser-sensor", "local-code-review")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_site(root: Path) -> None:
    write(root / "wp-load.php", "<?php\n")
    write(root / "wp-includes" / "version.php", "<?php\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "style.css", "/* Theme Name: ACME */\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "functions.php", "<?php\n")
    subprocess.run(["git", "init"], cwd=root, check=True, capture_output=True)
    subprocess.run(["git", "add", "-A"], cwd=root, check=True, capture_output=True)
    subprocess.run(
        [
            "git",
            "-c",
            "user.email=eval@example.com",
            "-c",
            "user.name=Eval",
            "commit",
            "-m",
            "init",
        ],
        cwd=root,
        check=True,
        capture_output=True,
    )


def audit(root: Path) -> dict:
    proc = subprocess.run(
        ["node", str(AUDIT), "--root", str(root)],
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(proc.stdout)


def ids(rows: list[dict]) -> set[str]:
    return {row["id"] for row in rows}


def check_catalog() -> list[str]:
    failures: list[str] = []
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {row["id"]: row for row in catalog.get("recommendedByShape") or []}
    for skill_id in SENSORS:
        row = by_id.get(skill_id)
        if row is None:
            failures.append(f"skill-catalog.json missing recommended {skill_id}")
            continue
        if row.get("when") != "always":
            failures.append(f"{skill_id} when must be always, got {row.get('when')!r}")
        if row.get("source") != "supernova-pack":
            failures.append(f"{skill_id} source must be supernova-pack")
    provision = PROVISION.read_text(encoding="utf-8")
    for needle in (
        "If `local-code-review` is offered",
        "If `wp-browser-sensor` is offered",
        "install-skills.mjs",
        "copy-local-code-review.mjs",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing {needle}")
    return failures


def main() -> int:
    failures = check_catalog()
    with tempfile.TemporaryDirectory(prefix="wp-harness-sensors-") as tmp:
        missing_root = Path(tmp) / "missing"
        missing_root.mkdir()
        make_site(missing_root)
        missing = audit(missing_root)
        rec_missing = ids(missing["skills"]["recommended"]["missing"])
        rec_present = ids(missing["skills"]["recommended"]["present"])
        for skill_id in SENSORS:
            if skill_id not in rec_missing:
                failures.append(f"missing site: {skill_id} should be recommended.missing")
            if skill_id in rec_present:
                failures.append(f"missing site: {skill_id} should not be recommended.present")
        notes = missing.get("notes") or []
        if not any("local-code-review is not vendored" in note for note in notes):
            failures.append("missing site: expected local-code-review vendor note")
        if not any("wp-browser-sensor is not vendored" in note for note in notes):
            failures.append("missing site: expected wp-browser-sensor vendor note")

        present_root = Path(tmp) / "present"
        present_root.mkdir()
        make_site(present_root)
        write(
            present_root / ".agents" / "skills" / "local-code-review" / "SKILL.md",
            "---\nname: local-code-review\n---\n# Local code review\n",
        )
        present = audit(present_root)
        if "local-code-review" not in ids(present["skills"]["recommended"]["present"]):
            failures.append("vendored site: local-code-review should be recommended.present")
        if "local-code-review" in ids(present["skills"]["recommended"]["missing"]):
            failures.append("vendored site: local-code-review should not be recommended.missing")
        if any("local-code-review is not vendored" in note for note in present.get("notes") or []):
            failures.append("vendored site: unexpected local-code-review vendor note")

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

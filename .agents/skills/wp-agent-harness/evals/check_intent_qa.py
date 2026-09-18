#!/usr/bin/env python3
"""Check empty-site intent QA: offerOnIntent, empty phpcs.xml, mandatory QA loop."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

EVALS_DIR = Path(__file__).resolve().parent
SKILL_DIR = EVALS_DIR.parent
AUDIT = SKILL_DIR / "scripts" / "audit.mjs"
WRITE_PHPCS = SKILL_DIR / "scripts" / "write-phpcs-config.mjs"
PROVISION = SKILL_DIR / "references" / "audit-provision.md"
SKILL_MD = SKILL_DIR / "SKILL.md"
PHPCS_MD = SKILL_DIR / "references" / "phpcs.md"
CURSOR_RULES = SKILL_DIR / "references" / "cursor-rules.md"
COMPONENT_SKILL = SKILL_DIR.parent / "wordpress-component-creation" / "SKILL.md"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_empty_site(root: Path) -> None:
    write(root / "wp-load.php", "<?php\n")
    write(root / "wp-includes" / "version.php", "<?php\n")
    write(root / "wp-content" / "themes" / "twentytwentyfive" / "style.css", "/* Theme Name: Twenty */\n")
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


def make_first_party_site(root: Path) -> None:
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


def check_docs() -> list[str]:
    failures: list[str] = []
    provision = PROVISION.read_text(encoding="utf-8")
    for needle in (
        "First-party intent (empty repo)",
        "Yes — offer the QA toolbox",
        "qa.offerOnIntent",
        "qa.emptyFirstParty",
        "the QA loop is **required**",
        "Do not offer `Not now`",
        "--allow-empty-files",
        "New non-tiny component (not a full audit)",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing {needle}")
    skill = SKILL_MD.read_text(encoding="utf-8")
    for needle in (
        "qa.emptyFirstParty",
        "qa.offerOnIntent",
        "Do not offer Not now",
        "new **non-tiny** first-party theme or plugin",
    ):
        if needle not in skill:
            failures.append(f"SKILL.md missing {needle}")
    phpcs = PHPCS_MD.read_text(encoding="utf-8")
    if "--allow-empty-files" not in phpcs:
        failures.append("phpcs.md missing --allow-empty-files")
    rules = CURSOR_RULES.read_text(encoding="utf-8")
    if "Do not offer `Not now`" not in rules:
        failures.append("cursor-rules.md must forbid Not now for the QA loop")
    if COMPONENT_SKILL.is_file():
        component = COMPONENT_SKILL.read_text(encoding="utf-8")
        if "phpstan.neon.dist" not in component:
            failures.append("wordpress-component-creation/SKILL.md must register non-tiny paths")
        if "Do not create `phpcs.xml.dist` from scratch" not in component:
            failures.append("wordpress-component-creation/SKILL.md must not create phpcs.xml.dist from scratch")
    return failures


def main() -> int:
    failures = check_docs()
    with tempfile.TemporaryDirectory(prefix="wp-harness-intent-") as tmp:
        empty_root = Path(tmp) / "empty"
        empty_root.mkdir()
        make_empty_site(empty_root)
        empty = audit(empty_root)
        qa = empty.get("qa") or {}
        if qa.get("emptyFirstParty") is not True:
            failures.append("empty site: qa.emptyFirstParty must be true")
        offer = qa.get("offerOnIntent") or {}
        for key in (
            "phpcsConfig",
            "phpstan",
            "lintJs",
            "phpunit",
            "jest",
            "qaLoop",
        ):
            if offer.get(key) is not True:
                failures.append(f"empty site: qa.offerOnIntent.{key} must be true")
        missing = qa.get("missing") or {}
        if missing.get("phpcsConfig") or missing.get("phpstan") or missing.get("phpunit"):
            failures.append("empty site: qa.missing must stay false without first-party paths")
        notes = empty.get("notes") or []
        if not any("will get a custom theme or plugin" in note for note in notes):
            failures.append("empty site: expected first-party intent note")
        if not any("Do not offer Not now" in note for note in notes):
            failures.append("empty site: expected mandatory QA-loop note")

        first_root = Path(tmp) / "first"
        first_root.mkdir()
        make_first_party_site(first_root)
        first = audit(first_root)
        if first.get("qa", {}).get("emptyFirstParty"):
            failures.append("first-party site: qa.emptyFirstParty must be false")

        refuse = subprocess.run(
            [
                "node",
                str(WRITE_PHPCS),
                "--confirm",
                "--root",
                str(empty_root),
                "--prefixes",
                "Acme,acme_",
            ],
            capture_output=True,
            text=True,
        )
        if refuse.returncode != 2:
            failures.append("write-phpcs-config without --files must exit 2 unless --allow-empty-files")

        allowed = subprocess.run(
            [
                "node",
                str(WRITE_PHPCS),
                "--confirm",
                "--root",
                str(empty_root),
                "--prefixes",
                "Acme,acme_",
                "--allow-empty-files",
            ],
            capture_output=True,
            text=True,
        )
        if allowed.returncode != 0:
            failures.append(f"write-phpcs-config --allow-empty-files failed: {allowed.stderr}")
        xml = (empty_root / "phpcs.xml.dist").read_text(encoding="utf-8")
        if "<file>" in xml:
            failures.append("intent phpcs.xml.dist must not invent <file> paths")
        if "acme_" not in xml:
            failures.append("intent phpcs.xml.dist must keep approved prefixes")

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

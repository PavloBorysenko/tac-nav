#!/usr/bin/env python3
"""Check audit offers the WordPress skills set plus testing/component skills on an empty site."""

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
SKILL_MD = SKILL_DIR / "SKILL.md"

WORDPRESS_SET_IDS = (
    "wordpress-router",
    "wp-phpstan",
    "wp-plugin-development",
    "wp-block-themes",
    "wp-block-development",
    "wp-rest-api",
    "wp-performance",
    "wp-patterns",
    "wp-playground",
    "wpds",
)
PACK_ALWAYS = ("wordpress-testing", "wordpress-component-creation")


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


def make_block_theme_site(root: Path) -> None:
    write(root / "wp-load.php", "<?php\n")
    write(root / "wp-includes" / "version.php", "<?php\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "style.css", "/* Theme Name: ACME */\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "functions.php", "<?php\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "theme.json", "{}\n")
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


def catalog_rows(catalog: dict) -> list[dict]:
    return [
        *(catalog.get("alwaysRequired") or []),
        *(catalog.get("requiredByShape") or []),
        *(catalog.get("recommendedByShape") or []),
    ]


def check_catalog() -> list[str]:
    failures: list[str] = []
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    by_id = {row["id"]: row for row in catalog_rows(catalog)}
    for skill_id in WORDPRESS_SET_IDS:
        row = by_id.get(skill_id)
        if row is None:
            failures.append(f"skill-catalog.json missing {skill_id}")
            continue
        if row.get("source") != "WordPress/agent-skills":
            failures.append(f"{skill_id} source must be WordPress/agent-skills")
        if row.get("set") != "wordpress":
            failures.append(f"{skill_id} must have set: wordpress")
    recommended = {row["id"]: row for row in catalog.get("recommendedByShape") or []}
    for skill_id in PACK_ALWAYS:
        row = recommended.get(skill_id)
        if row is None:
            failures.append(f"skill-catalog.json missing recommended {skill_id}")
            continue
        if row.get("when") != "always":
            failures.append(f"{skill_id} when must be always, got {row.get('when')!r}")
        if row.get("source") != "supernova-pack":
            failures.append(f"{skill_id} source must be supernova-pack")
    if by_id.get("wp-block-themes", {}).get("when") != "themeJson":
        failures.append("wp-block-themes required when must stay themeJson")
    return failures


def check_docs() -> list[str]:
    failures: list[str] = []
    provision = PROVISION.read_text(encoding="utf-8")
    for needle in (
        "Install WordPress skills set",
        "Do not install WordPress skills",
        "skills.wordpressSet.missing",
        "If `wordpress-testing` is offered",
        "If `wordpress-component-creation` is offered",
        "Do not skip `wordpress-testing` or `wordpress-component-creation`",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing {needle}")
    skill = SKILL_MD.read_text(encoding="utf-8")
    for needle in (
        "WordPress skills set",
        "wordpress-testing",
        "wordpress-component-creation",
        "even with no custom plugin or theme",
    ):
        if needle not in skill:
            failures.append(f"SKILL.md missing {needle}")
    return failures


def main() -> int:
    failures = check_catalog() + check_docs()
    with tempfile.TemporaryDirectory(prefix="wp-harness-wpset-") as tmp:
        empty_root = Path(tmp) / "empty"
        empty_root.mkdir()
        make_empty_site(empty_root)
        empty = audit(empty_root)
        set_missing = ids(empty["skills"]["wordpressSet"]["missing"])
        set_present = ids(empty["skills"]["wordpressSet"]["present"])
        required_missing = ids(empty["skills"]["required"]["missing"])
        rec_missing = ids(empty["skills"]["recommended"]["missing"])
        for skill_id in WORDPRESS_SET_IDS:
            if skill_id not in set_missing:
                failures.append(f"empty site: {skill_id} should be wordpressSet.missing")
            if skill_id in set_present:
                failures.append(f"empty site: {skill_id} should not be wordpressSet.present")
        if "wp-plugin-development" in required_missing:
            failures.append("empty site: wp-plugin-development must not be required without a custom plugin")
        if "wp-block-themes" in required_missing:
            failures.append("empty site: wp-block-themes must not be required without theme.json")
        if "wp-phpstan" in required_missing:
            failures.append("empty site: wp-phpstan must not be required without first-party PHP")
        for skill_id in PACK_ALWAYS:
            if skill_id not in rec_missing:
                failures.append(f"empty site: {skill_id} should be recommended.missing")
        notes = empty.get("notes") or []
        if not any("Install WordPress skills set" in note for note in notes):
            failures.append("empty site: expected WordPress skills set vendor note")
        if not any("wordpress-testing is not vendored" in note for note in notes):
            failures.append("empty site: expected wordpress-testing vendor note")
        if not any("wordpress-component-creation is not vendored" in note for note in notes):
            failures.append("empty site: expected wordpress-component-creation vendor note")

        block_root = Path(tmp) / "block"
        block_root.mkdir()
        make_block_theme_site(block_root)
        block = audit(block_root)
        block_required = ids(block["skills"]["required"]["missing"])
        block_set = ids(block["skills"]["wordpressSet"]["missing"])
        if "wp-block-themes" not in block_required:
            failures.append("theme.json site: wp-block-themes should be required.missing")
        if "wp-block-themes" not in block_set:
            failures.append("theme.json site: wp-block-themes should stay in wordpressSet.missing")

        present_root = Path(tmp) / "present"
        present_root.mkdir()
        make_empty_site(present_root)
        write(
            present_root / ".agents" / "skills" / "wp-plugin-development" / "SKILL.md",
            "---\nname: wp-plugin-development\n---\n# Plugin\n",
        )
        present = audit(present_root)
        if "wp-plugin-development" not in ids(present["skills"]["wordpressSet"]["present"]):
            failures.append("vendored site: wp-plugin-development should be wordpressSet.present")
        if "wp-plugin-development" in ids(present["skills"]["wordpressSet"]["missing"]):
            failures.append("vendored site: wp-plugin-development should not be wordpressSet.missing")

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

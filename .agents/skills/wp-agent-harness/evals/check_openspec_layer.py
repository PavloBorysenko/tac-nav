#!/usr/bin/env python3
"""Check OpenSpec CLI, init, skills location, and pin audit detection."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


EVALS_DIR = Path(__file__).resolve().parent
SKILL_DIR = EVALS_DIR.parent
AUDIT = SKILL_DIR / "scripts" / "audit.mjs"
PROVISION = SKILL_DIR / "references" / "audit-provision.md"
OPENSPEC_REFERENCE = SKILL_DIR / "references" / "openspec.md"
SKILL_MD = SKILL_DIR / "SKILL.md"
INSTALL_SKILLS = SKILL_DIR / "scripts" / "install-skills.mjs"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_site(root: Path, *, config: str | None = None) -> None:
    write(root / "wp-load.php", "<?php\n")
    write(root / "wp-includes" / "version.php", "<?php\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "style.css", "/* Theme Name: ACME */\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "functions.php", "<?php\n")
    if config == "yaml":
        write(root / "openspec" / "config.yaml", "schema: spec-driven\n")
    elif config == "yml":
        write(root / "openspec" / "config.yml", "schema: spec-driven\n")
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


def write_openspec_skill(root: Path, *, pinned: bool) -> None:
    front = "---\nname: openspec-propose\n"
    if pinned:
        front += "disable-model-invocation: true\n"
    front += "description: Propose a change.\n---\n\n# Propose\n"
    write(root / ".cursor" / "skills" / "openspec-propose" / "SKILL.md", front)


def audit(root: Path) -> dict:
    proc = subprocess.run(
        ["node", str(AUDIT), "--root", str(root)],
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(proc.stdout)


def expect(
    case: str,
    report: dict,
    *,
    init_missing: bool,
    skills_missing: bool = False,
    pin_missing: bool = False,
) -> list[str]:
    failures: list[str] = []
    openspec = report.get("openspec") or {}
    missing = openspec.get("missing") or {}
    got_init = missing.get("init")
    if got_init is not init_missing:
        failures.append(f"{case}: missing.init expected {init_missing}, got {got_init}")
    if missing.get("cli") is not (not openspec.get("onPath")):
        failures.append(f"{case}: missing.cli must match not onPath")
    if openspec.get("initialized") is not (not init_missing):
        failures.append(
            f"{case}: initialized expected {not init_missing}, got {openspec.get('initialized')}"
        )
    if missing.get("skills") is not skills_missing:
        failures.append(f"{case}: missing.skills expected {skills_missing}, got {missing.get('skills')}")
    if missing.get("pin") is not pin_missing:
        failures.append(f"{case}: missing.pin expected {pin_missing}, got {missing.get('pin')}")

    notes = report.get("notes") or []
    init_note = any("Enable OpenSpec in this repo" in n for n in notes)
    skills_note = any("no official openspec-* skills" in n for n in notes)
    pin_note = any("not pinned" in n for n in notes)
    location_note = any("Cursor init uses .cursor/skills" in n for n in notes)
    cli_note = any("openspec CLI is not on PATH" in n for n in notes)
    policy_note = any("OpenSpec CLI is team-global on PATH" in n for n in notes)
    if not policy_note:
        failures.append(f"{case}: missing OpenSpec team-global policy note")
    if init_missing and not init_note:
        failures.append(f"{case}: missing OpenSpec enable/init note")
    if not init_missing and init_note:
        failures.append(f"{case}: unexpected OpenSpec enable/init note")
    if skills_missing and not skills_note:
        failures.append(f"{case}: missing OpenSpec skills-location note")
    if not skills_missing and skills_note:
        failures.append(f"{case}: unexpected OpenSpec skills-location note")
    if pin_missing and not pin_note:
        failures.append(f"{case}: missing OpenSpec pin note")
    if not pin_missing and pin_note:
        failures.append(f"{case}: unexpected OpenSpec pin note")
    if pin_missing and not location_note:
        failures.append(f"{case}: missing OpenSpec .cursor/skills location note")
    if missing.get("cli") and not cli_note:
        failures.append(f"{case}: missing OpenSpec CLI note")
    if not missing.get("cli") and cli_note:
        failures.append(f"{case}: unexpected OpenSpec CLI note")
    return failures


def main() -> int:
    failures: list[str] = []
    provision = PROVISION.read_text(encoding="utf-8")
    reference = OPENSPEC_REFERENCE.read_text(encoding="utf-8")
    skill = SKILL_MD.read_text(encoding="utf-8")
    install = INSTALL_SKILLS.read_text(encoding="utf-8")
    for needle in (
        "openspec.missing.cli",
        "openspec.missing.init",
        "openspec.missing.skills",
        "openspec.missing.pin",
        "Enable OpenSpec in this repo",
        "Pin official OpenSpec skills",
        "npm install -g @fission-ai/openspec@latest",
        "openspec init --tools cursor",
        "same turn",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing OpenSpec choice: {needle}")
    for needle in (
        "Node 20.19",
        "@fission-ai/openspec@latest",
        "openspec init --tools cursor",
        "theme or plugin",
        "install-skills.mjs",
        "disable-openspec-auto-invoke.mjs",
        "disable-model-invocation",
        "same turn",
        "openspec.missing.pin",
    ):
        if needle not in reference:
            failures.append(f"references/openspec.md missing policy: {needle}")
    for needle in (
        "references/openspec.md",
        "OpenSpec CLI is a **global team tool**",
        "Do not install PHPUnit, Jest, or OpenSpec as skills",
        "Do not create `openspec/changes/<id>/`",
        "/opsx:propose",
        "under `.agents/skills` or `.cursor/skills`",
        "`.cursor/skills`",
        "disable-openspec-auto-invoke.mjs",
        "includes the pin in the same turn",
        "openspec.missing.pin",
    ):
        if needle not in skill:
            failures.append(f"SKILL.md missing OpenSpec instruction: {needle}")
    if "disable-openspec-auto-invoke.mjs" not in provision:
        failures.append("audit-provision.md missing OpenSpec auto-invoke pin")
    pin_script = SKILL_DIR / "scripts" / "disable-openspec-auto-invoke.mjs"
    if '"openspec"' not in install and "'openspec'" not in install:
        failures.append("install-skills.mjs must refuse openspec as a skill")

    with tempfile.TemporaryDirectory(prefix="wp-harness-openspec-") as tmp:
        base = Path(tmp)
        cases = [
            ("A-missing", None, True, False, False),
            ("B-config-yaml", "yaml", False, True, False),
            ("C-config-yml", "yml", False, True, False),
        ]
        for name, config, init_missing, skills_missing, pin_missing in cases:
            root = base / name
            root.mkdir()
            make_site(root, config=config)
            report = audit(root)
            failures.extend(
                expect(
                    name,
                    report,
                    init_missing=init_missing,
                    skills_missing=skills_missing,
                    pin_missing=pin_missing,
                )
            )
            if not init_missing:
                expected_path = (
                    "openspec/config.yaml" if config == "yaml" else "openspec/config.yml"
                )
                got_path = (report.get("openspec") or {}).get("config", {}).get("path")
                if got_path != expected_path:
                    failures.append(f"{name}: config.path expected {expected_path}, got {got_path}")

        unpinned_root = base / "D-unpinned"
        unpinned_root.mkdir()
        make_site(unpinned_root, config="yaml")
        write_openspec_skill(unpinned_root, pinned=False)
        failures.extend(
            expect(
                "D-unpinned",
                audit(unpinned_root),
                init_missing=False,
                skills_missing=False,
                pin_missing=True,
            )
        )

        pinned_root = base / "E-pinned"
        pinned_root.mkdir()
        make_site(pinned_root, config="yaml")
        write_openspec_skill(pinned_root, pinned=True)
        failures.extend(
            expect(
                "E-pinned",
                audit(pinned_root),
                init_missing=False,
                skills_missing=False,
                pin_missing=False,
            )
        )

        blocked = subprocess.run(
            [
                "node",
                str(INSTALL_SKILLS),
                "--confirm",
                "--root",
                str(base),
                "--skills",
                "openspec",
            ],
            capture_output=True,
            text=True,
        )
        if blocked.returncode == 0:
            failures.append("install-skills.mjs must refuse --skills openspec")
        elif '"openspec"' not in (blocked.stderr or ""):
            failures.append("install-skills.mjs blocked output missing openspec")

        pin_root = base / "pin"
        pin_root.mkdir()
        skill_md = pin_root / ".cursor" / "skills" / "openspec-propose" / "SKILL.md"
        skill_md.parent.mkdir(parents=True)
        skill_md.write_text(
            "---\nname: openspec-propose\ndescription: Propose a change.\n---\n\n# Propose\n",
            encoding="utf-8",
        )
        other = pin_root / ".agents" / "skills" / "wp-agent-harness" / "SKILL.md"
        other.parent.mkdir(parents=True)
        other.write_text("---\nname: wp-agent-harness\n---\n", encoding="utf-8")
        refused = subprocess.run(
            ["node", str(pin_script), "--root", str(pin_root)],
            capture_output=True,
            text=True,
        )
        if refused.returncode != 2:
            failures.append("disable-openspec-auto-invoke.mjs must refuse without --confirm")
        pinned = subprocess.run(
            ["node", str(pin_script), "--confirm", "--root", str(pin_root)],
            capture_output=True,
            text=True,
        )
        if pinned.returncode != 0:
            failures.append(f"pin failed: {pinned.stderr or pinned.stdout}")
        else:
            text = skill_md.read_text(encoding="utf-8")
            if "disable-model-invocation: true" not in text.split("---")[1]:
                failures.append("pin did not add disable-model-invocation to openspec-propose")
            if "disable-model-invocation" in other.read_text(encoding="utf-8"):
                failures.append("pin must not edit non-openspec skills")
            again = subprocess.run(
                ["node", str(pin_script), "--confirm", "--root", str(pin_root)],
                capture_output=True,
                text=True,
            )
            if again.returncode != 0:
                failures.append(f"idempotent pin failed: {again.stderr or again.stdout}")
            elif text != skill_md.read_text(encoding="utf-8"):
                failures.append("second pin must be a no-op")

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

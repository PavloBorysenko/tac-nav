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
    write(root / ".claude" / "skills" / "openspec-propose" / "SKILL.md", front)


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
    task_rules_missing: bool = False,
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
    if missing.get("taskRules") is not task_rules_missing:
        failures.append(
            f"{case}: missing.taskRules expected {task_rules_missing}, got {missing.get('taskRules')}"
        )

    notes = report.get("notes") or []
    init_note = any("Enable OpenSpec in this repo" in n for n in notes)
    skills_note = any("no official openspec-* skills" in n for n in notes)
    pin_note = any("not pinned" in n for n in notes)
    task_rules_note = any("Write OpenSpec task rules" in n for n in notes)
    location_note = any("Claude Code init uses .claude/skills" in n for n in notes)
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
    if task_rules_missing and not task_rules_note:
        failures.append(f"{case}: missing OpenSpec task-rules note")
    if not task_rules_missing and task_rules_note:
        failures.append(f"{case}: unexpected OpenSpec task-rules note")
    if pin_missing and not location_note:
        failures.append(f"{case}: missing OpenSpec .claude/skills location note")
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
        "openspec.missing.taskRules",
        "Write OpenSpec task rules",
        "write-openspec-task-rules.mjs",
        "Enable OpenSpec in this repo",
        "Pin official OpenSpec skills",
        "npm install -g @fission-ai/openspec@latest",
        "openspec init --tools claude",
        "same turn",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing OpenSpec choice: {needle}")
    for needle in (
        "Node 20.19",
        "@fission-ai/openspec@latest",
        "openspec init --tools claude",
        "theme or plugin",
        "install-skills.mjs",
        "disable-openspec-auto-invoke.mjs",
        "disable-model-invocation",
        "same turn",
        "openspec.missing.pin",
        "openspec.missing.taskRules",
        "write-openspec-task-rules.mjs",
        "harness-task-rules:",
        "does not include this write",
    ):
        if needle not in reference:
            failures.append(f"references/openspec.md missing policy: {needle}")
    for needle in (
        "references/openspec.md",
        "OpenSpec CLI is a **global team tool**",
        "Do not install PHPUnit, Jest, or OpenSpec as skills",
        "Do not create `openspec/changes/<id>/`",
        "/opsx:propose",
        "under `.claude/skills`",
        "`.claude/skills`",
        "disable-openspec-auto-invoke.mjs",
        "write-openspec-task-rules.mjs",
        "Init, pin, and task rules stay in `references/openspec.md`",
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
            ("A-missing", None, True, False, False, False),
            ("B-config-yaml", "yaml", False, True, False, True),
            ("C-config-yml", "yml", False, True, False, True),
        ]
        for name, config, init_missing, skills_missing, pin_missing, task_rules_missing in cases:
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
                    task_rules_missing=task_rules_missing,
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
                task_rules_missing=True,
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
                task_rules_missing=True,
            )
        )

        ruled_root = base / "F-task-rules"
        ruled_root.mkdir()
        make_site(ruled_root, config="yaml")
        write(
            ruled_root / "openspec" / "config.yaml",
            'schema: spec-driven\nrules:\n  tasks:\n    - "harness-task-rules: already"\n',
        )
        failures.extend(
            expect(
                "F-task-rules",
                audit(ruled_root),
                init_missing=False,
                skills_missing=True,
                pin_missing=False,
                task_rules_missing=False,
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
        skill_md = pin_root / ".claude" / "skills" / "openspec-propose" / "SKILL.md"
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

        rules_script = SKILL_DIR / "scripts" / "write-openspec-task-rules.mjs"
        rules_root = base / "task-rules"
        rules_root.mkdir()
        config_path = rules_root / "openspec" / "config.yaml"
        config_path.parent.mkdir(parents=True)
        original = (
            "schema: spec-driven\n"
            "context: |\n"
            "  Keep this context.\n"
            "rules:\n"
            "  proposal:\n"
            "    - Keep proposals short\n"
        )
        config_path.write_text(original, encoding="utf-8")
        refused_rules = subprocess.run(
            ["node", str(rules_script), "--root", str(rules_root)],
            capture_output=True,
            text=True,
        )
        if refused_rules.returncode != 2:
            failures.append("write-openspec-task-rules.mjs must refuse without --confirm")
        elif config_path.read_text(encoding="utf-8") != original:
            failures.append("refused task-rules write must not edit config")
        written = subprocess.run(
            ["node", str(rules_script), "--confirm", "--root", str(rules_root)],
            capture_output=True,
            text=True,
        )
        if written.returncode != 0:
            failures.append(f"task-rules write failed: {written.stderr or written.stdout}")
        else:
            got = config_path.read_text(encoding="utf-8")
            if "Keep this context." not in got or "Keep proposals short" not in got:
                failures.append("task-rules write must preserve context and other rules")
            if got.count("harness-task-rules:") != 1:
                failures.append("task-rules write must add the marker once")
            if "schema: spec-driven" not in got:
                failures.append("task-rules write must keep schema")
            again_rules = subprocess.run(
                ["node", str(rules_script), "--confirm", "--root", str(rules_root)],
                capture_output=True,
                text=True,
            )
            if again_rules.returncode != 0:
                failures.append(f"idempotent task-rules write failed: {again_rules.stderr or again_rules.stdout}")
            elif got != config_path.read_text(encoding="utf-8"):
                failures.append("second task-rules write must be a no-op")
        missing_config = base / "no-config"
        missing_config.mkdir()
        missing = subprocess.run(
            ["node", str(rules_script), "--confirm", "--root", str(missing_config)],
            capture_output=True,
            text=True,
        )
        if missing.returncode == 0:
            failures.append("task-rules write must refuse when OpenSpec config is missing")
        elif (missing_config / "openspec").exists():
            failures.append("task-rules write must not create openspec/")

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

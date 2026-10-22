#!/usr/bin/env python3
"""Check PHPUnit, Jest, and WP_UnitTestCase audit detection and notes."""

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
TESTS_REFERENCE = SKILL_DIR / "references" / "tests.md"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_site(
    root: Path,
    *,
    phpunit_root: bool = False,
    phpunit_component: bool = False,
    phpunit_runner: bool = False,
    wp_unit_test_case: bool = False,
    js: bool = False,
    jest_tools: bool = False,
) -> None:
    write(root / "wp-load.php", "<?php\n")
    write(root / "wp-includes" / "version.php", "<?php\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "style.css", "/* Theme Name: ACME */\n")
    write(root / "wp-content" / "themes" / "acme-theme" / "functions.php", "<?php\n")
    write(
        root / "wp-content" / "plugins" / "acme-hours" / "acme-hours.php",
        "<?php\n/** Plugin Name: ACME Hours */\n",
    )
    if phpunit_root:
        write(root / "phpunit.xml.dist", '<?xml version="1.0"?><phpunit></phpunit>\n')
    if phpunit_component:
        write(
            root / "wp-content" / "plugins" / "acme-hours" / "phpunit.xml.dist",
            '<?xml version="1.0"?><phpunit></phpunit>\n',
        )
    if phpunit_runner:
        write(root / "tools" / "phpunit" / "vendor" / "bin" / "phpunit", "")
        write(root / "tools" / "phpunit" / "vendor" / "bin" / "phpunit.bat", "")
    if wp_unit_test_case:
        write(
            root / "tools" / "phpunit" / "composer.json",
            '{"require-dev":{"phpunit/phpunit":"*","wp-phpunit/wp-phpunit":"*","yoast/phpunit-polyfills":"*"}}\n',
        )
        write(
            root / "tests" / "bootstrap.php",
            "<?php\nrequire 'vendor/wp-phpunit/wp-phpunit/includes/bootstrap.php';\n",
        )
        write(
            root / "phpunit.xml.dist",
            '<?xml version="1.0"?><phpunit bootstrap="tests/bootstrap.php"></phpunit>\n',
        )
    if js:
        write(
            root / "wp-content" / "themes" / "acme-theme" / "js" / "hours.js",
            "export function pad(n) { return n; }\n",
        )
    if jest_tools:
        write(root / "tools" / "js-test" / "package.json", '{"name":"js-test"}\n')
        write(root / "tools" / "js-test" / "jest.config.cjs", "module.exports = {};\n")

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


def expect(
    case: str,
    report: dict,
    *,
    phpunit: bool,
    jest: bool,
    wp_unit_test_case: bool,
    jest_present: bool | None = None,
) -> list[str]:
    failures: list[str] = []
    got_phpunit = report["qa"]["missing"]["phpunit"]
    got_jest = report["qa"]["missing"]["jest"]
    got_wp_unit = report["qa"]["missing"]["wpUnitTestCase"]
    if got_phpunit is not phpunit:
        failures.append(f"{case}: missing.phpunit expected {phpunit}, got {got_phpunit}")
    if got_jest is not jest:
        failures.append(f"{case}: missing.jest expected {jest}, got {got_jest}")
    if got_wp_unit is not wp_unit_test_case:
        failures.append(
            f"{case}: missing.wpUnitTestCase expected {wp_unit_test_case}, got {got_wp_unit}"
        )
    if jest_present is not None and report["qa"]["jest"]["present"] is not jest_present:
        failures.append(
            f"{case}: jest.present expected {jest_present}, got {report['qa']['jest']['present']}"
        )

    notes = report.get("notes") or []
    phpunit_note = any("No runnable PHPUnit suite" in n for n in notes)
    jest_note = any("No Jest toolbox" in n for n in notes)
    wp_unit_note = any("No WordPress integration test bootstrap" in n for n in notes)
    if phpunit and not phpunit_note:
        failures.append(f"{case}: missing PHPUnit toolbox note")
    if not phpunit and phpunit_note:
        failures.append(f"{case}: unexpected PHPUnit toolbox note")
    if jest and not jest_note:
        failures.append(f"{case}: missing Jest toolbox note")
    if not jest and jest_note:
        failures.append(f"{case}: unexpected Jest toolbox note")
    if wp_unit_test_case and not wp_unit_note:
        failures.append(f"{case}: missing WP_UnitTestCase layer note")
    if not wp_unit_test_case and wp_unit_note:
        failures.append(f"{case}: unexpected WP_UnitTestCase layer note")
    return failures


def main() -> int:
    failures: list[str] = []
    provision = PROVISION.read_text(encoding="utf-8")
    tests_reference = TESTS_REFERENCE.read_text(encoding="utf-8")
    for needle in (
        "qa.missing.phpunit",
        "qa.missing.jest",
        "qa.missing.wpUnitTestCase",
        "dedicated disposable test database",
    ):
        if needle not in provision:
            failures.append(f"audit-provision.md missing test-layer choice: {needle}")
    for needle in (
        "wp-phpunit/wp-phpunit",
        "yoast/phpunit-polyfills",
        "WP_PHPUNIT__TESTS_CONFIG",
        "development or production database",
        "focused new or failing test",
        "php-tool-constraint.mjs",
        "platform.php",
        "unpinned",
    ):
        if needle not in tests_reference:
            failures.append(f"references/tests.md missing integration policy: {needle}")

    constraint_script = SKILL_DIR / "scripts" / "phpunit-constraint.mjs"
    tool_script = SKILL_DIR / "scripts" / "php-tool-constraint.mjs"
    if not constraint_script.is_file():
        failures.append("missing scripts/phpunit-constraint.mjs")
    if not tool_script.is_file():
        failures.append("missing scripts/php-tool-constraint.mjs")
    phpcs_md = (SKILL_DIR / "references" / "phpcs.md").read_text(encoding="utf-8")
    phpstan_md = (SKILL_DIR / "references" / "phpstan.md").read_text(encoding="utf-8")
    install_phpcs = (SKILL_DIR / "scripts" / "install-phpcs.mjs").read_text(encoding="utf-8")
    for needle, hay, label in (
        ("php-tool-constraint.mjs", phpcs_md, "phpcs.md"),
        ("not PHPCS 4", phpcs_md, "phpcs.md"),
        ("php-tool-constraint.mjs", phpstan_md, "phpstan.md"),
        ("unpinned", phpstan_md, "phpstan.md"),
        ("php-tool-constraint.mjs", install_phpcs, "install-phpcs.mjs"),
        ("platform.php", install_phpcs, "install-phpcs.mjs"),
    ):
        if needle not in hay:
            failures.append(f"{label} missing {needle}")
    if tool_script.is_file():
        proc = subprocess.run(
            ["node", str(tool_script), "--php-version", "8.1.29"],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0:
            failures.append(f"php-tool-constraint 8.1.29 exited {proc.returncode}")
        else:
            payload = json.loads(proc.stdout)
            if payload.get("phpstan") != "^2.0":
                failures.append(f"php-tool-constraint 8.1 phpstan {payload.get('phpstan')}")
            if payload.get("phpcs") != "^3.13.5":
                failures.append(f"php-tool-constraint 8.1 phpcs {payload.get('phpcs')}")
            if payload.get("wpcs") != "^3.0":
                failures.append(f"php-tool-constraint 8.1 wpcs {payload.get('wpcs')}")
            if "phpcsstandards/php_codesniffer:^4" in json.dumps(payload):
                failures.append("php-tool-constraint must not pin PHPCS 4")
        old = subprocess.run(
            ["node", str(tool_script), "--php-version", "7.2.0"],
            capture_output=True,
            text=True,
        )
        if old.returncode != 0:
            failures.append(f"php-tool-constraint 7.2.0 exited {old.returncode}")
        else:
            old_payload = json.loads(old.stdout)
            if old_payload.get("phpstan") != "^1.12":
                failures.append(f"php-tool-constraint 7.2 phpstan {old_payload.get('phpstan')}")
            if old_payload.get("phpunit") is not None:
                failures.append("php-tool-constraint 7.2 must omit PHPUnit")

    if constraint_script.is_file():
        cases = [
            ("8.1.29", "^10", 0),
            ("8.2.4", "^11", 0),
            ("8.3.0", "^12", 0),
            ("8.4.1", "^13", 0),
            ("8.5.0", "^13", 0),
            ("7.4.33", "^9.6", 0),
            ("7.2.0", None, 2),
        ]
        for php_version, expected, code in cases:
            proc = subprocess.run(
                ["node", str(constraint_script), "--php-version", php_version],
                capture_output=True,
                text=True,
            )
            if proc.returncode != code:
                failures.append(
                    f"phpunit-constraint {php_version}: exit {proc.returncode}, expected {code}"
                )
                continue
            if code != 0:
                continue
            payload = json.loads(proc.stdout)
            if payload.get("phpunit") != expected:
                failures.append(
                    f"phpunit-constraint {php_version}: phpunit {payload.get('phpunit')}, expected {expected}"
                )
            if payload.get("require") != f"phpunit/phpunit:{expected}":
                failures.append(f"phpunit-constraint {php_version}: require {payload.get('require')}")
            if "platform" not in payload:
                failures.append(f"phpunit-constraint {php_version}: missing platform")


    with tempfile.TemporaryDirectory(prefix="wp-harness-tests-") as tmp:
        base = Path(tmp)
        cases = [
            (
                "A-baseline",
                {},
                {"phpunit": True, "jest": False, "wp_unit_test_case": True},
            ),
            (
                "B-root-config-only",
                {"phpunit_root": True},
                {"phpunit": True, "jest": False, "wp_unit_test_case": True},
            ),
            (
                "C-root-phpunit",
                {"phpunit_root": True, "phpunit_runner": True},
                {"phpunit": False, "jest": False, "wp_unit_test_case": True},
            ),
            (
                "D-js-no-jest",
                {"js": True},
                {"phpunit": True, "jest": True, "wp_unit_test_case": True},
            ),
            (
                "E-jest-tools",
                {"js": True, "jest_tools": True},
                {
                    "phpunit": True,
                    "jest": False,
                    "wp_unit_test_case": True,
                    "jest_present": True,
                },
            ),
            (
                "F-component-config-only",
                {"phpunit_component": True},
                {"phpunit": True, "jest": False, "wp_unit_test_case": True},
            ),
            (
                "G-wordpress-integration",
                {
                    "phpunit_root": True,
                    "phpunit_runner": True,
                    "wp_unit_test_case": True,
                },
                {"phpunit": False, "jest": False, "wp_unit_test_case": False},
            ),
        ]
        for name, site_kwargs, expected in cases:
            root = base / name
            root.mkdir()
            make_site(root, **site_kwargs)
            report = audit(root)
            failures.extend(expect(name, report, **expected))

    print(json.dumps({"ok": not failures, "failures": failures}, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

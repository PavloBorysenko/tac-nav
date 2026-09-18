"""install-skills.mjs vendors supernova-pack skills without evals; refuses tests-as-skills."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

HARNESS_DIR = Path(__file__).resolve().parents[1]
INSTALL = HARNESS_DIR / "scripts" / "install-skills.mjs"
AUTHORING_EVALS = HARNESS_DIR / "evals"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def run_install(root: Path, skills: str, confirm: bool = True) -> subprocess.CompletedProcess[str]:
    cmd = ["node", str(INSTALL), "--root", str(root), "--skills", skills]
    if confirm:
        cmd.append("--confirm")
    return subprocess.run(cmd, check=False, capture_output=True, text=True)


def assert_runtime_copy(dest: Path) -> None:
    if not (dest / "SKILL.md").is_file():
        fail(f"missing {dest / 'SKILL.md'}")
    if (dest / "evals").exists():
        fail(f"vendored copy must omit evals: {dest / 'evals'}")


def main() -> None:
    if not AUTHORING_EVALS.is_dir():
        fail(f"authoring evals missing: {AUTHORING_EVALS}")

    no_confirm = run_install(Path("."), "wordpress-project-documentation", confirm=False)
    if no_confirm.returncode != 2:
        fail(f"expected exit 2 without --confirm, got {no_confirm.returncode}")

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        tests_as_skills = run_install(root, "phpunit,jest")
        if tests_as_skills.returncode == 0:
            fail("phpunit/jest must not install as skills")
        err = tests_as_skills.stderr
        start = err.find("{")
        end = err.rfind("}") + 1
        if start < 0 or end <= start:
            fail(f"expected blocked JSON on stderr, got {err!r}")
        blocked = json.loads(err[start:end])
        ids = {row["id"] for row in blocked.get("blocked") or []}
        if ids != {"phpunit", "jest"}:
            fail(f"expected phpunit and jest blocked, got {blocked}")

        pack = run_install(root, "wordpress-project-documentation,wp-browser-sensor,local-code-review,wordpress-testing")
        if pack.returncode != 0:
            fail(f"pack install failed: {pack.stderr or pack.stdout}")
        for skill_id in (
            "wordpress-project-documentation",
            "wp-browser-sensor",
            "local-code-review",
            "wordpress-testing",
        ):
            dest = root / ".agents" / "skills" / skill_id
            assert_runtime_copy(dest)
        if (root / ".agents" / "skills" / "wordpress-project-documentation" / "assets").is_dir() is False:
            fail("docs vendor copy missing assets/")
        if not (root / ".agents" / "skills" / "wordpress-testing" / "references" / "phpunit.md").is_file():
            fail("testing vendor copy missing references/phpunit.md")
        if not (root / ".agents" / "skills" / "wordpress-testing" / "references" / "openspec.md").is_file():
            fail("testing vendor copy missing references/openspec.md")

        harness_only = root / "harness-only"
        harness_only.mkdir()
        trio = run_install(harness_only, "wp-agent-harness,wp-browser-sensor,local-code-review")
        if trio.returncode != 0:
            fail(f"harness trio install failed: {trio.stderr or trio.stdout}")
        if (harness_only / ".agents" / "skills" / "wordpress-testing").exists():
            fail("installing the harness trio must not vendor wordpress-testing")

    if not AUTHORING_EVALS.is_dir():
        fail("authoring evals were removed")

    print("install-skills vendors pack skills without evals; refuses PHPUnit/Jest")


if __name__ == "__main__":
    main()

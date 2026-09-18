"""Vendor copy omits skill evals. Authoring copies keep them."""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path

HARNESS_DIR = Path(__file__).resolve().parents[1]
SCRIPTS = HARNESS_DIR / "scripts" / "copy-harness.mjs"
DOCS_COPY = (
    HARNESS_DIR.parent
    / "wordpress-project-documentation"
    / "scripts"
    / "copy-wordpress-project-documentation.mjs"
)
TESTING_COPY = (
    HARNESS_DIR.parent / "wordpress-testing" / "scripts" / "copy-wordpress-testing.mjs"
)
COMPONENT_COPY = (
    HARNESS_DIR.parent
    / "wordpress-component-creation"
    / "scripts"
    / "copy-wordpress-component-creation.mjs"
)
PROJECT_COPY = (
    HARNESS_DIR.parent
    / "wordpress-project-creation"
    / "scripts"
    / "copy-wordpress-project-creation.mjs"
)
AUTHORING_EVALS = HARNESS_DIR / "evals"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def copied_skills(root: Path) -> list[Path]:
    skills = root / ".agents" / "skills"
    if not skills.is_dir():
        fail(f"missing vendored skills dir: {skills}")
    return sorted(p for p in skills.iterdir() if p.is_dir())


def assert_runtime_copy(dest: Path) -> None:
    if not (dest / "SKILL.md").is_file():
        fail(f"missing {dest / 'SKILL.md'}")
    evals = dest / "evals"
    if evals.exists():
        fail(f"vendored copy must omit evals: {evals}")


def main() -> None:
    if not AUTHORING_EVALS.is_dir():
        fail(f"authoring evals missing: {AUTHORING_EVALS}")

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        result = subprocess.run(
            ["node", str(SCRIPTS), "--confirm", "--root", str(root)],
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            fail(f"copy-harness failed: {result.stderr or result.stdout}")

        names = {p.name for p in copied_skills(root)}
        for required in ("wp-agent-harness", "wp-browser-sensor", "local-code-review"):
            if required not in names:
                fail(f"copy-harness did not vendor {required}: {sorted(names)}")
        if "wordpress-testing" in names:
            fail("copy-harness must not vendor wordpress-testing")
        if "wordpress-component-creation" in names:
            fail("copy-harness must not vendor wordpress-component-creation")
        if "wordpress-project-creation" in names:
            fail("copy-harness must not vendor wordpress-project-creation")
        for dest in copied_skills(root):
            assert_runtime_copy(dest)

        docs_root = root / "docs-site"
        docs_result = subprocess.run(
            ["node", str(DOCS_COPY), "--confirm", "--root", str(docs_root)],
            check=False,
            capture_output=True,
            text=True,
        )
        if docs_result.returncode != 0:
            fail(f"docs copy failed: {docs_result.stderr or docs_result.stdout}")
        docs_dest = docs_root / ".agents" / "skills" / "wordpress-project-documentation"
        assert_runtime_copy(docs_dest)
        if not (docs_dest / "assets").is_dir():
            fail("docs vendor copy missing assets/")

        testing_root = root / "testing-site"
        testing_result = subprocess.run(
            ["node", str(TESTING_COPY), "--confirm", "--root", str(testing_root)],
            check=False,
            capture_output=True,
            text=True,
        )
        if testing_result.returncode != 0:
            fail(f"testing copy failed: {testing_result.stderr or testing_result.stdout}")
        testing_dest = testing_root / ".agents" / "skills" / "wordpress-testing"
        assert_runtime_copy(testing_dest)
        if not (testing_dest / "references" / "phpunit.md").is_file():
            fail("testing vendor copy missing references/phpunit.md")
        if not (testing_dest / "references" / "openspec.md").is_file():
            fail("testing vendor copy missing references/openspec.md")
        if (testing_dest / "evals").exists():
            fail("testing vendor copy must omit evals")

        component_root = root / "component-site"
        component_result = subprocess.run(
            ["node", str(COMPONENT_COPY), "--confirm", "--root", str(component_root)],
            check=False,
            capture_output=True,
            text=True,
        )
        if component_result.returncode != 0:
            fail(f"component-creation copy failed: {component_result.stderr or component_result.stdout}")
        component_dest = component_root / ".agents" / "skills" / "wordpress-component-creation"
        assert_runtime_copy(component_dest)
        if not (component_dest / "references" / "plugin.md").is_file():
            fail("component-creation vendor copy missing references/plugin.md")
        if (component_dest / "evals").exists():
            fail("component-creation vendor copy must omit evals")

        project_root = root / "project-site"
        project_result = subprocess.run(
            ["node", str(PROJECT_COPY), "--confirm", "--root", str(project_root)],
            check=False,
            capture_output=True,
            text=True,
        )
        if project_result.returncode != 0:
            fail(f"project-creation copy failed: {project_result.stderr or project_result.stdout}")
        project_dest = project_root / ".agents" / "skills" / "wordpress-project-creation"
        assert_runtime_copy(project_dest)
        if not (project_dest / "assets" / "gitlab-ci-two-env.yml").is_file():
            fail("project-creation vendor copy missing assets/gitlab-ci-two-env.yml")
        if not (project_dest / "references" / "sync.md").is_file():
            fail("project-creation vendor copy missing references/sync.md")
        if (project_dest / "evals").exists():
            fail("project-creation vendor copy must omit evals")

    if not AUTHORING_EVALS.is_dir():
        fail("authoring evals were removed")

    print("vendor copy omits evals; authoring evals remain")


if __name__ == "__main__":
    main()

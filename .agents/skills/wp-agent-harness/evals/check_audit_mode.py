#!/usr/bin/env python3
"""Check harness audit-mode budget: SKILL.md fork, audit.mjs runs, and trace reads."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


EVALS_DIR = Path(__file__).resolve().parent
SKILL_DIR = EVALS_DIR.parent
EVALS_JSON = EVALS_DIR / "evals.json"
TRACES_DIR = EVALS_DIR / "traces"
MAX_SKILL_TOKENS = 5500

REQUIRED_IN_SKILL = (
    "### Develop mode",
    "### Audit mode",
    "Do not run `audit.mjs`",
    "audit the harness, check the harness, set up the harness",
    "Offer the documentation skill only in **audit** mode",
    "WordPress skills set",
    "wordpress-component-creation",
    "Load only what the mode needs",
    "references/audit-provision.md",
    "Develop uses this `SKILL.md` only",
    "wp-browser-sensor",
    "local-code-review",
    "Do not inline that checklist here",
    "Do not inline the review checklist",
    "Skip it when the user asked only for a review",
    "If that file is missing, skip the review step",
    "launch one isolated readonly subagent",
    "Do not review the diff yourself",
    "Do not pass author justifications",
    "Do not launch a second reviewer",
    "Read `.agents/skills/wordpress-testing/SKILL.md`",
    "new/changed behavior meets the test-worthy rule",
    "Before the final gate run",
    "named test-tooling install",
    "Do not create `openspec/changes/<id>/`",
    "/opsx:propose",
        "under `.agents/skills` or `.cursor/skills`",
        "disable-openspec-auto-invoke.mjs",
    "includes the pin in the same turn",
    "qa.emptyFirstParty",
    "qa.offerOnIntent",
    "Do not offer Not now",
    "new **non-tiny** first-party theme or plugin",
)
FORBIDDEN_IN_SKILL = (
    "3. **Audit mode only**",
    "Install all missing required",
    "Questions before any install",
)

SKILL_PATH_RE = re.compile(
    r"(?:(?:\.agents/skills/|result/)?wp-agent-harness/)?(?P<rel>"
    r"(?:SKILL\.md|references/[\w.-]+\.(?:md|json)|assets/rules/[\w.-]+\.mdc))"
)
NEGATED_READ_RE = re.compile(
    r"(?i)(not opened|did not open|did not read|must not read|do not (?:open|read))"
)
AUDIT_RUN_RE = re.compile(r"node\s+\S*audit\.mjs")
ISOLATED_LAUNCH_RE = re.compile(
    r"(?i)launch(?:ed)? one isolated(?: readonly)? subagent"
)
REVIEW_SCOPE_RE = re.compile(r"review-scope\.mjs")
TEST_FILE_RE = re.compile(r"(?i)(?:wp-content/\S+/)?tests/\S+\.(?:php|js|jsx|ts|tsx)")
TEST_RUN_RE = re.compile(r"(?i)(?:\bphpunit\b|\bjest\b|npm\s+(?:--prefix\s+\S+\s+)?(?:run\s+)?test\b)")
OTHER_SKILL_RE = re.compile(
    r"(?:\.agents/skills/|\.cursor/skills/|result/)(?P<rel>(?:wp-browser-sensor|local-code-review|wordpress-testing|openspec-[\w-]+)/SKILL\.md)"
)
DESCRIPTION_RE = re.compile(
    r"^description:\s*(?:>-\s*)?(?P<body>(?:(?:  .*)?\n)+)",
    re.MULTILINE,
)


def estimate_tokens(text: str) -> int:
    return max(len(text) // 4, int(len(text.split()) * 1.3))


def load_evals() -> dict:
    return json.loads(EVALS_JSON.read_text(encoding="utf-8"))


def skill_relpaths(data: dict) -> list[str]:
    files = ["SKILL.md"]
    files.extend(data["skill_files"]["references"])
    files.extend(data["skill_files"]["assets"])
    return files


def extract_skill_reads(trace_text: str) -> set[str]:
    found: set[str] = set()
    for line in trace_text.replace("\\", "/").splitlines():
        if NEGATED_READ_RE.search(line):
            continue
        for match in SKILL_PATH_RE.finditer(line):
            found.add(match.group("rel"))
    return found


def extract_other_skill_reads(trace_text: str) -> set[str]:
    found: set[str] = set()
    for line in trace_text.replace("\\", "/").splitlines():
        if NEGATED_READ_RE.search(line):
            continue
        for match in OTHER_SKILL_RE.finditer(line):
            found.add(match.group("rel"))
    return found


def ran_audit(trace_text: str) -> bool:
    return bool(AUDIT_RUN_RE.search(trace_text.replace("\\", "/")))


def description_text(skill_text: str) -> str:
    match = DESCRIPTION_RE.search(skill_text)
    if not match:
        return ""
    lines = [line.strip() for line in match.group("body").splitlines() if line.strip()]
    return " ".join(lines)


def grade_trace(eval_spec: dict, trace_text: str) -> list[str]:
    failures: list[str] = []
    reads = extract_skill_reads(trace_text)
    budget = eval_spec.get("skill_reads", {})
    for path in budget.get("must_read", []):
        if path not in reads:
            failures.append(f"missing required read: {path}")
    for path in budget.get("must_not_read", []):
        if path in reads:
            failures.append(f"unexpected read: {path}")

    other = extract_other_skill_reads(trace_text)
    other_budget = eval_spec.get("other_skill_reads", {})
    for path in other_budget.get("must_read", []):
        if path not in other:
            failures.append(f"missing required other-skill read: {path}")
    for path in other_budget.get("must_not_read", []):
        if path in other:
            failures.append(f"unexpected other-skill read: {path}")

    ran = ran_audit(trace_text)
    if eval_spec.get("must_run") and not ran:
        failures.append("missing required run: scripts/audit.mjs")
    if eval_spec.get("must_not_run") and ran:
        failures.append("unexpected run: scripts/audit.mjs")

    if eval_spec.get("isolated_review"):
        if not ISOLATED_LAUNCH_RE.search(trace_text):
            failures.append("missing isolated subagent launch")
        for line in trace_text.replace("\\", "/").splitlines():
            if NEGATED_READ_RE.search(line):
                continue
            if REVIEW_SCOPE_RE.search(line):
                failures.append("parent must not run review-scope.mjs")
                break

    active_lines = [
        line for line in trace_text.replace("\\", "/").splitlines()
        if not NEGATED_READ_RE.search(line)
    ]
    active_text = "\n".join(active_lines)
    if eval_spec.get("writes_tests") and not TEST_FILE_RE.search(active_text):
        failures.append("missing first-party test file")
    if eval_spec.get("runs_tests") and not TEST_RUN_RE.search(active_text):
        failures.append("missing PHPUnit/Jest run")
    return failures


def check_skill_structure(data: dict) -> list[str]:
    failures: list[str] = []
    skill_text = (SKILL_DIR / "SKILL.md").read_text(encoding="utf-8")
    tokens = estimate_tokens(skill_text)
    if tokens > MAX_SKILL_TOKENS:
        failures.append(f"SKILL.md is about {tokens} tokens; keep it at or below {MAX_SKILL_TOKENS}")

    for needle in REQUIRED_IN_SKILL:
        if needle not in skill_text:
            failures.append(f"SKILL.md is missing required instruction: {needle}")

    for needle in FORBIDDEN_IN_SKILL:
        if needle in skill_text:
            failures.append(f"SKILL.md still uses a sequential audit default: {needle}")

    desc = description_text(skill_text)
    if not desc:
        failures.append("SKILL.md description is missing or unreadable")
    elif desc.startswith("Audits"):
        failures.append("description must not lead with Audits; develop gates come first")
    if len(desc) > 1024:
        failures.append(f"description is {len(desc)} characters; keep it at or below 1024")

    develop_at = skill_text.find("### Develop mode")
    audit_at = skill_text.find("### Audit mode")
    if develop_at == -1 or audit_at == -1:
        failures.append("SKILL.md must fork ### Develop mode and ### Audit mode")
    elif develop_at > audit_at:
        failures.append("SKILL.md must place ### Develop mode before ### Audit mode")

    for rel in skill_relpaths(data):
        if not (SKILL_DIR / rel).is_file():
            failures.append(f"missing skill file: {rel}")

    for eval_spec in data["evals"]:
        budget = eval_spec.get("skill_reads")
        if not budget:
            failures.append(f"eval {eval_spec['id']} is missing skill_reads")
            continue
        for key in ("must_read", "must_not_read"):
            for rel in budget.get(key, []):
                if not (SKILL_DIR / rel).is_file():
                    failures.append(f"eval {eval_spec['id']} {key} points at missing file: {rel}")
                if rel == "SKILL.md":
                    failures.append(f"eval {eval_spec['id']} should not list SKILL.md in {key}")
        overlap = set(budget.get("must_read", [])) & set(budget.get("must_not_read", []))
        if overlap:
            failures.append(f"eval {eval_spec['id']} skill_reads overlap: {sorted(overlap)}")
        if eval_spec.get("must_run") and eval_spec.get("must_not_run"):
            if set(eval_spec["must_run"]) & set(eval_spec["must_not_run"]):
                failures.append(f"eval {eval_spec['id']} run lists overlap")

    return failures


def self_test(data: dict) -> list[str]:
    cases = [
        (1, "eval-1-pass.md", True),
        (1, "eval-1-fail.md", False),
        (2, "eval-2-pass.md", True),
        (2, "eval-2-fail.md", False),
        (3, "eval-3-pass.md", True),
        (3, "eval-3-negated-pass.md", True),
        (3, "eval-3-fail.md", False),
        (4, "eval-4-pass.md", True),
        (4, "eval-4-fail.md", False),
        (5, "eval-5-pass.md", True),
        (5, "eval-5-fail.md", False),
        (5, "eval-5-self-review-fail.md", False),
        (6, "eval-6-pass.md", True),
        (6, "eval-6-fail.md", False),
        (6, "eval-6-self-review-fail.md", False),
        (7, "eval-7-pass.md", True),
        (7, "eval-7-fail.md", False),
        (8, "eval-8-pass.md", True),
        (8, "eval-8-fail.md", False),
        (9, "eval-9-pass.md", True),
        (9, "eval-9-fail.md", False),
        (10, "eval-10-pass.md", True),
        (10, "eval-10-fail.md", False),
        (11, "eval-11-pass.md", True),
        (11, "eval-11-fail.md", False),
        (12, "eval-12-pass.md", True),
        (12, "eval-12-fail.md", False),
        (13, "eval-13-pass.md", True),
        (13, "eval-13-fail.md", False),
    ]
    by_id = {item["id"]: item for item in data["evals"]}
    failures: list[str] = []
    for eval_id, filename, should_pass in cases:
        path = TRACES_DIR / filename
        if not path.is_file():
            failures.append(f"missing audit-mode fixture: {filename}")
            continue
        grade_failures = grade_trace(by_id[eval_id], path.read_text(encoding="utf-8"))
        passed = not grade_failures
        if passed != should_pass:
            detail = "; ".join(grade_failures) or "no audit-mode failures"
            failures.append(
                f"{filename} for eval {eval_id}: expected {'pass' if should_pass else 'fail'}, got {detail}"
            )
    return failures


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate harness audit-mode budget and optional agent traces."
    )
    parser.add_argument("--eval", type=int, help="Eval id to grade against a trace.")
    parser.add_argument("--trace", type=Path, help="TRACE.md or other trace text.")
    parser.add_argument(
        "--skip-self-test",
        action="store_true",
        help="Skip bundled audit-mode fixtures.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    data = load_evals()
    failures = check_skill_structure(data)
    if not args.skip_self_test and args.trace is None:
        failures.extend(self_test(data))

    if args.trace is not None:
        if args.eval is None:
            print("--eval is required with --trace", file=sys.stderr)
            return 2
        eval_spec = next((item for item in data["evals"] if item["id"] == args.eval), None)
        if eval_spec is None:
            print(f"Unknown eval id: {args.eval}", file=sys.stderr)
            return 2
        if not args.trace.is_file():
            print(f"Trace not found: {args.trace}", file=sys.stderr)
            return 2
        failures.extend(grade_trace(eval_spec, args.trace.read_text(encoding="utf-8")))

    skill_text = (SKILL_DIR / "SKILL.md").read_text(encoding="utf-8")
    print(
        json.dumps(
            {
                "skill_md_chars": len(skill_text),
                "skill_md_lines": skill_text.count("\n") + 1,
                "skill_md_tokens_estimate": estimate_tokens(skill_text),
                "ok": not failures,
                "failures": failures,
            },
            indent=2,
        )
    )
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

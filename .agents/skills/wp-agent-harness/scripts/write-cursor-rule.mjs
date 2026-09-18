#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(__dirname, "..");
const ALLOWED = new Set(["qa-loop", "first-party-scope"]);
const QA_MODES = new Set(["task", "every-change", "manual"]);

function parseArgs(argv) {
  const args = { confirm: false, root: process.cwd(), id: "", mode: "task" };
  for (let i = 2; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === "--confirm") args.confirm = true;
    else if (a === "--root" && argv[i + 1]) args.root = argv[++i];
    else if (a === "--id" && argv[i + 1]) args.id = argv[++i];
    else if (a === "--mode" && argv[i + 1]) args.mode = argv[++i];
  }
  return args;
}

function templatePath(id, mode) {
  const dir = path.join(SKILL_ROOT, "assets", "rules");
  if (id === "qa-loop") return path.join(dir, `qa-loop.${mode}.mdc`);
  return path.join(dir, `${id}.mdc`);
}

function targetName(id) {
  if (id === "qa-loop") return "wp-agent-harness-qa.mdc";
  if (id === "first-party-scope") return "wp-agent-harness-scope.mdc";
  return `${id}.mdc`;
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to write a Cursor rule: pass --confirm after the user approved the exact rule.\n");
    process.exit(2);
  }
  if (!ALLOWED.has(args.id)) {
    process.stderr.write(`Refusing unknown --id ${args.id}. Allowed: ${[...ALLOWED].join(", ")}\n`);
    process.exit(2);
  }
  if (args.id === "qa-loop" && !QA_MODES.has(args.mode)) {
    process.stderr.write(`Refusing unknown --mode ${args.mode}. Allowed: ${[...QA_MODES].join(", ")}\n`);
    process.exit(2);
  }

  const src = templatePath(args.id, args.mode);
  if (!fs.existsSync(src)) {
    process.stderr.write(`Missing template ${src}\n`);
    process.exit(2);
  }

  const rulesDir = path.join(path.resolve(args.root), ".cursor", "rules");
  fs.mkdirSync(rulesDir, { recursive: true });
  const target = path.join(rulesDir, targetName(args.id));
  fs.writeFileSync(target, fs.readFileSync(src, "utf8"));
  process.stdout.write(`Wrote ${target}\n`);
}

main();

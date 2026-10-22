#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(__dirname, "..");

function parseArgs(argv) {
  const args = { confirm: false, root: process.cwd() };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--confirm") args.confirm = true;
    else if (argv[i] === "--root" && argv[i + 1]) args.root = argv[++i];
  }
  return args;
}

const args = parseArgs(process.argv);
if (!args.confirm) {
  process.stderr.write("Refusing to write the code-reviewer agent: pass --confirm after the user approved it.\n");
  process.exit(2);
}
const src = path.join(SKILL_ROOT, "assets", "agents", "code-reviewer.md");
const dir = path.join(path.resolve(args.root), ".claude", "agents");
fs.mkdirSync(dir, { recursive: true });
const target = path.join(dir, "code-reviewer.md");
fs.writeFileSync(target, fs.readFileSync(src, "utf8"));
process.stdout.write(`Wrote ${target}\n`);

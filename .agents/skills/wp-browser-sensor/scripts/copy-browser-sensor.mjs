#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(__dirname, "..");
const SKIP = new Set([".git", "node_modules", "evals"]);

function parseArgs(argv) {
  const args = { confirm: false, root: process.cwd() };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--confirm") args.confirm = true;
    else if (argv[i] === "--root" && argv[i + 1]) args.root = argv[++i];
  }
  return args;
}

function copyTree(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const ent of fs.readdirSync(src, { withFileTypes: true })) {
    if (SKIP.has(ent.name)) continue;
    const from = path.join(src, ent.name);
    const to = path.join(dest, ent.name);
    if (ent.isDirectory()) copyTree(from, to);
    else fs.copyFileSync(from, to);
  }
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to vendor wp-browser-sensor: pass --confirm after the user asked to copy it into this repo.\n");
    process.exit(2);
  }

  const wpRoot = path.resolve(args.root);
  const dest = path.join(wpRoot, ".agents", "skills", "wp-browser-sensor");
  if (path.resolve(dest) === SKILL_ROOT) {
    process.stderr.write("Refusing: destination is this skill pack.\n");
    process.exit(2);
  }

  copyTree(SKILL_ROOT, dest);
  process.stdout.write(`Wrote ${dest}\n`);
}

main();

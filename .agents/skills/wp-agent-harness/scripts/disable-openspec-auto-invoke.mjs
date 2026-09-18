#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const args = { confirm: false, root: process.cwd() };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--confirm") args.confirm = true;
    else if (argv[i] === "--root" && argv[i + 1]) args.root = argv[++i];
  }
  return args;
}

function skillDirs(wpRoot) {
  const roots = [
    path.join(wpRoot, ".agents", "skills"),
    path.join(wpRoot, ".cursor", "skills"),
  ];
  const found = [];
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    for (const ent of fs.readdirSync(root, { withFileTypes: true })) {
      if (!ent.isDirectory() || !ent.name.startsWith("openspec-")) continue;
      found.push(path.join(root, ent.name, "SKILL.md"));
    }
  }
  return found;
}

function pinFrontmatter(raw) {
  const nl = raw.includes("\r\n") ? "\r\n" : "\n";
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return { next: raw, changed: false, reason: "no-frontmatter" };
  const fm = match[1];
  if (/^disable-model-invocation\s*:/m.test(fm)) {
    return { next: raw, changed: false, reason: "already" };
  }
  let pinned = fm.replace(/^(name:\s*.+)$/m, `$1${nl}disable-model-invocation: true`);
  if (pinned === fm) pinned = `disable-model-invocation: true${nl}${fm}`;
  const next = `---${nl}${pinned}${nl}---${raw.slice(match[0].length)}`;
  return { next, changed: true, reason: "pinned" };
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write(
      "Refusing to pin OpenSpec skills: pass --confirm after init (or after the user asked to stop official OpenSpec auto-invoke).\n"
    );
    process.exit(2);
  }

  const wpRoot = path.resolve(args.root);
  const wrote = [];
  for (const file of skillDirs(wpRoot)) {
    if (!fs.existsSync(file)) continue;
    const raw = fs.readFileSync(file, "utf8");
    const result = pinFrontmatter(raw);
    if (result.changed) {
      fs.writeFileSync(file, result.next);
    }
    wrote.push({
      file: path.relative(wpRoot, file).replaceAll("\\", "/"),
      changed: result.changed,
      reason: result.reason,
    });
  }

  process.stdout.write(`${JSON.stringify({ root: wpRoot, skills: wrote }, null, 2)}\n`);
}

main();

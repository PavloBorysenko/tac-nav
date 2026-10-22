#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const MARKER = "harness-task-rules:";
const BULLET =
  '    - "harness-task-rules: tasks.md lists implementation steps. Do not add browser, screenshot, viewport, or click-through tasks. UI check is one wp-browser-sensor pass after green gates, owned by wp-agent-harness. Specs state observable behavior and do not prescribe the browser procedure."';

function parseArgs(argv) {
  const args = { confirm: false, root: process.cwd() };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--confirm") args.confirm = true;
    else if (argv[i] === "--root" && argv[i + 1]) args.root = argv[++i];
  }
  return args;
}

function configFile(wpRoot) {
  const yaml = path.join(wpRoot, "openspec", "config.yaml");
  const yml = path.join(wpRoot, "openspec", "config.yml");
  if (fs.existsSync(yaml)) return yaml;
  if (fs.existsSync(yml)) return yml;
  return null;
}

function insertTaskRules(raw) {
  if (raw.includes(MARKER)) return { next: raw, changed: false, reason: "already" };
  const nl = raw.includes("\r\n") ? "\r\n" : "\n";
  const hadTrailing = raw.endsWith("\n");
  const lines = raw.split(/\r?\n/);
  if (lines.length && lines[lines.length - 1] === "") lines.pop();

  let rulesIdx = -1;
  for (let i = 0; i < lines.length; i += 1) {
    if (/^rules:\s*(#.*)?$/.test(lines[i])) {
      rulesIdx = i;
      break;
    }
  }

  if (rulesIdx === -1) {
    if (lines.length && lines[lines.length - 1] !== "") lines.push("");
    lines.push("rules:", "  tasks:", BULLET);
  } else {
    let end = lines.length;
    for (let i = rulesIdx + 1; i < lines.length; i += 1) {
      if (/^[A-Za-z]/.test(lines[i])) {
        end = i;
        break;
      }
    }
    let tasksIdx = -1;
    for (let i = rulesIdx + 1; i < end; i += 1) {
      if (/^  tasks:\s*(\[\]\s*)?(#.*)?$/.test(lines[i])) {
        tasksIdx = i;
        break;
      }
    }
    if (tasksIdx === -1) {
      lines.splice(end, 0, "  tasks:", BULLET);
    } else if (/\[\s*\]/.test(lines[tasksIdx])) {
      lines.splice(tasksIdx, 1, "  tasks:", BULLET);
    } else {
      lines.splice(tasksIdx + 1, 0, BULLET);
    }
  }

  let next = lines.join(nl);
  if (hadTrailing || !raw.endsWith("\n")) next += nl;
  return { next, changed: true, reason: "written" };
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write(
      "Refusing to write OpenSpec task rules: pass --confirm after the user approved this audit question.\n"
    );
    process.exit(2);
  }

  const wpRoot = path.resolve(args.root);
  const file = configFile(wpRoot);
  if (!file) {
    process.stderr.write(
      "No openspec/config.yaml. Enable OpenSpec before writing task rules.\n"
    );
    process.exit(1);
  }

  const raw = fs.readFileSync(file, "utf8");
  const result = insertTaskRules(raw);
  if (result.changed) fs.writeFileSync(file, result.next);
  process.stdout.write(
    `${JSON.stringify(
      {
        root: wpRoot,
        file: path.relative(wpRoot, file).replaceAll("\\", "/"),
        changed: result.changed,
        reason: result.reason,
      },
      null,
      2
    )}\n`
  );
}

main();

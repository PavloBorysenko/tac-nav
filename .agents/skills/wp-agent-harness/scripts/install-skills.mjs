#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { vendorPackSkills } from "./pack-vendor.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(__dirname, "..");

const SOURCE_INSTALL = {
  "WordPress/agent-skills": (id) => ["npx", ["skills", "add", "wordpress/agent-skills", "--skill", id, "-y"]],
  "wshobson/agents": (id) => ["npx", ["skills", "add", "wshobson/agents", "--skill", id, "-y"]],
};

const NOT_SKILLS = new Set(["phpunit", "jest", "wp-testing", "phpunit.xml.dist", "tools/phpunit", "tools/js-test", "openspec"]);

function parseArgs(argv) {
  const args = { root: process.cwd(), confirm: false, skills: [] };
  for (let i = 2; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === "--root" && argv[i + 1]) args.root = argv[++i];
    else if (a === "--confirm") args.confirm = true;
    else if (a === "--skills" && argv[i + 1]) {
      args.skills = argv[++i]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    }
  }
  return args;
}

function readCatalog() {
  const p = path.join(SKILL_ROOT, "references", "skill-catalog.json");
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

function allKnown(catalog) {
  const rows = [
    ...(catalog.alwaysRequired || []),
    ...(catalog.requiredByShape || []),
    ...(catalog.recommendedByShape || []),
  ];
  const map = new Map();
  for (const row of rows) map.set(row.id, row);
  return map;
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to install: pass --confirm after the user approved an exact skill list.\n");
    process.exit(2);
  }
  if (args.skills.length === 0) {
    process.stderr.write("Refusing to install: --skills is empty.\n");
    process.exit(2);
  }

  const catalog = readCatalog();
  const known = allKnown(catalog);
  const blocked = [];
  const npxCommands = [];
  const packIds = [];

  for (const id of args.skills) {
    if (NOT_SKILLS.has(id)) {
      blocked.push({ id, reason: "not a skill; PHPUnit, Jest, and OpenSpec are separate layers, not install-skills" });
      continue;
    }
    const spec = known.get(id);
    if (!spec) {
      blocked.push({ id, reason: "not in skill-catalog.json" });
      continue;
    }
    if (spec.source === "supernova-pack") {
      packIds.push(id);
      continue;
    }
    const factory = SOURCE_INSTALL[spec.source];
    if (!factory) {
      blocked.push({ id, reason: `no installer for source ${spec.source}` });
      continue;
    }
    npxCommands.push({ id, source: spec.source, argv: factory(id) });
  }

  if (blocked.length) {
    process.stderr.write(`${JSON.stringify({ blocked }, null, 2)}\n`);
  }

  if (npxCommands.length === 0 && packIds.length === 0) {
    process.stderr.write("Nothing to install.\n");
    process.exit(blocked.length ? 3 : 0);
  }

  const cwd = path.resolve(args.root);

  if (packIds.length) {
    try {
      vendorPackSkills({ skillRoot: SKILL_ROOT, wpRoot: cwd, catalog, ids: packIds });
    } catch (err) {
      process.stderr.write(`${err.message}\n`);
      process.exit(1);
    }
  }

  for (const cmd of npxCommands) {
    process.stderr.write(`Installing ${cmd.id} from ${cmd.source}\n`);
    const result = spawnSync(cmd.argv[0], cmd.argv[1], { cwd, stdio: "inherit", shell: process.platform === "win32" });
    if (result.status !== 0) {
      process.stderr.write(`Install failed for ${cmd.id}\n`);
      process.exit(result.status ?? 1);
    }
  }
}

main();

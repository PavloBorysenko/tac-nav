#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { vendorPackSkills } from "./pack-vendor.mjs";

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

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to vendor the harness: pass --confirm after the user asked to copy it into this repo.\n");
    process.exit(2);
  }

  const catalogPath = path.join(SKILL_ROOT, "references", "skill-catalog.json");
  const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
  try {
    vendorPackSkills({
      skillRoot: SKILL_ROOT,
      wpRoot: args.root,
      catalog,
      ids: ["wp-agent-harness", "wp-browser-sensor", "local-code-review"],
    });
  } catch (err) {
    process.stderr.write(`${err.message}\n`);
    process.exit(1);
  }
}

main();

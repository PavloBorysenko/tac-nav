#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const args = {
    confirm: false,
    root: process.cwd(),
    prefixes: [],
    files: [],
    allowEmptyFiles: false,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === "--confirm") args.confirm = true;
    else if (a === "--allow-empty-files") args.allowEmptyFiles = true;
    else if (a === "--root" && argv[i + 1]) args.root = argv[++i];
    else if (a === "--prefixes" && argv[i + 1]) {
      args.prefixes = argv[++i]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    } else if (a === "--files" && argv[i + 1]) {
      args.files = argv[++i]
        .split(",")
        .map((s) => s.trim().replace(/\\/g, "/"))
        .filter(Boolean);
    }
  }
  return args;
}

function xmlEscape(value) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function buildXml({ files, prefixes }) {
  const fileTags = files.map((f) => `  <file>${xmlEscape(f)}</file>`).join("\n");
  const prefixRule =
    prefixes.length === 0
      ? "  <!-- Add WordPress.NamingConventions.PrefixAllGlobals prefixes after the team agrees on them. -->"
      : `  <rule ref="WordPress.NamingConventions.PrefixAllGlobals">
    <properties>
      <property name="prefixes" type="array">
${prefixes.map((p) => `        <element value="${xmlEscape(p)}"/>`).join("\n")}
      </property>
    </properties>
  </rule>`;

  return `<?xml version="1.0"?>
<ruleset name="WP Agent Harness">
  <description>First-party WordPress coding standards. PHPCS itself is installed globally for the whole team.</description>
${fileTags}
  <arg name="extensions" value="php"/>
  <exclude-pattern>*/vendor/*</exclude-pattern>
  <exclude-pattern>*/node_modules/*</exclude-pattern>
  <exclude-pattern>*/build/*</exclude-pattern>
  <rule ref="WordPress-Extra"/>
${prefixRule}
</ruleset>
`;
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to write phpcs.xml.dist: pass --confirm after the user approved repo config.\n");
    process.exit(2);
  }
  if (args.files.length === 0 && !args.allowEmptyFiles) {
    process.stderr.write(
      "Refusing: --files must list confirmed first-party paths, or pass --allow-empty-files after intent Yes with no custom code yet.\n",
    );
    process.exit(2);
  }
  if (args.files.length === 0 && args.allowEmptyFiles) {
    process.stderr.write(
      "Writing phpcs.xml.dist with no <file> entries. Register the first non-tiny theme or plugin later; do not invent paths.\n",
    );
  }

  const blocked = ["wp-admin", "wp-includes"];
  for (const f of args.files) {
    if (blocked.some((b) => f.split("/").includes(b))) {
      process.stderr.write(`Refusing path ${f}: Core is out of scope.\n`);
      process.exit(2);
    }
  }

  const target = path.join(path.resolve(args.root), "phpcs.xml.dist");
  if (fs.existsSync(target)) {
    process.stderr.write(`Refusing: ${target} already exists. Will not overwrite.\n`);
    process.exit(3);
  }

  fs.writeFileSync(target, buildXml(args), "utf8");
  process.stderr.write(`Wrote ${target}\n`);
}

main();

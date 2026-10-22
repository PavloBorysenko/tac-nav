#!/usr/bin/env node
// Installs playwright-core (no browser download) into tools/browser-check/ of the WordPress root.
// It uses the Chrome or Edge already installed on the machine.
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const argv = process.argv;
const confirm = argv.includes("--confirm");
const ri = argv.indexOf("--root");
const root = path.resolve(ri > 0 ? argv[ri + 1] : process.cwd());
if (!confirm) {
  process.stderr.write("Refusing to install: pass --confirm after the user approved tools/browser-check.\n");
  process.exit(2);
}
const dir = path.join(root, "tools", "browser-check");
fs.mkdirSync(dir, { recursive: true });
const pkg = path.join(dir, "package.json");
if (!fs.existsSync(pkg)) {
  fs.writeFileSync(pkg, `${JSON.stringify({ name: "browser-check", private: true, dependencies: { "playwright-core": "^1.56.0" } }, null, 2)}\n`);
}
const gi = path.join(dir, ".gitignore");
if (!fs.existsSync(gi)) fs.writeFileSync(gi, ".profile/\nnode_modules/\nshots/\n");
const r = spawnSync(process.platform === "win32" ? "npm.cmd" : "npm", ["install", "--no-audit", "--no-fund"], {
  cwd: dir, stdio: "inherit", env: { ...process.env, PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: "1" }, shell: process.platform === "win32",
});
process.exit(r.status ?? 1);

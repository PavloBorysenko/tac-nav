#!/usr/bin/env node
// Writes this machine's local site URL. The URL differs per developer, so the file is
// never committed: in a Local site it goes next to app/public (outside git); otherwise
// into the repo root with a .gitignore entry.
import fs from "node:fs";
import path from "node:path";

const argv = process.argv;
const get = (k) => { const i = argv.indexOf(k); return i > 0 ? argv[i + 1] : ""; };
if (!argv.includes("--confirm")) {
  process.stderr.write("Refusing to write local-site.json: pass --confirm after the user gave the URL.\n");
  process.exit(2);
}
let origin;
try {
  const u = new URL(get("--url"));
  if (!/^https?:$/.test(u.protocol)) throw new Error("scheme");
  origin = u.origin;
} catch {
  process.stderr.write("Refusing: --url must be an http(s) URL such as https://name.local\n");
  process.exit(2);
}
const root = path.resolve(get("--root") || process.cwd());
const inLocalApp = path.basename(root) === "public" && path.basename(path.dirname(root)) === "app";
const dir = inLocalApp ? path.dirname(root) : root;
const file = path.join(dir, "local-site.json");
fs.writeFileSync(file, `${JSON.stringify({ url: origin }, null, 2)}\n`);
if (!inLocalApp) {
  const gi = path.join(root, ".gitignore");
  const cur = fs.existsSync(gi) ? fs.readFileSync(gi, "utf8") : "";
  if (!/^\/?local-site\.json$/m.test(cur)) fs.appendFileSync(gi, `${cur && !cur.endsWith("\n") ? "\n" : ""}local-site.json\n`);
}
process.stdout.write(`Wrote ${file}\n`);

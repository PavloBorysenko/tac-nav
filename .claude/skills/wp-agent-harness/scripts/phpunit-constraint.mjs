#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), "php-tool-constraint.mjs");
const result = spawnSync(process.execPath, [script, ...process.argv.slice(2)], { encoding: "utf8" });
if (result.stderr) process.stderr.write(result.stderr);
if (result.status !== 0) process.exit(result.status ?? 2);

let payload;
try {
  payload = JSON.parse(result.stdout);
} catch {
  process.stderr.write(result.stdout || "php-tool-constraint.mjs returned invalid JSON.\n");
  process.exit(2);
}

if (!payload.phpunit || !payload.packages?.phpunit) {
  process.stderr.write(`No supported PHPUnit constraint for PHP ${payload.php}. Need PHP 7.3 or newer.\n`);
  process.exit(2);
}

process.stdout.write(
  `${JSON.stringify(
    {
      php: payload.php,
      phpunit: payload.phpunit,
      require: payload.packages.phpunit,
      platform: payload.platform,
    },
    null,
    2
  )}\n`
);

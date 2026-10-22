#!/usr/bin/env node
import { spawnSync } from "node:child_process";

function parseArgs(argv) {
  const args = { php: "php", phpVersion: null, help: false };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--help" || argv[i] === "-h") args.help = true;
    else if (argv[i] === "--php" && argv[i + 1]) args.php = argv[++i];
    else if (argv[i] === "--php-version" && argv[i + 1]) args.phpVersion = argv[++i];
  }
  return args;
}

function parsePhp(phpVersion) {
  const match = String(phpVersion).trim().match(/^(\d+)\.(\d+)/);
  if (!match) return null;
  return { major: Number(match[1]), minor: Number(match[2]), text: String(phpVersion).trim() };
}

function atLeast(v, major, minor) {
  return v.major > major || (v.major === major && v.minor >= minor);
}

export function constraintsFor(phpVersion) {
  const v = parsePhp(phpVersion);
  if (!v) return null;

  let phpunit = null;
  if (atLeast(v, 8, 4)) phpunit = "^13";
  else if (v.major === 8 && v.minor === 3) phpunit = "^12";
  else if (v.major === 8 && v.minor === 2) phpunit = "^11";
  else if (v.major === 8 && v.minor === 1) phpunit = "^10";
  else if (atLeast(v, 7, 3)) phpunit = "^9.6";

  let phpstan = null;
  let phpstanWordpress = null;
  if (atLeast(v, 7, 4)) {
    phpstan = "^2.0";
    phpstanWordpress = "^2.0";
  } else if (atLeast(v, 7, 2)) {
    phpstan = "^1.12";
    phpstanWordpress = "^1.3";
  }

  // WPCS 3.x requires PHPCS 3.13.5+, not PHPCS 4.
  const phpcs = atLeast(v, 7, 2) ? "^3.13.5" : null;
  const wpcs = atLeast(v, 7, 2) ? "^3.0" : null;

  if (!phpunit && !phpstan && !phpcs) return null;

  const platform = `${v.major}.${v.minor}.0`;
  const packages = {
    phpunit: phpunit ? `phpunit/phpunit:${phpunit}` : null,
    phpstan: phpstan ? `phpstan/phpstan:${phpstan}` : null,
    phpstanWordpress: phpstanWordpress ? `szepeviktor/phpstan-wordpress:${phpstanWordpress}` : null,
    phpcs: phpcs ? `squizlabs/php_codesniffer:${phpcs}` : null,
    wpcs: wpcs ? `wp-coding-standards/wpcs:${wpcs}` : null,
    phpcsInstaller: "dealerdirect/phpcodesniffer-composer-installer:^1.0",
  };

  return {
    php: v.text,
    platform,
    phpunit,
    phpstan,
    phpstanWordpress,
    phpcs,
    wpcs,
    packages,
  };
}

function detectPhpVersion(phpBin) {
  const result = spawnSync(
    phpBin,
    ["-r", "echo PHP_MAJOR_VERSION, '.', PHP_MINOR_VERSION, '.', PHP_RELEASE_VERSION;"],
    { encoding: "utf8", shell: process.platform === "win32" }
  );
  if (result.status !== 0) {
    const err = (result.stderr || result.stdout || "php failed").trim();
    throw new Error(err || `Could not run ${phpBin}`);
  }
  return (result.stdout || "").trim();
}

function printHelp() {
  process.stdout.write(`Select Composer constraints for the PHP binary that will run PHPCS, PHPStan, and PHPUnit.

Usage:
  node php-tool-constraint.mjs
  node php-tool-constraint.mjs --php "/path/to/local/php"
  node php-tool-constraint.mjs --php-version 8.1.29

Prints JSON with platform.php and pinned packages. Exit 2 if PHP is missing or too old.
Use Local site PHP, not a newer CLI. Never composer-require these tools unpinned.
`);
}

function main() {
  const args = parseArgs(process.argv);
  if (args.help) {
    printHelp();
    return;
  }

  let phpVersion = args.phpVersion;
  try {
    if (!phpVersion) phpVersion = detectPhpVersion(args.php);
  } catch (err) {
    process.stderr.write(`${err.message}\nUse the PHP binary that will run the tools (Local site PHP, not a newer CLI).\n`);
    process.exit(2);
  }

  const payload = constraintsFor(phpVersion);
  if (!payload) {
    process.stderr.write(`No supported PHPCS/PHPStan/PHPUnit constraint for PHP ${phpVersion}. Need PHP 7.2 or newer.\n`);
    process.exit(2);
  }

  process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
}

const invokedDirectly = Boolean(process.argv[1]?.replace(/\\/g, "/").endsWith("/php-tool-constraint.mjs"));
if (invokedDirectly) {
  main();
}

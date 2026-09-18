#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = { confirm: false, global: false, php: null, phpVersion: null };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--confirm") args.confirm = true;
    else if (argv[i] === "--global") args.global = true;
    else if (argv[i] === "--php" && argv[i + 1]) args.php = argv[++i];
    else if (argv[i] === "--php-version" && argv[i + 1]) args.phpVersion = argv[++i];
  }
  return args;
}

function loadConstraints(args) {
  const script = path.join(__dirname, "php-tool-constraint.mjs");
  const extra = [];
  if (args.phpVersion) extra.push("--php-version", args.phpVersion);
  else if (args.php) extra.push("--php", args.php);
  const result = spawnSync(process.execPath, [script, ...extra], { encoding: "utf8" });
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.status !== 0) process.exit(result.status ?? 2);
  return JSON.parse(result.stdout);
}

function main() {
  const args = parseArgs(process.argv);
  if (!args.confirm) {
    process.stderr.write("Refusing to install PHPCS: pass --confirm after the user approved a global install.\n");
    process.exit(2);
  }
  if (!args.global) {
    process.stderr.write("Refusing: this harness installs PHPCS globally only. Pass --global. Do not add phpcs to a theme/plugin composer.json.\n");
    process.exit(2);
  }

  const constraints = loadConstraints(args);
  if (!constraints.packages?.phpcs || !constraints.packages?.wpcs) {
    process.stderr.write(`No PHPCS/WPCS constraint for PHP ${constraints.php}. Need PHP 7.2 or newer.\n`);
    process.exit(2);
  }

  const env = { ...process.env };
  if (env.COMPOSER_HOME && env.COMPOSER_HOME.includes("cursor-sandbox")) {
    delete env.COMPOSER_HOME;
  }
  const spawnOpts = {
    stdio: "inherit",
    shell: process.platform === "win32",
    env,
  };

  process.stderr.write(`Pin Composer platform.php=${constraints.platform} to the PHP that will run phpcs.\n`);
  const platform = spawnSync(
    "composer",
    ["global", "config", "platform.php", constraints.platform],
    spawnOpts
  );
  if (platform.status !== 0) process.exit(platform.status ?? 1);

  process.stderr.write("Allow Composer plugin that registers WPCS installed_paths.\n");
  const allow = spawnSync(
    "composer",
    ["global", "config", "--no-plugins", "allow-plugins.dealerdirect/phpcodesniffer-composer-installer", "true"],
    spawnOpts
  );
  if (allow.status !== 0) {
    process.exit(allow.status ?? 1);
  }

  const packages = [
    constraints.packages.phpcs,
    constraints.packages.wpcs,
    constraints.packages.phpcsInstaller,
  ];
  process.stderr.write("composer global require pinned PHPCS 3.x + WPCS 3.x. Do not install PHPCS 4: WPCS does not support it.\n");
  const result = spawnSync("composer", ["global", "require", ...packages, "--no-interaction"], spawnOpts);
  process.exit(result.status ?? 1);
}

main();

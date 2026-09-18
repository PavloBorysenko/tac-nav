#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const TOOL_VERSION = "0.12.0";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SKILL_ROOT = path.resolve(__dirname, "..");

const THIRD_PARTY_SLUGS = new Set([
  "akismet",
  "admin-menu-editor",
  "advanced-custom-fields",
  "advanced-custom-fields-pro",
  "breakdance",
  "contact-form-7",
  "cron-logger",
  "duplicate-post",
  "error-log-monitor",
  "facetwp",
  "facetwp-hierarchy-select",
  "hello-dolly",
  "perfmatters",
  "perfmatters-old",
  "post-types-order",
  "query-monitor",
  "taxonomy-terms-order",
  "webp-express",
  "woocommerce",
  "woocommerce-payments",
  "wordpress-importer",
  "wp-consent-api",
  "wp-crontrol",
  "wp-rocket",
  "cf7-telegram",
  "action-scheduler",
  "admin-site-enhancements",
  "bp-better-messages",
  "cookie-law-info",
  "facetwp-submit",
  "limit-login-attempts-reloaded",
  "members",
  "pexlechris-adminer",
  "redirection",
  "ultimate-member",
  "um-social-login",
  "woocommerce-gateway-stripe",
  "woocommerce-pdf-invoices-packing-slips",
  "wordpress-seo",
  "wp-mail-logging",
  "wp-mail-smtp",
  "wp-migrate-db",
  "ws-action-scheduler-cleaner",
  "breakdance-zero-theme-master",
]);

const IGNORE_DIRS = new Set([
  ".git",
  "node_modules",
  "vendor",
  "dist",
  "build",
  "wp-admin",
  "wp-includes",
]);

function parseArgs(argv) {
  const args = { root: process.cwd(), firstParty: [], prefixes: [] };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--root" && argv[i + 1]) {
      args.root = argv[++i];
    } else if (argv[i] === "--first-party" && argv[i + 1]) {
      args.firstParty = argv[++i]
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    } else if (argv[i] === "--prefix" && argv[i + 1]) {
      args.prefixes = argv[++i]
        .split(",")
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean);
    }
  }
  return args;
}

function exists(p) {
  try {
    fs.accessSync(p);
    return true;
  } catch {
    return false;
  }
}

function isDir(p) {
  try {
    return fs.statSync(p).isDirectory();
  } catch {
    return false;
  }
}

function isFile(p) {
  try {
    return fs.statSync(p).isFile();
  } catch {
    return false;
  }
}

function readJsonSafe(p) {
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return null;
  }
}

function readTextSafe(p) {
  try {
    return fs.readFileSync(p, "utf8");
  } catch {
    return "";
  }
}

function treeContains(dir, pattern, state = { files: 0 }) {
  if (!isDir(dir) || state.files > 300) return false;
  let entries = [];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return false;
  }
  for (const ent of entries) {
    if (IGNORE_DIRS.has(ent.name) || ent.name.startsWith(".")) continue;
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      if (treeContains(full, pattern, state)) return true;
      continue;
    }
    if (!ent.isFile() || !ent.name.endsWith(".php")) continue;
    state.files += 1;
    if (pattern.test(readTextSafe(full))) return true;
  }
  return false;
}

function walkUp(start, predicate) {
  let dir = path.resolve(start);
  while (true) {
    if (predicate(dir)) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

function resolveWpRoot(start) {
  const fromMarker = walkUp(start, (dir) => isDir(path.join(dir, "wp-content")) && (isFile(path.join(dir, "wp-load.php")) || isFile(path.join(dir, "wp-config.php"))));
  if (fromMarker) return fromMarker;
  const git = walkUp(start, (dir) => isDir(path.join(dir, ".git")));
  return git || path.resolve(start);
}

function hasBin(name) {
  const cmd = process.platform === "win32" ? "where" : "which";
  try {
    execFileSync(cmd, [name], { stdio: "pipe" });
    return true;
  } catch {
    return false;
  }
}

function runTool(name, args) {
  const result = spawnSync(name, args, {
    encoding: "utf8",
    shell: process.platform === "win32",
    stdio: ["ignore", "pipe", "pipe"],
  });
  if (result.status !== 0) return null;
  return String(result.stdout || "").trim();
}

function inspectPhpcs(wpRoot) {
  const onPath = hasBin("phpcs");
  const versionOut = onPath ? runTool("phpcs", ["--version"]) : null;
  const installedOut = onPath ? runTool("phpcs", ["-i"]) : null;
  const standards = [];
  if (installedOut) {
    const match = installedOut.match(/standards are (.+)$/i);
    const list = match ? match[1] : installedOut;
    for (const part of list.split(/,| and /i)) {
      const name = part.trim();
      if (name) standards.push(name);
    }
  }
  const wpcs = standards.some((s) => s === "WordPress" || s.startsWith("WordPress-"));
  const vendorBin = path.join(wpRoot, "vendor", "bin", process.platform === "win32" ? "phpcs.bat" : "phpcs");
  const dist = isFile(path.join(wpRoot, "phpcs.xml.dist"));
  const xml = isFile(path.join(wpRoot, "phpcs.xml"));
  return {
    policy: "global",
    onPath,
    version: versionOut,
    standards,
    wpcs,
    phpcbf: hasBin("phpcbf"),
    projectVendor: isFile(vendorBin),
    config: {
      dist,
      xml,
      path: dist ? "phpcs.xml.dist" : xml ? "phpcs.xml" : null,
    },
  };
}

function inspectPhpstan(wpRoot) {
  const dist = isFile(path.join(wpRoot, "phpstan.neon.dist"));
  const neon = isFile(path.join(wpRoot, "phpstan.neon"));
  const baseline = isFile(path.join(wpRoot, "phpstan-baseline.neon"));
  const binName = process.platform === "win32" ? "phpstan.bat" : "phpstan";
  const toolsBin = path.join(wpRoot, "tools", "phpstan", "vendor", "bin", binName);
  return {
    policy: "tools",
    onPath: hasBin("phpstan"),
    toolsVendor: isFile(toolsBin),
    config: {
      dist,
      neon,
      path: dist ? "phpstan.neon.dist" : neon ? "phpstan.neon" : null,
    },
    baseline,
  };
}

function isHashedOrMinJs(fileName) {
  if (fileName.endsWith(".min.js")) return true;
  return /\.[a-f0-9]{8,}\.js$/i.test(fileName);
}

function countLintableJs(dir, acc = { count: 0 }) {
  if (!isDir(dir) || acc.count > 400) return acc;
  let entries = [];
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return acc;
  }
  for (const ent of entries) {
    if (IGNORE_DIRS.has(ent.name) || ent.name.startsWith(".")) continue;
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      countLintableJs(full, acc);
      continue;
    }
    if (!ent.isFile() || !ent.name.endsWith(".js") || isHashedOrMinJs(ent.name)) continue;
    acc.count += 1;
  }
  return acc;
}

function inspectJsLint(wpRoot) {
  const dir = path.join(wpRoot, "tools", "js-lint");
  const pkg = isFile(path.join(dir, "package.json"));
  const config = ["eslint.config.js", "eslint.config.mjs", ".eslintrc.cjs", ".eslintrc.js"].some((n) =>
    isFile(path.join(dir, n))
  );
  const bin = isFile(path.join(dir, "node_modules", "eslint", "bin", "eslint.js"));
  return {
    policy: "tools",
    present: pkg && config,
    nodeModules: bin,
    config,
  };
}

const JEST_CONFIG_NAMES = [
  "jest.config.js",
  "jest.config.cjs",
  "jest.config.mjs",
  "jest.config.ts",
  "jest.config.json",
];

function hasJestConfig(dir) {
  return JEST_CONFIG_NAMES.some((n) => isFile(path.join(dir, n)));
}

function inspectPhpunit(wpRoot, components) {
  const dist = isFile(path.join(wpRoot, "phpunit.xml.dist"));
  const xml = isFile(path.join(wpRoot, "phpunit.xml"));
  const binName = process.platform === "win32" ? "phpunit.bat" : "phpunit";
  const toolsBin = path.join(wpRoot, "tools", "phpunit", "vendor", "bin", binName);
  const configPaths = [
    dist ? "phpunit.xml.dist" : null,
    xml ? "phpunit.xml" : null,
    ...components.flatMap((component) =>
      ["phpunit.xml.dist", "phpunit.xml"]
        .filter((name) => isFile(path.join(wpRoot, component.path, name)))
        .map((name) => `${component.path}/${name}`)
    ),
  ].filter(Boolean);
  const componentVendor = components.some((component) =>
    isFile(path.join(wpRoot, component.path, "vendor", "bin", binName))
  );
  const composerFiles = [
    path.join(wpRoot, "tools", "phpunit", "composer.json"),
    path.join(wpRoot, "composer.json"),
    ...components.map((component) => path.join(wpRoot, component.path, "composer.json")),
  ];
  const integrationPackage = composerFiles.some((composerPath) => {
    const composer = readJsonSafe(composerPath);
    return Boolean(
      composer?.require?.["wp-phpunit/wp-phpunit"] ||
      composer?.["require-dev"]?.["wp-phpunit/wp-phpunit"]
    );
  });
  const integrationSignal = /WP_UnitTestCase|WP_PHPUNIT__TESTS_CONFIG|WP_TESTS_DIR|wp-phpunit|tests_add_filter|includes\/bootstrap\.php/i;
  let bootstrapEvidence = false;
  for (const rel of configPaths) {
    const configPath = path.join(wpRoot, rel);
    const configText = readTextSafe(configPath);
    if (integrationSignal.test(configText)) {
      bootstrapEvidence = true;
      break;
    }
    const bootstrap = configText.match(/\bbootstrap\s*=\s*["']([^"']+)["']/i)?.[1];
    if (bootstrap && integrationSignal.test(readTextSafe(path.resolve(path.dirname(configPath), bootstrap)))) {
      bootstrapEvidence = true;
      break;
    }
  }
  const testsUseWPUnitTestCase = components.some((component) =>
    treeContains(path.join(wpRoot, component.path, "tests"), /\bWP_UnitTestCase\b/)
  );
  const runner = hasBin("phpunit") || isFile(toolsBin) || componentVendor;
  const wpUnitTestCase = {
    present: configPaths.length > 0 && (integrationPackage || bootstrapEvidence),
    package: integrationPackage,
    bootstrap: bootstrapEvidence,
    testsUseClass: testsUseWPUnitTestCase,
  };
  return {
    policy: "tools",
    onPath: hasBin("phpunit"),
    toolsVendor: isFile(toolsBin),
    componentVendor,
    present: configPaths.length > 0 && runner,
    config: {
      dist,
      xml,
      path: dist ? "phpunit.xml.dist" : xml ? "phpunit.xml" : null,
      paths: configPaths,
    },
    wpUnitTestCase,
  };
}

function inspectJest(wpRoot) {
  const dir = path.join(wpRoot, "tools", "js-test");
  const pkg = isFile(path.join(dir, "package.json"));
  const toolsConfig = hasJestConfig(dir);
  const rootConfig = hasJestConfig(wpRoot);
  const rootPkg = readJsonSafe(path.join(wpRoot, "package.json"));
  const rootJestKey = Boolean(rootPkg?.jest);
  const bin = isFile(path.join(dir, "node_modules", "jest", "bin", "jest.js"));
  return {
    policy: "tools",
    present: (pkg && toolsConfig) || rootConfig || rootJestKey,
    tools: pkg && toolsConfig,
    nodeModules: bin,
    config: toolsConfig || rootConfig || rootJestKey,
  };
}

function openspecSkillFiles(wpRoot) {
  const found = [];
  for (const rel of [".agents/skills", ".cursor/skills"]) {
    const dir = path.join(wpRoot, rel);
    if (!isDir(dir)) continue;
    let names;
    try {
      names = fs.readdirSync(dir);
    } catch {
      continue;
    }
    for (const name of names) {
      if (!name.startsWith("openspec-")) continue;
      const file = path.join(dir, name, "SKILL.md");
      if (!isFile(file)) continue;
      found.push({ id: name, location: `project:${rel}`, file });
    }
  }
  return found;
}

function openspecSkillPinned(text) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return false;
  return /^disable-model-invocation\s*:\s*true\s*$/m.test(match[1]);
}

function inspectOpenspec(wpRoot) {
  const onPath = hasBin("openspec");
  const versionOut = onPath ? runTool("openspec", ["--version"]) : null;
  const yaml = isFile(path.join(wpRoot, "openspec", "config.yaml"));
  const yml = isFile(path.join(wpRoot, "openspec", "config.yml"));
  const configPath = yaml ? "openspec/config.yaml" : yml ? "openspec/config.yml" : null;
  const skillFiles = openspecSkillFiles(wpRoot);
  const present = [];
  const unpinned = [];
  for (const row of skillFiles) {
    let text = "";
    try {
      text = fs.readFileSync(row.file, "utf8");
    } catch {
      unpinned.push(row.id);
      present.push({ id: row.id, location: row.location, pinned: false });
      continue;
    }
    const pinned = openspecSkillPinned(text);
    present.push({ id: row.id, location: row.location, pinned });
    if (!pinned) unpinned.push(row.id);
  }
  const skillIds = [...new Set(present.map((s) => s.id))];
  return {
    policy: "global",
    onPath,
    version: versionOut,
    initialized: Boolean(configPath),
    config: {
      path: configPath,
    },
    skills: {
      present,
      ids: skillIds,
      autoInvokePinned: skillIds.length > 0 && unpinned.length === 0,
    },
    missing: {
      cli: !onPath,
      init: !configPath,
      skills: Boolean(configPath) && skillIds.length === 0,
      pin: skillIds.length > 0 && unpinned.length > 0,
    },
  };
}

function parseMdcFrontmatter(text) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return {};
  const front = {};
  for (const line of match[1].split(/\r?\n/)) {
    const kv = line.match(/^([A-Za-z0-9_]+):\s*(.*)$/);
    if (!kv) continue;
    front[kv[1]] = kv[2].trim().replace(/^["']|["']$/g, "");
  }
  return front;
}

function inspectCursorRules(wpRoot) {
  const catalog = readJsonSafe(path.join(SKILL_ROOT, "references", "rules-catalog.json")) || {};
  const dir = path.join(wpRoot, ".cursor", "rules");
  const names = isDir(dir)
    ? fs.readdirSync(dir).filter((n) => n.endsWith(".mdc"))
    : [];
  const qaLoop = {
    present: false,
    file: catalog.qaLoop?.file ?? ".cursor/rules/wp-agent-harness-qa.mdc",
    mode: null,
    alwaysApply: false,
    defaultMode: catalog.qaLoop?.defaultMode ?? "task",
    modes: catalog.qaLoop?.modes ?? [],
  };
  const recommended = (catalog.recommended ?? []).map((r) => ({
    id: r.id,
    present: false,
    file: r.file ?? null,
    why: r.why,
  }));

  for (const name of names) {
    const rel = `.cursor/rules/${name}`;
    let text = "";
    try {
      text = fs.readFileSync(path.join(dir, name), "utf8");
    } catch {
      continue;
    }
    const front = parseMdcFrontmatter(text);
    const harness = front.harness || "";
    if (harness === "qa-loop" || name === "wp-agent-harness-qa.mdc") {
      qaLoop.present = true;
      qaLoop.file = rel;
      qaLoop.mode = front.qaLoop || "task";
      qaLoop.alwaysApply = front.alwaysApply === "true";
    }
    if (harness === "first-party-scope" || name === "wp-agent-harness-scope.mdc") {
      const row = recommended.find((r) => r.id === "first-party-scope");
      if (row) {
        row.present = true;
        row.file = rel;
      }
    }
    if (/docs\/catalog\.md/.test(text)) {
      const row = recommended.find((r) => r.id === "docs-catalog");
      if (row && !row.present) {
        row.present = true;
        row.file = rel;
      }
    }
  }

  return {
    qaLoop,
    recommended,
    missing: {
      qaLoop: !qaLoop.present,
    },
  };
}

function gitLsFiles(wpRoot, spec) {
  try {
    const out = execFileSync("git", ["-C", wpRoot, "ls-files", "--", spec], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    });
    return out.split(/\r?\n/).filter(Boolean);
  } catch {
    return null;
  }
}

function isDefaultTheme(slug) {
  return slug.startsWith("twenty");
}

function gitFirstParty(wpRoot) {
  const probe = gitLsFiles(wpRoot, ".");
  if (probe === null) return { available: false, components: [] };

  const files = gitLsFiles(wpRoot, "wp-content/themes") ?? [];
  const pluginFiles = gitLsFiles(wpRoot, "wp-content/plugins") ?? [];
  const components = [];
  const seen = new Set();
  for (const rel of [...files, ...pluginFiles]) {
    const parts = rel.split(/[/\\]/);
    if (parts.length < 3 || parts[0] !== "wp-content") continue;
    const type = parts[1];
    const slug = parts[2];
    if (type !== "themes" && type !== "plugins") continue;
    if (isDefaultTheme(slug) || THIRD_PARTY_SLUGS.has(slug)) continue;
    const key = `${type}/${slug}`;
    if (seen.has(key)) continue;
    seen.add(key);
    components.push({
      type: type === "themes" ? "theme" : "plugin",
      slug,
      path: path.join("wp-content", type, slug),
    });
  }
  return { available: true, components };
}

function listCandidates(wpRoot) {
  const components = [];
  for (const type of ["themes", "plugins"]) {
    const base = path.join(wpRoot, "wp-content", type);
    if (!isDir(base)) continue;
    for (const slug of fs.readdirSync(base)) {
      if (isDefaultTheme(slug) || THIRD_PARTY_SLUGS.has(slug)) continue;
      const dir = path.join(base, slug);
      if (!isDir(dir)) continue;
      components.push({
        type: type === "themes" ? "theme" : "plugin",
        slug,
        path: path.join("wp-content", type, slug),
      });
    }
  }
  return components;
}

function matchesPrefix(slug, prefixes) {
  const lower = slug.toLowerCase();
  return prefixes.some((p) => lower === p || lower.startsWith(`${p}-`) || lower.startsWith(p));
}

function resolveUserFirstParty(wpRoot, specs, prefixes, candidates) {
  const byPath = new Map(candidates.map((c) => [c.path.replace(/\\/g, "/"), c]));
  const picked = [];
  const seen = new Set();

  const add = (c) => {
    const key = c.path.replace(/\\/g, "/");
    if (seen.has(key)) return;
    seen.add(key);
    picked.push(c);
  };

  for (const spec of specs) {
    const normalized = spec.replace(/\\/g, "/").replace(/^wp-content\//, "");
    const absTheme = path.join("wp-content", "themes", spec);
    const absPlugin = path.join("wp-content", "plugins", spec);
    const hit =
      byPath.get(normalized) ||
      byPath.get(`wp-content/${normalized}`) ||
      candidates.find((c) => c.slug === spec) ||
      (isDir(path.join(wpRoot, absTheme))
        ? { type: "theme", slug: spec, path: absTheme }
        : null) ||
      (isDir(path.join(wpRoot, absPlugin))
        ? { type: "plugin", slug: spec, path: absPlugin }
        : null);
    if (hit) add({ ...hit, confirmedBy: "user" });
  }

  if (prefixes.length) {
    for (const c of candidates) {
      if (matchesPrefix(c.slug, prefixes)) add({ ...c, confirmedBy: "prefix" });
    }
  }

  return picked;
}

function collectFiles(root, predicate, { maxFiles = 4000, maxDepth = 8 } = {}) {
  const results = [];
  const queue = [{ dir: root, depth: 0 }];
  let visited = 0;
  while (queue.length) {
    const { dir, depth } = queue.shift();
    if (depth > maxDepth) continue;
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) {
        if (IGNORE_DIRS.has(ent.name)) continue;
        queue.push({ dir: full, depth: depth + 1 });
        continue;
      }
      if (!ent.isFile()) continue;
      visited += 1;
      if (visited > maxFiles) return results;
      if (predicate(full, ent.name)) results.push(full);
    }
  }
  return results;
}

function shapeFlags(wpRoot, components) {
  const flags = {
    wpSite: isDir(path.join(wpRoot, "wp-content")),
    customPlugin: components.some((c) => c.type === "plugin"),
    customTheme: components.some((c) => c.type === "theme"),
    themeJson: false,
    blockJson: false,
    firstPartyPhp: false,
    restApi: false,
    patterns: false,
    frontend: false,
    wpAdminUi: false,
  };

  for (const c of components) {
    const abs = path.join(wpRoot, c.path);
    if (isFile(path.join(abs, "theme.json"))) flags.themeJson = true;
    if (isDir(path.join(abs, "patterns"))) flags.patterns = true;
    const files = collectFiles(abs, (full, name) => {
      if (name === "block.json") flags.blockJson = true;
      if (name.endsWith(".php")) flags.firstPartyPhp = true;
      if (name.endsWith(".scss") || name.endsWith(".css") || name.endsWith(".js")) flags.frontend = true;
      return name.endsWith(".php");
    });
    for (const file of files.slice(0, 80)) {
      try {
        const text = fs.readFileSync(file, "utf8");
        if (text.includes("register_rest_route")) flags.restApi = true;
        if (text.includes("add_menu_page") || text.includes("add_options_page")) flags.wpAdminUi = true;
      } catch {
        /* ignore */
      }
    }
  }
  return flags;
}

function classifyKind(flags) {
  if (flags.wpSite && (flags.customPlugin || flags.customTheme)) return "wp-site";
  if (flags.themeJson) return "wp-block-theme";
  if (flags.customTheme) return "wp-theme";
  if (flags.blockJson && flags.customPlugin) return "wp-block-plugin";
  if (flags.customPlugin) return "wp-plugin";
  if (flags.wpSite) return "wp-site";
  return "unknown";
}

function skillDirs(wpRoot) {
  const home = process.env.USERPROFILE || process.env.HOME || "";
  const dirs = [
    { location: "project:.agents/skills", dir: path.join(wpRoot, ".agents", "skills") },
    { location: "project:.cursor/skills", dir: path.join(wpRoot, ".cursor", "skills") },
    { location: "user:~/.cursor/skills", dir: path.join(home, ".cursor", "skills") },
    { location: "user:~/.agents/skills", dir: path.join(home, ".agents", "skills") },
  ];
  return dirs.filter((d) => isDir(d.dir));
}

function listInstalledSkills(wpRoot) {
  const found = new Map();
  const lock = readJsonSafe(path.join(wpRoot, "skills-lock.json"));
  if (lock?.skills && typeof lock.skills === "object") {
    for (const id of Object.keys(lock.skills)) {
      found.set(id, { id, locations: ["project:skills-lock.json"] });
    }
  }
  for (const { location, dir } of skillDirs(wpRoot)) {
    let names;
    try {
      names = fs.readdirSync(dir);
    } catch {
      continue;
    }
    for (const id of names) {
      if (!isFile(path.join(dir, id, "SKILL.md"))) continue;
      const prev = found.get(id);
      if (prev) {
        if (!prev.locations.includes(location)) prev.locations.push(location);
      } else {
        found.set(id, { id, locations: [location] });
      }
    }
  }
  const harnessPresent = isFile(path.join(SKILL_ROOT, "SKILL.md"));
  if (harnessPresent) {
    const prev = found.get("wp-agent-harness");
    const loc = `skill-pack:${SKILL_ROOT}`;
    if (prev) {
      if (!prev.locations.includes(loc)) prev.locations.push(loc);
    } else {
      found.set("wp-agent-harness", { id: "wp-agent-harness", locations: [loc] });
    }
  }
  return found;
}

function isProjectSkillLocation(location) {
  return location === "project:.agents/skills" || location === "project:.cursor/skills";
}

function skillIsPresent(spec, hit) {
  const locations = hit?.locations ?? [];
  const projectOnly = spec.source === "supernova-pack" || spec.set === "wordpress";
  if (projectOnly) {
    return locations.some((l) => isProjectSkillLocation(l));
  }
  return locations.length > 0;
}

function catalogRows(catalog) {
  return [
    ...(catalog.alwaysRequired || []),
    ...(catalog.requiredByShape || []),
    ...(catalog.recommendedByShape || []),
  ];
}

function flagTrue(flags, when) {
  if (when === "always") return true;
  return Boolean(flags[when]);
}

function qaInComponent(wpRoot, component) {
  const abs = path.join(wpRoot, component.path);
  const pkg = readJsonSafe(path.join(abs, "package.json"));
  const composer = readJsonSafe(path.join(abs, "composer.json"));
  const scripts = pkg?.scripts && typeof pkg.scripts === "object" ? Object.keys(pkg.scripts) : [];
  const lintable = countLintableJs(abs);
  return {
    path: component.path,
    phpcs: ["phpcs.xml", "phpcs.xml.dist", ".phpcs.xml"].some((n) => isFile(path.join(abs, n))),
    phpstan: ["phpstan.neon", "phpstan.neon.dist"].some((n) => isFile(path.join(abs, n))),
    phpunit: ["phpunit.xml", "phpunit.xml.dist"].some((n) => isFile(path.join(abs, n))),
    jest: scripts.includes("test") || hasJestConfig(abs) || Boolean(pkg?.jest),
    lintJs: scripts.includes("lint:js"),
    lintCss: scripts.includes("lint:css") || scripts.includes("lint-style"),
    wpScripts: Boolean(pkg?.devDependencies?.["@wordpress/scripts"] || pkg?.dependencies?.["@wordpress/scripts"]),
    lintableJs: lintable.count,
    composerAutoload: Boolean(composer?.autoload),
  };
}

function docsFor(wpRoot, components) {
  const rootCatalog = isFile(path.join(wpRoot, "docs", "catalog.md"));
  const required = [{ id: "docs/catalog.md", present: rootCatalog, scope: "root" }];
  const recommended = [
    { id: "docs/human-overview.md", present: isFile(path.join(wpRoot, "docs", "human-overview.md")), scope: "root" },
  ];
  for (const c of components) {
    const rel = `${c.path}/docs/catalog.md`;
    required.push({ id: rel, present: isFile(path.join(wpRoot, c.path, "docs", "catalog.md")), scope: c.path });
  }
  return { required, recommended };
}

function buildSkillReport(catalog, flags, installed) {
  const requiredSpec = [
    ...catalog.alwaysRequired,
    ...catalog.requiredByShape.filter((s) => flagTrue(flags, s.when)),
  ];
  const recommendedSpec = catalog.recommendedByShape.filter(
    (s) => flagTrue(flags, s.when) && s.set !== "wordpress"
  );

  const mapStatus = (spec) => {
    const hit = installed.get(spec.id);
    return {
      id: spec.id,
      source: spec.source,
      why: spec.why,
      present: skillIsPresent(spec, hit),
      locations: hit?.locations ?? [],
    };
  };

  const required = requiredSpec.map(mapStatus);
  const recommended = recommendedSpec.map(mapStatus);
  const wordpressSet = [];
  const seenSet = new Set();
  for (const spec of catalogRows(catalog)) {
    if (spec.set !== "wordpress" || seenSet.has(spec.id)) continue;
    seenSet.add(spec.id);
    wordpressSet.push(mapStatus(spec));
  }
  return {
    required: {
      present: required.filter((s) => s.present),
      missing: required.filter((s) => !s.present),
    },
    recommended: {
      present: recommended.filter((s) => s.present),
      missing: recommended.filter((s) => !s.present),
    },
    wordpressSet: {
      present: wordpressSet.filter((s) => s.present),
      missing: wordpressSet.filter((s) => !s.present),
    },
    unavailable: catalog.unavailable ?? [],
  };
}

function main() {
  const args = parseArgs(process.argv);
  const wpRoot = resolveWpRoot(args.root);
  const catalog = readJsonSafe(path.join(SKILL_ROOT, "references", "skill-catalog.json"));
  if (!catalog) {
    process.stderr.write("Missing references/skill-catalog.json\n");
    process.exit(1);
  }

  const git = gitFirstParty(wpRoot);
  const candidates = listCandidates(wpRoot);
  const userPicks = resolveUserFirstParty(wpRoot, args.firstParty, args.prefixes, candidates);
  let source = "git";
  let needsConfirmation = false;
  let components = git.components;

  if (!git.available) {
    if (userPicks.length) {
      source = args.prefixes.length ? "user-prefix" : "user";
      components = userPicks;
    } else {
      source = "needs-user";
      needsConfirmation = true;
      components = [];
    }
  }

  const flags = shapeFlags(wpRoot, components);
  const kind = classifyKind(flags);
  const installed = listInstalledSkills(wpRoot);
  const skills = buildSkillReport(catalog, flags, installed);
  const docs = docsFor(wpRoot, components);
  const qa = components.map((c) => qaInComponent(wpRoot, c));

  const missingRuntime = [];
  const runtime = {
    php: hasBin("php"),
    composer: hasBin("composer"),
    node: hasBin("node"),
    git: hasBin("git"),
    wpCli: hasBin("wp"),
    phpcs: hasBin("phpcs"),
  };
  for (const [k, ok] of Object.entries(runtime)) {
    if (!ok) missingRuntime.push(k);
  }

  const phpcsTool = inspectPhpcs(wpRoot);
  const phpstanTool = inspectPhpstan(wpRoot);
  const jsLintTool = inspectJsLint(wpRoot);
  const phpunitTool = inspectPhpunit(wpRoot, components);
  const jestTool = inspectJest(wpRoot);
  const openspecTool = inspectOpenspec(wpRoot);
  const cursorRules = inspectCursorRules(wpRoot);
  const wpScriptsUnlinted = qa.some((q) => q.wpScripts && !q.lintJs);
  const sourceJsNeedsTools = qa.some((q) => !q.wpScripts && q.lintableJs > 0) && !jsLintTool.present;
  const sourceJs = qa.some((q) => q.wpScripts || q.lintableJs > 0);
  const firstPartyQaMissing = {
    phpcsConfig: qa.length > 0 && !phpcsTool.config.path && qa.every((q) => !q.phpcs),
    phpstan: qa.length > 0 && !phpstanTool.config.path && qa.every((q) => !q.phpstan),
    lintJs: wpScriptsUnlinted || sourceJsNeedsTools,
    phpunit: qa.length > 0 && !phpunitTool.present,
    jest: sourceJs && !jestTool.present && qa.every((q) => !q.jest),
    wpUnitTestCase: qa.length > 0 && !phpunitTool.wpUnitTestCase.present,
  };

  const report = {
    tool: { name: "wp-agent-harness-audit", version: TOOL_VERSION },
    wpRoot,
    kind,
    flags,
    firstParty: {
      source,
      needsConfirmation,
      components,
      candidates: git.available ? [] : candidates,
    },
    runtime: { ...runtime, missing: missingRuntime },
    skills,
    docs: {
      required: docs.required,
      recommended: docs.recommended,
      knowledge: docs.required.every((d) => d.present) ? "ok" : "degraded",
    },
    qa: {
      deferred: needsConfirmation,
      phpcs: phpcsTool,
      phpstan: phpstanTool,
      jsLint: jsLintTool,
      phpunit: phpunitTool,
      jest: jestTool,
      wpUnitTestCase: phpunitTool.wpUnitTestCase,
      components: qa,
      missing: firstPartyQaMissing,
    },
    openspec: openspecTool,
    rules: cursorRules,
    notes: [
      "Install nothing from this report. The skill must ask the user and pass --confirm with an explicit skill list.",
      "PHPCS+WPCS are team-global on PATH. Do not composer require them in a theme/plugin. Commit phpcs.xml.dist in the repo.",
      "PHPStan lives in tools/phpstan (Composer). Do not composer require phpstan in a theme/plugin. Commit phpstan.neon.dist and phpstan-baseline.neon.",
      "JS lint: wp-scripts lint:js where that package exists. Other first-party JS uses tools/js-lint (ESLint). Skip hashed, minified, vendor, and build files.",
      "PHPUnit lives in tools/phpunit (Composer). Do not composer require PHPUnit in a theme/plugin. Commit phpunit.xml.dist. Tests go in <component>/tests/.",
      "WP_UnitTestCase is a WordPress integration-test layer, not a standalone package. Prefer wp-phpunit/wp-phpunit plus Yoast PHPUnit Polyfills in tools/phpunit, an explicit bootstrap, and a dedicated test database.",
      "Jest lives in tools/js-test. Do not add Jest to each plugin package.json.",
      "OpenSpec CLI is team-global on PATH. Do not add @fission-ai/openspec to a theme/plugin package.json. Repo shares the openspec/ folder.",
      firstPartyQaMissing.phpunit
        ? "No runnable PHPUnit suite. Ask to configure tools/phpunit + phpunit.xml.dist, or install dependencies for the existing config. Not now is valid."
        : null,
      firstPartyQaMissing.jest
        ? "No Jest toolbox. Ask to write tools/js-test for first-party JS utilities. Not now is valid."
        : null,
      firstPartyQaMissing.wpUnitTestCase
        ? "No WordPress integration test bootstrap providing WP_UnitTestCase. Offer wp-phpunit/wp-phpunit + Yoast PHPUnit Polyfills + bootstrap + a dedicated test database as a separate optional layer. Not now is valid."
        : null,
      openspecTool.missing.cli
        ? "openspec CLI is not on PATH. Ask to install globally (npm install -g @fission-ai/openspec@latest). Requires Node 20.19.0+. Not now is valid."
        : null,
      openspecTool.missing.init
        ? "No OpenSpec project folder. Ask Enable OpenSpec in this repo (openspec init --tools cursor writes official skills to .cursor/skills, then pin disable-model-invocation in the same turn). If the CLI is also missing, install it first. Not now is valid."
        : null,
      openspecTool.missing.skills
        ? "openspec/ exists but no official openspec-* skills in .cursor/skills or .agents/skills. Ask Enable OpenSpec (re-run openspec init --tools cursor, then pin). Cursor init does not copy those skills into .agents/skills."
        : null,
      !openspecTool.missing.init && !openspecTool.missing.skills && openspecTool.skills.present.length
        ? `Official OpenSpec skills are in ${[...new Set(openspecTool.skills.present.map((s) => s.location))].join(" and ")}. Cursor init uses .cursor/skills, not .agents/skills.`
        : null,
      openspecTool.missing.pin
        ? "Official OpenSpec skills are present but not pinned. Ask Pin official OpenSpec skills (disable-model-invocation) so they do not auto-invoke on ordinary coding. After Yes, disable-openspec-auto-invoke.mjs --confirm --root. Not now is valid."
        : null,
      cursorRules.missing.qaLoop
        ? "No QA-loop Cursor rule. Ask which mode to write: task (default, run gates before finishing a coding task), every-change, or manual. Then write-cursor-rule.mjs --confirm --id qa-loop --mode <mode>."
        : `QA-loop Cursor rule is ${cursorRules.qaLoop.mode} (${cursorRules.qaLoop.file}).`,
      skills.wordpressSet.missing.length
        ? "Official WordPress/agent-skills are missing from this project. Ask Install WordPress skills set (all missing skills.wordpressSet ids) / Choose individually / Do not install WordPress skills. Offer this even with no custom plugin or theme yet. A personal ~/.cursor/skills copy does not count."
        : null,
      skills.required.missing.some((s) => s.id === "wp-agent-harness")
        ? "wp-agent-harness is not vendored in this repo. A personal ~/.cursor/skills copy does not count. Ask to run install-skills.mjs --confirm --root <wp-root> --skills wp-agent-harness (pack git, omits evals) so teammates get the same controller."
        : null,
      skills.recommended.missing.some((s) => s.id === "wp-browser-sensor")
        ? "wp-browser-sensor is not vendored in .agents/skills. After green gates, UI tasks skip the browser pass. Ask to install it with install-skills.mjs --confirm --skills wp-browser-sensor (pack git, omits evals)."
        : null,
      skills.recommended.missing.some((s) => s.id === "local-code-review")
        ? "local-code-review is not vendored in .agents/skills. After green gates, non-tiny first-party PHP/HTML/JS/CSS skips local review. Ask to install it with install-skills.mjs --confirm --skills local-code-review (pack git, omits evals)."
        : null,
      skills.recommended.missing.some((s) => s.id === "wordpress-testing")
        ? "wordpress-testing is not vendored in .agents/skills. Writing PHPUnit/Jest tests skips the writing-rule skill. Ask to install it with install-skills.mjs --confirm --skills wordpress-testing (pack git, omits evals). PHPUnit and Jest themselves are not skills."
        : null,
      skills.recommended.missing.some((s) => s.id === "wordpress-component-creation")
        ? "wordpress-component-creation is not vendored in .agents/skills. Scaffolding a new first-party theme or plugin skips house naming and bootstrap rules. Ask to install it with install-skills.mjs --confirm --skills wordpress-component-creation (pack git, omits evals)."
        : null,
      skills.recommended.missing.some((s) => s.id === "wordpress-project-creation")
        ? "wordpress-project-creation is not vendored in .agents/skills. New-project git, GitLab CI, and SSH content/DB sync skip house commands. Ask to install it with install-skills.mjs --confirm --skills wordpress-project-creation (pack git, omits evals)."
        : null,
      needsConfirmation
        ? "No git first-party map. Ask which themes/plugins the agent may change, then re-run with --first-party and/or --prefix. Do not infer first-party from disk."
        : "Third-party phpcs.xml files are ignored; only first-party components are listed.",
      !phpcsTool.onPath
        ? "phpcs is not on PATH. Ask to install globally (composer global require). Every teammate needs the same."
        : null,
      phpcsTool.onPath && !phpcsTool.wpcs
        ? "phpcs is installed but WordPress standards are missing. Ask to add wp-coding-standards/wpcs globally."
        : null,
      missingRuntime.includes("php")
        ? "PHP is not on PATH. Use the Local site shell if this is a Local WP install."
        : null,
    ].filter(Boolean),
  };

  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

main();

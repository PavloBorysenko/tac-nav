#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TINY_MAX_LINES = 15;
const THEME_TEMPLATES = new Set([
  "index.php",
  "single.php",
  "archive.php",
  "page.php",
  "header.php",
  "footer.php",
  "search.php",
  "404.php",
  "home.php",
  "front-page.php",
  "singular.php",
  "category.php",
  "tag.php",
  "author.php",
  "date.php",
  "attachment.php",
  "comments.php",
  "sidebar.php",
  "taxonomy.php",
  "embed.php",
  "searchform.php",
]);

function parseArgs(argv) {
  const args = { root: process.cwd(), firstParty: [], help: false };
  for (let i = 2; i < argv.length; i += 1) {
    if (argv[i] === "--help" || argv[i] === "-h") args.help = true;
    else if (argv[i] === "--root" && argv[i + 1]) args.root = argv[++i];
    else if (argv[i] === "--first-party" && argv[i + 1]) {
      args.firstParty = argv[++i]
        .split(",")
        .map((item) => posix(item.trim()).replace(/\/$/, ""))
        .filter(Boolean);
    }
  }
  return args;
}

function posix(rel) {
  return rel.replaceAll("\\", "/");
}

function git(root, gitArgs) {
  const result = spawnSync("git", gitArgs, {
    cwd: root,
    encoding: "utf8",
    windowsHide: true,
  });
  if (result.error) {
    process.stderr.write(`${result.error.message}\n`);
    process.exit(2);
  }
  if (result.status !== 0) {
    process.stderr.write(result.stderr || "git failed\n");
    process.exit(result.status || 2);
  }
  return result.stdout || "";
}

function isOutOfScope(rel) {
  if (rel === "wp-config.php" || rel.startsWith("wp-config.php/")) return true;
  if (rel.startsWith("wp-admin/") || rel.startsWith("wp-includes/")) return true;
  if (rel.startsWith("wp-content/uploads/")) return true;
  if (/^wp-content\/themes\/twenty/i.test(rel)) return true;
  return false;
}

function isBundledAsset(rel) {
  const base = path.posix.basename(rel);
  if (/\.min\.(js|css)$/.test(base)) return true;
  if (/\/(build|dist|node_modules|vendor)\//.test(rel)) return true;
  if (/\.[a-f0-9]{8,}\.(js|css)$/.test(base)) return true;
  return false;
}

function fileKind(rel) {
  const base = path.posix.basename(rel);
  if (rel.endsWith(".php") || base === "block.json") return "php";
  if (rel.endsWith(".html")) return "html";
  if (/\.(js|jsx|mjs|cjs|ts|tsx)$/.test(rel)) return "js";
  if (/\.(css|scss)$/.test(rel)) return "css";
  return "";
}

function isReviewablePath(rel) {
  const kind = fileKind(rel);
  if (!kind) return false;
  if ((kind === "js" || kind === "css") && isBundledAsset(rel)) return false;
  return true;
}

function isHtmlTemplate(rel) {
  return rel.endsWith(".html") && /\/(templates|parts|patterns)\//.test(rel);
}

function chooseRead(files) {
  const kinds = new Set(files.map(fileKind));
  const read = [];
  if (kinds.has("php")) read.push("references/php.md");
  if (kinds.has("html")) read.push("references/html.md");
  if (kinds.has("js")) read.push("references/js.md");
  if (kinds.has("css")) read.push("references/css.md");
  return read;
}

function isFirstParty(rel, prefixes) {
  if (isOutOfScope(rel)) return false;
  if (prefixes.length > 0) {
    return prefixes.some((prefix) => rel === prefix || rel.startsWith(`${prefix}/`));
  }
  return /^wp-content\/(themes|plugins)\/[^/]+\//.test(rel);
}

function isTemplate(rel) {
  const base = path.posix.basename(rel);
  if (!rel.endsWith(".php") || base === "functions.php") return false;
  if (THEME_TEMPLATES.has(base)) return true;
  if (/\/(template-parts|templates|block-templates|parts)\//.test(rel)) return true;
  return /^wp-content\/themes\/[^/]+\/[^/]+\.php$/.test(rel);
}

function collectChanged(root) {
  const nameStatus = git(root, ["diff", "HEAD", "--name-status", "-z"]);
  const entries = new Map();
  const parts = nameStatus.split("\0").filter(Boolean);
  for (let i = 0; i < parts.length; i += 1) {
    const statusToken = parts[i];
    const status = statusToken[0];
    const src = posix(parts[i + 1] || "");
    let dest = src;
    let consumed = 1;
    if ((status === "R" || status === "C") && parts[i + 2]) {
      dest = posix(parts[i + 2]);
      consumed = 2;
    }
    i += consumed;
    if (!dest) continue;
    entries.set(dest, { rel: dest, status, added: status === "A" });
  }

  const untracked = git(root, ["ls-files", "--others", "--exclude-standard", "-z"]);
  for (const rel of untracked.split("\0").filter(Boolean).map(posix)) {
    if (!entries.has(rel)) entries.set(rel, { rel, status: "A", added: true });
  }
  return [...entries.values()];
}

function numstatMap(root) {
  const map = new Map();
  for (const line of git(root, ["diff", "HEAD", "--numstat"]).split("\n")) {
    const match = line.match(/^(\d+|-)\t(\d+|-)\t(.+)$/);
    if (!match) continue;
    const added = match[1] === "-" ? 0 : Number(match[1]);
    const removed = match[2] === "-" ? 0 : Number(match[2]);
    map.set(posix(match[3]), added + removed);
  }
  return map;
}

function fileText(root, rel, added) {
  const abs = path.join(root, ...rel.split("/"));
  if (added) {
    try {
      return fs.readFileSync(abs, "utf8");
    } catch {
      return "";
    }
  }
  return git(root, ["diff", "HEAD", "--", rel]);
}

const PERSIST_RE =
  /\b(?:sanitize_\w+|update_option|add_option|delete_option|update_post_meta|add_post_meta|delete_post_meta|update_user_meta|wp_update_post|wp_insert_post|wp_delete_post)\b/;
const MARKUP_TAG_RE =
  /<(?:form|div|table|tbody|thead|tr|td|th|label|input|textarea|select|option|button|fieldset|legend|section|nav|ul|ol|li|h[1-6]|p|span|article)\b/gi;

function readCurrent(root, rel) {
  const abs = path.join(root, ...rel.split("/"));
  try {
    return fs.readFileSync(abs, "utf8");
  } catch {
    return "";
  }
}

function countMarkupTags(text) {
  MARKUP_TAG_RE.lastIndex = 0;
  return (text.match(MARKUP_TAG_RE) || []).length;
}

function countMarkupLines(text) {
  let htmlLines = 0;
  for (const line of text.split(/\r?\n/)) {
    MARKUP_TAG_RE.lastIndex = 0;
    if (MARKUP_TAG_RE.test(line) || /<<<['"]?(?:HTML|FORM|MARKUP)/i.test(line)) {
      htmlLines += 1;
    }
  }
  return htmlLines;
}

function hasLargeMarkup(text) {
  if (/<<<['"]?(?:HTML|FORM|MARKUP)/i.test(text)) return true;
  return countMarkupTags(text) >= 3 || countMarkupLines(text) >= 3;
}

function detectMixedMarkup(current) {
  return Boolean(current) && PERSIST_RE.test(current) && hasLargeMarkup(current);
}

function detectSignals(rel, meta, text, current) {
  const signals = [];
  if (meta.added) signals.push("new-file");
  if (path.posix.basename(rel) === "functions.php") signals.push("functions-php");
  if (isTemplate(rel)) signals.push("template");
  if (isHtmlTemplate(rel)) signals.push("html-template");
  if (meta.added && (/^\s*class\s+\w+/m.test(text) || /(?:^|\/)class-[^/]+\.php$/.test(rel))) {
    signals.push("new-class");
  }
  if (/\b(?:\$wpdb|SELECT\s+|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM)\b/i.test(text)) {
    signals.push("sql");
  }
  if (
    /\b(?:echo|print)\b/.test(text) &&
    /(?:<\w+|\$?_(?:GET|POST|REQUEST|COOKIE))/.test(text)
  ) {
    signals.push("echo-html");
  } else if (/\$_(?:GET|POST|REQUEST)\b/.test(text) && /\b(?:echo|print)\b/.test(text)) {
    signals.push("echo-html");
  }
  if (/\b(?:register_rest_route|register_post_type|register_taxonomy)\s*\(/.test(text)) {
    signals.push("rest-cpt-hook");
  }
  if (hasNonEnglishComment(text, meta.added)) {
    signals.push("non-english-comment");
  }
  if (rel.endsWith(".php") && detectMixedMarkup(current)) {
    signals.push("mixed-markup");
  }
  return signals;
}

function hasNonEnglishComment(text, addedFile) {
  for (const line of text.split(/\r?\n/)) {
    let src = line;
    if (!addedFile) {
      if (!line.startsWith("+") || line.startsWith("+++")) continue;
      src = line.slice(1);
    }
    const trimmed = src.trim();
    const isComment =
      trimmed.startsWith("//") ||
      trimmed.startsWith("/*") ||
      trimmed.startsWith("*") ||
      trimmed.startsWith("<!--") ||
      /\/\/.*[\u0400-\u04FF]/.test(src);
    if (isComment && /[\u0400-\u04FF]/.test(src)) return true;
  }
  return false;
}

function unique(items) {
  return [...new Set(items)];
}

function chooseMode({ files, outOfScope, signals, lineCount }) {
  if (outOfScope.length > 0) return "full";
  const structural = [
    "new-file",
    "functions-php",
    "template",
    "html-template",
    "new-class",
    "rest-cpt-hook",
    "mixed-markup",
  ];
  if (signals.some((item) => structural.includes(item)) || files.length > 1) return "full";
  const risky =
    signals.includes("sql") ||
    signals.includes("echo-html") ||
    signals.includes("non-english-comment");
  if (files.length === 1 && lineCount <= TINY_MAX_LINES) return risky ? "short" : "skip";
  if (files.length === 1) return risky || lineCount > TINY_MAX_LINES ? "short" : "skip";
  return "full";
}

function printHelp() {
  process.stdout.write(`Classify a first-party WordPress PHP / HTML / JS / CSS diff for local-code-review.

Usage:
  node scripts/review-scope.mjs --root "/absolute/path/to/wp-root"
  node scripts/review-scope.mjs --root "/absolute/wp-root" --first-party "wp-content/themes/acme-theme,wp-content/plugins/acme-hours"

Prints JSON: mode (skip|short|full), files[], outOfScope[], signals[], read[] (references/php.md, html.md, js.md, css.md).
mode skip = one existing file, <= ${TINY_MAX_LINES} lines, no structural, SQL/HTML, mixed-markup, or non-english-comment signals.
read[] is empty on skip. Does not invoke an LLM. Requires git.
`);
}

function main() {
  const args = parseArgs(process.argv);
  if (args.help) {
    printHelp();
    return;
  }

  const root = path.resolve(args.root);
  if (!fs.existsSync(path.join(root, ".git"))) {
    process.stderr.write("review-scope.mjs needs a git repository at --root.\n");
    process.exit(2);
  }

  const changed = collectChanged(root);
  const stats = numstatMap(root);
  const files = [];
  const outOfScope = [];
  const signals = [];
  let lineCount = 0;

  for (const meta of changed) {
    const rel = meta.rel;
    if (isOutOfScope(rel)) {
      outOfScope.push(rel);
      continue;
    }
    if (!isReviewablePath(rel) || !isFirstParty(rel, args.firstParty)) continue;
    files.push(rel);
    const text = fileText(root, rel, meta.added);
    const current = meta.added ? text : readCurrent(root, rel);
    signals.push(...detectSignals(rel, meta, text, current));
    if (meta.added) {
      lineCount += text.split(/\r?\n/).filter(Boolean).length;
    } else {
      lineCount += stats.get(rel) || 0;
    }
  }

  const mode = chooseMode({
    files,
    outOfScope,
    signals: unique(signals),
    lineCount,
  });
  const result = {
    mode,
    files,
    outOfScope,
    signals: unique(signals),
    read: mode === "skip" ? [] : chooseRead(files),
  };
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

main();

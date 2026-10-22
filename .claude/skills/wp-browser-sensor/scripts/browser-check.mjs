#!/usr/bin/env node
// Autonomous browser check for a local WordPress page. No prompts, no approvals.
// Launches the machine's own Chrome/Edge headless through playwright-core with a
// persistent profile, so a WordPress login made once with --login is reused.
// Prints one JSON report on stdout. Exit codes: 0 PASS, 1 FINDINGS (console errors
// or 404s on this site's own files: fix them), 3 BLOCKED (login / forbidden),
// 4 NOT CHECKED (no browser, unreachable, cert), 5 wrong host (not WordPress),
// 2 bad arguments.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

const VIEWPORTS = {
  narrow: { width: 375, height: 812 },
  wide: { width: 1280, height: 800 },
};

function parseArgs(argv) {
  const a = {
    url: "", root: process.cwd(), login: false, viewports: "single", profile: "",
    chrome: process.env.CHROME_PATH || "", screenshots: "", timeout: 25000, settle: 1500,
  };
  for (let i = 2; i < argv.length; i += 1) {
    const k = argv[i];
    const v = () => argv[++i];
    if (k === "--url") a.url = v();
    else if (k === "--root") a.root = v();
    else if (k === "--login") a.login = true;
    else if (k === "--viewports") a.viewports = v();
    else if (k === "--profile") a.profile = v();
    else if (k === "--chrome") a.chrome = v();
    else if (k === "--screenshots") a.screenshots = v();
    else if (k === "--timeout") a.timeout = Number(v());
    else if (k === "--settle") a.settle = Number(v());
    else if (k === "--help" || k === "-h") a.help = true;
  }
  return a;
}

function out(report, code) {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  process.exit(code);
}

function isLocalHost(h) {
  return h === "localhost" || h === "[::1]" || /^127\./.test(h) || /\.(local|test|localhost)$/.test(h);
}

// Local URL differs per developer, so it is never read from this pack: a flag, a
// per-machine local-site.json (next to app/public or in the repo root, gitignored),
// a "Local URL:" line in CLAUDE.md, or WP_HOME/WP_SITEURL in wp-config.php.
export function resolveLocalSite(root) {
  const read = (f) => { try { return fs.readFileSync(f, "utf8"); } catch { return ""; } };
  for (const f of [path.join(root, "local-site.json"), path.join(root, "..", "local-site.json")]) {
    try {
      const u = JSON.parse(read(f)).url;
      if (u) return { url: u, source: f };
    } catch { /* next */ }
  }
  for (const f of [path.join(root, "CLAUDE.md"), path.join(root, ".claude", "CLAUDE.md")]) {
    const m = read(f).match(/^\s*Local URL:\s*(https?:\/\/\S+)/im);
    if (m) return { url: m[1], source: f };
  }
  const cfg = read(path.join(root, "wp-config.php"));
  const m = cfg.match(/define\(\s*['"]WP_(?:HOME|SITEURL)['"]\s*,\s*['"](https?:\/\/[^'"]+)['"]/);
  if (m) return { url: m[1], source: "wp-config.php" };
  return { url: "", source: "" };
}

function loadPlaywright(root) {
  const tries = [
    () => createRequire(path.join(root, "tools", "browser-check", "package.json"))("playwright-core"),
    () => createRequire(import.meta.url)("playwright-core"),
  ];
  for (const t of tries) {
    try { return t(); } catch { /* next */ }
  }
  return null;
}

async function launch(pw, profileDir, args, headless, ignoreHTTPSErrors) {
  const base = { headless, ignoreHTTPSErrors, viewport: VIEWPORTS.wide };
  const attempts = args.chrome
    ? [{ executablePath: args.chrome }]
    : [{ channel: "chrome" }, { channel: "msedge" }, { channel: "chromium" }];
  let last = null;
  for (const extra of attempts) {
    try {
      return await pw.chromium.launchPersistentContext(profileDir, { ...base, ...extra });
    } catch (e) {
      last = e;
    }
  }
  throw last;
}

async function main() {
  const args = parseArgs(process.argv);
  if (!args.url && !args.help) {
    const found = resolveLocalSite(path.resolve(args.root));
    if (found.url) args.url = found.url;
    else out({
      status: "BLOCKED: local URL required",
      reason: "no --url, local-site.json, Local URL line in CLAUDE.md, or WP_HOME/WP_SITEURL in wp-config.php",
      fix: "node <skill>/scripts/write-local-site.mjs --confirm --root <wp-root> --url <this machine's local site URL>",
    }, 3);
  }
  if (args.help) {
    process.stderr.write("usage: browser-check.mjs [--url <url>] [--root <wp-root>] [--viewports narrow,wide|single] [--screenshots <dir>] [--login] [--profile <dir>] [--chrome <path>]\n");
    process.exit(0);
  }
  let target;
  try { target = new URL(args.url); } catch { out({ status: "ERROR", reason: `invalid url: ${args.url}` }, 2); }
  const root = path.resolve(args.root);
  const toolDir = path.join(root, "tools", "browser-check");
  const profileDir = args.profile || path.join(toolDir, ".profile");
  fs.mkdirSync(profileDir, { recursive: true });
  const giPath = path.join(toolDir, ".gitignore");
  if (!fs.existsSync(giPath)) fs.writeFileSync(giPath, ".profile/\nnode_modules/\nshots/\n");

  const pw = loadPlaywright(root);
  if (!pw) {
    out({
      status: "NOT CHECKED: browser unavailable",
      reason: "playwright-core is not installed. Ask to run: node <skill>/scripts/install-browser-check.mjs --confirm --root <wp-root>",
    }, 4);
  }

  const local = isLocalHost(target.hostname);
  let ctx;
  try {
    ctx = await launch(pw, profileDir, args, !args.login, local);
  } catch (e) {
    out({
      status: "NOT CHECKED: browser unavailable",
      reason: `no Chrome or Edge could be started: ${String(e.message).split("\n")[0]}. Pass --chrome <path> or set CHROME_PATH.`,
    }, 4);
  }

  if (args.login) {
    const page = ctx.pages()[0] || (await ctx.newPage());
    const loginUrl = new URL("/wp-admin/", target.origin).href;
    await page.goto(loginUrl).catch(() => {});
    process.stderr.write("Sign in to WordPress in the opened window. Waiting up to 5 minutes...\n");
    try {
      await page.waitForURL((u) => /\/wp-admin\//.test(u.pathname) && !/wp-login\.php/.test(u.pathname), { timeout: 300000 });
      await ctx.close();
      out({ status: "LOGGED IN", profile: profileDir }, 0);
    } catch {
      await ctx.close();
      out({ status: "BLOCKED: WordPress login required", reason: "no login completed within 5 minutes" }, 3);
    }
  }

  const names = args.viewports === "single" ? ["wide"] : args.viewports.split(",").map((s) => s.trim());
  for (const n of names) {
    if (!VIEWPORTS[n]) out({ status: "ERROR", reason: `unknown viewport ${n}` }, 2);
  }

  const page = await ctx.newPage();
  const host = target.hostname;
  const sameSite = (u) => {
    try { const h = new URL(u).hostname; return h === host || h.endsWith(`.${host}`); } catch { return false; }
  };
  const isCore = (u) => { try { return /^\/(wp-includes|wp-admin)\//.test(new URL(u).pathname); } catch { return false; } };

  let cur;
  const fresh = () => ({ consoleErrors: [], pageErrors: [], ownFailures: [], coreFailures: [], thirdParty: 0 });
  page.on("console", (m) => {
    if (m.type() !== "error") return;
    const t = m.text();
    if (/^Failed to load resource/.test(t)) return; // reported from responses
    const loc = m.location()?.url || "";
    if (loc && !sameSite(loc)) { cur.thirdParty += 1; return; }
    cur.consoleErrors.push({ text: t.slice(0, 300), source: loc });
  });
  page.on("pageerror", (e) => cur.pageErrors.push(String(e.message || e).slice(0, 300)));
  page.on("response", (r) => {
    if (r.status() < 400) return;
    const u = r.url();
    if (!sameSite(u)) { cur.thirdParty += 1; return; }
    (isCore(u) ? cur.coreFailures : cur.ownFailures).push({ url: u, status: r.status() });
  });
  page.on("requestfailed", (rq) => {
    const err = rq.failure()?.errorText || "";
    if (/ERR_ABORTED/.test(err)) return;
    const u = rq.url();
    if (!sameSite(u)) { cur.thirdParty += 1; return; }
    (isCore(u) ? cur.coreFailures : cur.ownFailures).push({ url: u, failure: err });
  });

  const report = {
    status: "PASS", ok: true, url: target.href, finalUrl: "", wordpress: null,
    adminBar: null, loggedInProfile: null, viewports: [],
  };
  if (args.screenshots) fs.mkdirSync(args.screenshots, { recursive: true });

  for (const name of names) {
    cur = fresh();
    await page.setViewportSize(VIEWPORTS[name]);
    let resp;
    try {
      resp = await page.goto(target.href, { waitUntil: "load", timeout: args.timeout });
    } catch (e) {
      const m = String(e.message);
      await ctx.close();
      if (/ERR_CERT|SSL|certificate/i.test(m)) out({ status: "NOT CHECKED: certificate not trusted", reason: m.split("\n")[0] }, 4);
      out({ status: "NOT CHECKED: site unreachable", reason: m.split("\n")[0] }, 4);
    }
    await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(args.settle);
    report.finalUrl = page.url();
    const docStatus = resp ? resp.status() : null;
    const onLogin = /wp-login\.php/.test(report.finalUrl) && !/wp-login\.php/.test(target.pathname);
    if (onLogin) {
      await ctx.close();
      out({
        status: "BLOCKED: WordPress login required",
        reason: "redirected to wp-login.php; admin was not checked",
        fix: `node <skill>/scripts/browser-check.mjs --login --url ${target.origin} --root ${root}`,
        finalUrl: report.finalUrl,
      }, 3);
    }
    const info = await page.evaluate(() => ({
      api: !!document.querySelector('link[rel="https://api.w.org/"]'),
      bar: !!document.getElementById("wpadminbar"),
    }));
    report.adminBar = info.bar;
    if (docStatus === 403) {
      await ctx.close();
      out({ status: "BLOCKED: authenticated page forbidden", reason: "document returned HTTP 403", adminBar: info.bar, finalUrl: report.finalUrl }, 3);
    }
    if (report.wordpress === null) {
      let wp = info.api;
      if (!wp) {
        try {
          const j = await ctx.request.get(new URL("/wp-json/", target.origin).href, { timeout: 8000 });
          wp = j.ok() && /namespaces/.test(await j.text());
        } catch { wp = false; }
      }
      report.wordpress = wp;
      if (!wp) {
        await ctx.close();
        out({ status: "WRONG HOST", reason: "no api.w.org link and /wp-json/ did not answer; this host is not the WordPress site", finalUrl: report.finalUrl }, 5);
      }
    }
    const row = { viewport: name, size: VIEWPORTS[name], documentStatus: docStatus, ...cur };
    if (args.screenshots) {
      const f = path.join(args.screenshots, `${name}.png`);
      await page.screenshot({ path: f, fullPage: false });
      row.screenshot = f;
    }
    report.viewports.push(row);
  }

  report.loggedInProfile = report.adminBar;
  report.ok = report.viewports.every((v) => !v.consoleErrors.length && !v.pageErrors.length && !v.ownFailures.length);
  report.status = report.ok ? "PASS" : "FINDINGS";
  await ctx.close();
  out(report, report.ok ? 0 : 1);
}

main().catch((e) => out({ status: "NOT CHECKED: browser unavailable", reason: String(e.message || e).split("\n")[0] }, 4));

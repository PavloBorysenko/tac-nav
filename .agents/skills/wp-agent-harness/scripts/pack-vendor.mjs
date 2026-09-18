#!/usr/bin/env node
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";

export const SKIP = new Set([".git", "node_modules", "evals"]);

export function copyTree(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const ent of fs.readdirSync(src, { withFileTypes: true })) {
    if (SKIP.has(ent.name)) continue;
    const from = path.join(src, ent.name);
    const to = path.join(dest, ent.name);
    if (ent.isDirectory()) copyTree(from, to);
    else fs.copyFileSync(from, to);
  }
}

export function localPackSource(skillRoot, id) {
  if (path.basename(skillRoot) === id && isSkillDir(skillRoot)) return skillRoot;
  const sibling = path.resolve(skillRoot, "..", id);
  if (isSkillDir(sibling)) return sibling;
  return null;
}

function isSkillDir(dir) {
  return fs.existsSync(path.join(dir, "SKILL.md"));
}

function gitClonePack(pack) {
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "wp-skills-pack-"));
  const args = ["clone", "--depth", "1", "--filter=blob:none", "--sparse"];
  if (pack.ref) {
    args.push("--branch", pack.ref);
  }
  args.push(pack.git, dest);
  const result = spawnSync("git", args, { encoding: "utf8" });
  if (result.status !== 0) {
    fs.rmSync(dest, { recursive: true, force: true });
    return { error: (result.stderr || result.stdout || "git clone failed").trim() };
  }
  return { dest };
}

function sparseSkills(cloneDest, pack, ids) {
  const paths = ids.map((id) => `${pack.pathPrefix}/${id}`.replaceAll("\\", "/"));
  spawnSync("git", ["-C", cloneDest, "sparse-checkout", "set", "--cone", ...paths], {
    encoding: "utf8",
  });
}

function skillDirInClone(cloneDest, pack, id) {
  return path.join(cloneDest, pack.pathPrefix, id);
}

export function vendorPackSkills({ skillRoot, wpRoot, catalog, ids }) {
  const pack = catalog.pack;
  if (!pack || !pack.git || !pack.pathPrefix) {
    throw new Error("skill-catalog.json is missing pack.git and pack.pathPrefix");
  }

  const local = [];
  const remote = [];
  for (const id of ids) {
    const src = localPackSource(skillRoot, id);
    if (src) local.push({ id, src, via: "local" });
    else remote.push(id);
  }

  const destRoot = path.join(path.resolve(wpRoot), ".agents", "skills");
  const wrote = [];

  for (const item of local) {
    wrote.push(writeSkill(item.id, item.src, destRoot, item.via));
  }

  if (remote.length === 0) return wrote;

  const clone = gitClonePack(pack);
  if (clone.error) {
    throw new Error(
      `Cannot fetch ${remote.join(", ")} from ${pack.git}: ${clone.error}. Push the skills pack or run the copy from the authoring result/ folder.`
    );
  }
  try {
    sparseSkills(clone.dest, pack, remote);
    for (const id of remote) {
      const src = skillDirInClone(clone.dest, pack, id);
      if (!isSkillDir(src)) {
        throw new Error(`Pack git has no ${pack.pathPrefix}/${id}/SKILL.md at ${pack.ref || "HEAD"}`);
      }
      wrote.push(writeSkill(id, src, destRoot, "git"));
    }
  } finally {
    fs.rmSync(clone.dest, { recursive: true, force: true });
  }

  return wrote;
}

function writeSkill(id, src, destRoot, via) {
  const dest = path.join(destRoot, id);
  if (path.resolve(dest) === path.resolve(src)) {
    throw new Error(`Refusing: destination is this skill pack (${id}).`);
  }
  copyTree(src, dest);
  process.stdout.write(`Wrote ${dest} (${via}, without evals)\n`);
  return dest;
}

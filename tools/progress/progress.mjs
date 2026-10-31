#!/usr/bin/env node
// Suivi d'avancement du projet (docs/D-orchestration-backend.md D9), sans dépendance.
//
//   node tools/progress/progress.mjs add --task M0-02 --agent backend-module-dev \
//        --status done --summary "..." [--notes "..."] [--pr URL] [--branch X] [--next "..."]
//   node tools/progress/progress.mjs build
//
// Principe anti-collision : chaque appel `add` écrit UN nouveau fichier JSON au nom unique
// dans docs/progress/events/ (écriture atomique : fichier temporaire puis rename).
// Personne ne modifie jamais un fichier existant, donc deux agents qui terminent au même
// instant ne se gênent pas, et les fichiers fusionnent sans conflit entre branches/worktrees.
// La page docs/progress/index.html est dérivée (non commitée) : elle est regénérée à partir
// de tous les événements (dépôt principal + chaque worktree), sous un verrou `mkdir`.

import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CHECKOUT = path.resolve(HERE, "..", "..");
const MAIN_ROOT = findMainRoot(CHECKOUT);
const PROGRESS_DIR = "docs/progress";
const STATUSES = ["todo", "in-progress", "review", "blocked", "done", "merged"];
const STATUS_ALIASES = {
  "a-faire": "todo", "en-cours": "in-progress", wip: "in-progress", revue: "review",
  bloquee: "blocked", bloquée: "blocked", terminee: "done", terminée: "done",
  fini: "done", fusionnee: "merged", fusionnée: "merged", merge: "merged",
};

main();

function main() {
  const [cmd = "build", ...rest] = process.argv.slice(2);
  if (cmd === "add") {
    const event = add(parseArgs(rest));
    console.log(`événement enregistré : ${event.file}`);
    buildWithLock();
  } else if (cmd === "build") {
    buildWithLock();
  } else {
    console.error("usage : progress.mjs add --task ID --agent NOM --status STATUT --summary TEXTE | build");
    process.exit(2);
  }
}

// ---------- add ----------

function add(args) {
  const task = (args.task || "").trim().toUpperCase();
  const agent = (args.agent || "").trim();
  const status = normalizeStatus(args.status);
  const summary = (args.summary || "").trim();
  const problems = [];
  if (!/^(M\d-\d{2}[A-Z]?|PROJET)$/.test(task)) problems.push("--task doit être un ID de tâche (ex. M0-02) ou PROJET");
  if (!agent) problems.push("--agent est obligatoire (ex. backend-module-dev, reviewer, claude)");
  if (!status) problems.push(`--status doit être l'un de : ${STATUSES.join(", ")}`);
  if (!summary) problems.push("--summary est obligatoire");
  if (problems.length) {
    console.error(problems.join("\n"));
    process.exit(2);
  }
  const at = args.at ? new Date(args.at).toISOString() : new Date().toISOString();
  const event = {
    at,
    task,
    agent,
    status,
    summary,
    notes: (args.notes || "").trim() || undefined,
    next: (args.next || "").trim() || undefined,
    pr: (args.pr || "").trim() || undefined,
    branch: (args.branch || "").trim() || gitBranch(CHECKOUT) || undefined,
    checkout: path.basename(CHECKOUT),
  };
  const dir = path.join(CHECKOUT, PROGRESS_DIR, "events");
  fs.mkdirSync(dir, { recursive: true });
  const stamp = at.replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z");
  const name = `${stamp}-${task}-${process.pid}-${Math.random().toString(36).slice(2, 8)}.json`;
  const tmp = path.join(dir, `.${name}.tmp`);
  fs.writeFileSync(tmp, JSON.stringify(event, null, 2) + "\n");
  fs.renameSync(tmp, path.join(dir, name)); // atomique : le fichier apparaît complet ou pas du tout
  return { ...event, file: path.relative(CHECKOUT, path.join(dir, name)) };
}

// ---------- build ----------

function buildWithLock() {
  const outDir = path.join(MAIN_ROOT, PROGRESS_DIR);
  fs.mkdirSync(outDir, { recursive: true });
  const lock = path.join(outDir, ".lock");
  acquire(lock);
  try {
    // Si des événements arrivent pendant la génération, on recommence (au plus 3 fois).
    let previous = null;
    for (let i = 0; i < 3; i++) {
      const events = collectEvents();
      const key = events.map((e) => e.file).join("\n");
      if (key === previous) break;
      previous = key;
      writeAtomic(path.join(outDir, "index.html"), render(events, loadPlan()));
    }
  } finally {
    try { fs.rmdirSync(lock); } catch {}
  }
  console.log(`page regénérée : ${path.join(outDir, "index.html")}`);
}

function acquire(lock) {
  const deadline = Date.now() + 15_000;
  for (;;) {
    try {
      fs.mkdirSync(lock); // atomique sur tous les systèmes
      return;
    } catch (e) {
      if (e.code !== "EEXIST") throw e;
      try {
        if (Date.now() - fs.statSync(lock).mtimeMs > 30_000) { fs.rmdirSync(lock); continue; } // verrou abandonné
      } catch {}
      if (Date.now() > deadline) throw new Error(`verrou ${lock} toujours pris après 15 s`);
      sleep(100 + Math.floor(Math.random() * 100));
    }
  }
}

function writeAtomic(file, content) {
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, content);
  fs.renameSync(tmp, file);
}

function collectEvents() {
  const roots = [MAIN_ROOT];
  const wt = path.join(MAIN_ROOT, ".claude", "worktrees");
  if (fs.existsSync(wt)) {
    for (const d of fs.readdirSync(wt)) {
      const p = path.join(wt, d);
      if (fs.statSync(p).isDirectory()) roots.push(p);
    }
  }
  const seen = new Map();
  for (const root of roots) {
    const dir = path.join(root, PROGRESS_DIR, "events");
    if (!fs.existsSync(dir)) continue;
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith(".json") || seen.has(f)) continue;
      try {
        const e = JSON.parse(fs.readFileSync(path.join(dir, f), "utf8"));
        e.file = f;
        e.source = root === MAIN_ROOT ? "main" : path.basename(root);
        seen.set(f, e);
      } catch (err) {
        console.error(`événement illisible ignoré : ${f} (${err.message})`);
      }
    }
  }
  return [...seen.values()].sort((a, b) => a.at.localeCompare(b.at) || a.file.localeCompare(b.file));
}

// ---------- plan (docs) ----------

function loadPlan() {
  const tasks = new Map();
  const cells = (line) => line.split("|").slice(1, -1).map((c) => c.trim());
  const isTaskRow = (line) => /^\| M\d-\d{2}[a-z]? \|/i.test(line);
  const clean = (s) => s.replace(/\*\*/g, "").replace(/`/g, "");

  const plan = readDoc("docs/C-plan.md");
  let milestoneTitle = "";
  for (const line of plan.split("\n")) {
    const h = line.match(/^### (M\d) (.+)$/);
    if (h) milestoneTitle = `${h[1]} ${h[2]}`;
    if (!isTaskRow(line)) continue;
    const c = cells(line);
    if (c.length < 8) continue; // les tableaux de rappel (2 colonnes) ne comptent pas
    const id = c[0].toUpperCase();
    if (tasks.has(id)) continue;
    tasks.set(id, {
      id, title: clean(c[1]), scope: clean(c[2]), read: clean(c[3]), criteria: clean(c[5]),
      tests: clean(c[6]), dependsOn: clean(c[7]), milestone: id.slice(0, 2), milestoneTitle,
    });
  }
  const orch = readDoc("docs/D-orchestration-backend.md");
  let inD9 = false;
  for (const line of orch.split("\n")) {
    if (/^## D9/.test(line)) inD9 = true;
    else if (/^## D\d/.test(line)) inD9 = false;
    if (inD9) {
      const m = line.match(/^\| (V\d+) \| (M\d-\d{2}[a-z]?) \| ([^|]*) \| ([^|]*) \|/i);
      if (m) {
        const id = m[2].toUpperCase();
        if (!tasks.has(id)) tasks.set(id, { id, title: "", scope: "", milestone: id.slice(0, 2), milestoneTitle: "" });
        Object.assign(tasks.get(id), { wave: m[1], lane: clean(m[3].trim()), planned: clean(m[4].trim()) });
      }
    } else if (isTaskRow(line)) {
      const c = cells(line);
      const id = c[0].toUpperCase();
      if (c.length >= 4 && !tasks.has(id)) {
        tasks.set(id, { id, title: clean(c[1]), scope: clean(c[2]), dependsOn: clean(c[3]), milestone: id.slice(0, 2), milestoneTitle: "" });
      } else if (tasks.has(id) && !tasks.get(id).title) tasks.get(id).title = clean(c[1]);
    }
  }
  return [...tasks.values()].sort((a, b) => a.id.localeCompare(b.id));
}

function readDoc(rel) {
  for (const root of [CHECKOUT, MAIN_ROOT]) {
    const p = path.join(root, rel);
    if (fs.existsSync(p)) return fs.readFileSync(p, "utf8");
  }
  return "";
}

// ---------- rendu ----------

function render(events, tasks) {
  const generatedAt = new Date().toISOString();
  const data = { generatedAt, tasks, events, statuses: STATUSES };
  const json = JSON.stringify(data).replace(/<\//g, "<\\/");
  return `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PsyMate · Avancement</title>
<style>
  :root {
    --bg: #f6f7f9; --surface: #ffffff; --surface-2: #eef1f5; --border: #d9dee6;
    --text: #1b2430; --muted: #5d6b7c; --accent: #3457c9;
    --todo: #8a96a6; --in-progress: #2f7bd9; --review: #b7791f; --blocked: #c0392b; --done: #2e8b57; --merged: #1f6f43;
    color-scheme: light;
  }
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
      --bg: #12161c; --surface: #1a2029; --surface-2: #232b36; --border: #303a47;
      --text: #e6eaf0; --muted: #98a4b3; --accent: #8aa6ff;
      --todo: #7b8794; --in-progress: #6aa5ff; --review: #e0b35a; --blocked: #ff7b6e; --done: #5fc78a; --merged: #3fa86e;
      color-scheme: dark;
    }
  }
  * { box-sizing: border-box; }
  body { margin: 0; font: 15px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); }
  main { max-width: 1200px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { font-size: 1.5rem; margin: 0 0 4px; }
  h2 { font-size: 1.1rem; margin: 32px 0 12px; }
  .sub { color: var(--muted); margin: 0 0 20px; font-size: .9rem; }
  .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; }
  .tile { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; cursor: pointer; }
  .tile.active { outline: 2px solid var(--accent); }
  .tile b { display: block; font-size: 1.6rem; line-height: 1.1; }
  .tile span { color: var(--muted); font-size: .85rem; }
  .bar { display: flex; height: 10px; border-radius: 6px; overflow: hidden; background: var(--surface-2); margin: 6px 0 2px; }
  .bar i { display: block; height: 100%; }
  .milestones { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 10px; }
  .ms { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
  .ms .t { display: flex; justify-content: space-between; gap: 8px; font-weight: 600; }
  .ms small { color: var(--muted); }
  .filters { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 12px 0; }
  .filters input, .filters select { font: inherit; padding: 6px 8px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text); }
  table { width: 100%; border-collapse: collapse; background: var(--surface); border: 1px solid var(--border); border-radius: 10px; overflow: hidden; }
  th, td { text-align: left; padding: 8px 10px; border-top: 1px solid var(--border); vertical-align: top; font-size: .92rem; }
  th { background: var(--surface-2); border-top: none; font-weight: 600; cursor: pointer; white-space: nowrap; }
  tr.task { cursor: pointer; }
  tr.task:hover { background: var(--surface-2); }
  tr.detail td { background: var(--surface-2); font-size: .88rem; }
  .pill { display: inline-flex; align-items: center; gap: 6px; padding: 2px 9px; border-radius: 999px; font-size: .8rem; font-weight: 600; border: 1px solid; white-space: nowrap; }
  .pill::before { content: ""; width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
  .pill.todo { color: var(--todo); } .pill.in-progress { color: var(--in-progress); } .pill.review { color: var(--review); }
  .pill.blocked { color: var(--blocked); } .pill.done { color: var(--done); } .pill.merged { color: var(--merged); }
  .muted { color: var(--muted); } .mono { font-family: ui-monospace, Consolas, monospace; font-size: .85em; }
  a { color: var(--accent); }
  .journal { display: grid; gap: 8px; }
  .ev { background: var(--surface); border: 1px solid var(--border); border-left: 4px solid var(--border); border-radius: 8px; padding: 10px 12px; }
  .ev.todo { border-left-color: var(--todo); } .ev.in-progress { border-left-color: var(--in-progress); } .ev.review { border-left-color: var(--review); }
  .ev.blocked { border-left-color: var(--blocked); } .ev.done { border-left-color: var(--done); } .ev.merged { border-left-color: var(--merged); }
  .ev .head { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: center; font-size: .88rem; color: var(--muted); }
  .ev .head b { color: var(--text); }
  .ev p { margin: 6px 0 0; white-space: pre-wrap; }
  .ev .notes { margin-top: 6px; padding: 8px 10px; background: var(--surface-2); border-radius: 6px; white-space: pre-wrap; font-size: .9rem; }
  .ev .next { margin-top: 6px; font-size: .9rem; }
  .ev .next::before { content: "Suite : "; font-weight: 600; }
  .empty { color: var(--muted); padding: 16px; text-align: center; }
  details summary { cursor: pointer; color: var(--muted); font-size: .9rem; }
  code { background: var(--surface-2); padding: 1px 5px; border-radius: 4px; font-size: .88em; }
  @media (max-width: 720px) { th.opt, td.opt { display: none; } }
</style>
</head>
<body>
<main>
  <h1>PsyMate · Avancement</h1>
  <p class="sub">Généré le <span id="gen"></span> à partir de <span id="nev"></span> événement(s) · tâches de <code>docs/C-plan.md</code>, vagues et couloirs de <code>docs/D-orchestration-backend.md</code> D9.</p>

  <section class="tiles" id="tiles"></section>

  <h2>Jalons</h2>
  <section class="milestones" id="milestones"></section>

  <h2>Tâches</h2>
  <div class="filters">
    <input id="q" type="search" placeholder="Filtrer : ID, titre, couloir, agent…">
    <select id="fm"><option value="">Tous les jalons</option></select>
    <select id="fs"><option value="">Tous les états</option></select>
    <label><input type="checkbox" id="hideTodo"> masquer « à faire »</label>
    <span class="muted" id="count"></span>
  </div>
  <table id="tasks">
    <thead><tr>
      <th data-k="id">ID</th><th data-k="title">Titre</th><th data-k="lane" class="opt">Couloir</th><th data-k="wave" class="opt">Vague</th>
      <th data-k="status">État</th><th data-k="agent" class="opt">Agent</th><th data-k="at" class="opt">Dernière mise à jour</th><th>PR</th>
    </tr></thead>
    <tbody></tbody>
  </table>

  <h2>Journal</h2>
  <section class="journal" id="journal"></section>

  <details style="margin-top:32px">
    <summary>Comment mettre la page à jour</summary>
    <p class="muted" style="font-size:.9rem">
      Un agent qui termine (ou se bloque) exécute :<br>
      <code>make log TASK=M0-04 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=… NOTES="…" NEXT="…"</code><br>
      États : <code>todo</code>, <code>in-progress</code>, <code>review</code>, <code>blocked</code>, <code>done</code>, <code>merged</code>.
      Chaque appel écrit un nouveau fichier dans <code>docs/progress/events/</code> ; la page se regénère avec <code>make progress</code>.
    </p>
  </details>
</main>
<script>
const DATA = ${json};
const LABEL = { todo: "à faire", "in-progress": "en cours", review: "en revue", blocked: "bloquée", done: "terminée", merged: "fusionnée" };
const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const fmt = (iso) => iso ? new Date(iso).toLocaleString("fr-FR", { dateStyle: "short", timeStyle: "short" }) : "";

// Dernier événement par tâche = état courant.
const latest = new Map();
for (const e of DATA.events) latest.set(e.task, e);
const rows = DATA.tasks.map((t) => {
  const e = latest.get(t.id);
  return { ...t, status: e?.status || "todo", agent: e?.agent || "", at: e?.at || "", pr: e?.pr || "", branch: e?.branch || "", last: e };
});
for (const [id, e] of latest) if (!rows.some((r) => r.id === id) && id !== "PROJET") {
  rows.push({ id, title: "(hors plan)", lane: "", wave: "", milestone: id.slice(0, 2), status: e.status, agent: e.agent, at: e.at, pr: e.pr || "", last: e });
}
const byTask = new Map();
for (const e of DATA.events) (byTask.get(e.task) || byTask.set(e.task, []).get(e.task)).push(e);

document.getElementById("gen").textContent = fmt(DATA.generatedAt);
document.getElementById("nev").textContent = DATA.events.length;

// Tuiles
const counts = Object.fromEntries(DATA.statuses.map((s) => [s, 0]));
for (const r of rows) counts[r.status]++;
let filterStatus = "";
const tiles = document.getElementById("tiles");
const tileHtml = [["", rows.length, "tâches"], ...DATA.statuses.map((s) => [s, counts[s], LABEL[s]])];
tiles.innerHTML = tileHtml.map(([s, n, l]) => \`<div class="tile" data-s="\${s}"><b>\${n}</b><span>\${l}</span></div>\`).join("");
tiles.addEventListener("click", (ev) => {
  const t = ev.target.closest(".tile"); if (!t) return;
  filterStatus = t.dataset.s; document.getElementById("fs").value = filterStatus; draw();
});

// Jalons
const ms = new Map();
for (const r of rows) {
  const m = ms.get(r.milestone) || ms.set(r.milestone, { id: r.milestone, title: r.milestoneTitle || "", total: 0, c: {} }).get(r.milestone);
  if (r.milestoneTitle && !m.title) m.title = r.milestoneTitle;
  m.total++; m.c[r.status] = (m.c[r.status] || 0) + 1;
}
document.getElementById("milestones").innerHTML = [...ms.values()].sort((a, b) => a.id.localeCompare(b.id)).map((m) => {
  const fin = (m.c.done || 0) + (m.c.merged || 0);
  const pct = Math.round((fin / m.total) * 100);
  const bar = DATA.statuses.filter((s) => s !== "todo").map((s) => \`<i style="width:\${((m.c[s] || 0) / m.total) * 100}%;background:var(--\${s})" title="\${LABEL[s]} : \${m.c[s] || 0}"></i>\`).join("");
  return \`<div class="ms"><div class="t"><span>\${esc(m.title || m.id)}</span><span>\${pct}%</span></div><div class="bar">\${bar}</div><small>\${fin}/\${m.total} terminées · \${m.c["in-progress"] || 0} en cours · \${m.c.review || 0} en revue · \${m.c.blocked || 0} bloquées</small></div>\`;
}).join("");

// Filtres
const fm = document.getElementById("fm"), fs = document.getElementById("fs");
for (const m of [...ms.keys()].sort()) fm.insertAdjacentHTML("beforeend", \`<option value="\${m}">\${m}</option>\`);
for (const s of DATA.statuses) fs.insertAdjacentHTML("beforeend", \`<option value="\${s}">\${LABEL[s]}</option>\`);
let sortKey = "id", sortDir = 1;
for (const el of ["q", "fm", "fs", "hideTodo"]) document.getElementById(el).addEventListener("input", () => { filterStatus = fs.value; draw(); });
document.querySelectorAll("th[data-k]").forEach((th) => th.addEventListener("click", () => {
  sortDir = sortKey === th.dataset.k ? -sortDir : 1; sortKey = th.dataset.k; draw();
}));

function draw() {
  const q = document.getElementById("q").value.trim().toLowerCase();
  const hideTodo = document.getElementById("hideTodo").checked;
  document.querySelectorAll(".tile").forEach((t) => t.classList.toggle("active", t.dataset.s === filterStatus));
  const list = rows.filter((r) =>
    (!fm.value || r.milestone === fm.value) && (!filterStatus || r.status === filterStatus) && !(hideTodo && r.status === "todo") &&
    (!q || [r.id, r.title, r.lane, r.scope, r.agent, r.wave].join(" ").toLowerCase().includes(q)))
    .sort((a, b) => sortDir * String(a[sortKey] ?? "").localeCompare(String(b[sortKey] ?? ""), "fr", { numeric: true }));
  document.getElementById("count").textContent = \`\${list.length} affichée(s)\`;
  const tb = document.querySelector("#tasks tbody");
  tb.innerHTML = list.length ? list.map((r) => \`
    <tr class="task" data-id="\${r.id}">
      <td class="mono">\${r.id}</td><td>\${esc(r.title)}<div class="muted" style="font-size:.8rem">\${esc(r.scope || "")}</div></td>
      <td class="opt">\${esc(r.lane || "")}</td><td class="opt">\${esc(r.wave || "")}</td>
      <td><span class="pill \${r.status}">\${LABEL[r.status]}</span></td>
      <td class="opt">\${esc(r.agent)}</td><td class="opt">\${fmt(r.at)}</td>
      <td>\${r.pr ? \`<a href="\${esc(r.pr)}" target="_blank" rel="noopener">#\${esc(r.pr.split("/").pop())}</a>\` : ""}</td>
    </tr>\`).join("") : '<tr><td colspan="8" class="empty">Aucune tâche ne correspond.</td></tr>';
}
document.querySelector("#tasks tbody").addEventListener("click", (ev) => {
  const tr = ev.target.closest("tr.task"); if (!tr || ev.target.closest("a")) return;
  const next = tr.nextElementSibling;
  if (next?.classList.contains("detail")) { next.remove(); return; }
  const r = rows.find((x) => x.id === tr.dataset.id);
  const evs = (byTask.get(r.id) || []).slice().reverse();
  const hist = evs.length ? evs.map((e) => \`<div class="ev \${e.status}"><div class="head"><b>\${LABEL[e.status]}</b><span>\${fmt(e.at)}</span><span>\${esc(e.agent)}</span>\${e.branch ? \`<span class="mono">\${esc(e.branch)}</span>\` : ""}</div><p>\${esc(e.summary)}</p>\${e.notes ? \`<div class="notes">\${esc(e.notes)}</div>\` : ""}\${e.next ? \`<div class="next">\${esc(e.next)}</div>\` : ""}</div>\`).join("") : '<p class="muted">Aucun événement pour cette tâche.</p>';
  tr.insertAdjacentHTML("afterend", \`<tr class="detail"><td colspan="8">
    \${r.dependsOn ? \`<p><b>Dépend de :</b> \${esc(r.dependsOn)}</p>\` : ""}\${r.planned ? \`<p><b>Plan D9 :</b> \${esc(r.planned)}</p>\` : ""}
    \${r.criteria ? \`<p><b>Critères :</b> \${esc(r.criteria)}</p>\` : ""}\${r.tests ? \`<p><b>Tests :</b> \${esc(r.tests)}</p>\` : ""}
    <div class="journal">\${hist}</div></td></tr>\`);
});

// Journal global, du plus récent au plus ancien
const j = document.getElementById("journal");
const evs = DATA.events.slice().reverse();
j.innerHTML = evs.length ? evs.map((e) => \`<div class="ev \${e.status}"><div class="head"><b class="mono">\${esc(e.task)}</b><span class="pill \${e.status}">\${LABEL[e.status]}</span><span>\${fmt(e.at)}</span><span>\${esc(e.agent)}</span>\${e.branch ? \`<span class="mono">\${esc(e.branch)}</span>\` : ""}\${e.pr ? \`<a href="\${esc(e.pr)}" target="_blank" rel="noopener">PR</a>\` : ""}\${e.source && e.source !== "main" ? \`<span title="événement lu dans le worktree \${esc(e.source)}, pas encore fusionné">worktree \${esc(e.source)}</span>\` : ""}</div><p>\${esc(e.summary)}</p>\${e.notes ? \`<div class="notes">\${esc(e.notes)}</div>\` : ""}\${e.next ? \`<div class="next">\${esc(e.next)}</div>\` : ""}</div>\`).join("") : '<div class="empty">Aucun événement enregistré.</div>';

draw();
</script>
</body>
</html>
`;
}

// ---------- utilitaires ----------

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const eq = a.indexOf("=");
      if (eq > 0) out[a.slice(2, eq)] = a.slice(eq + 1);
      else out[a.slice(2)] = argv[i + 1] !== undefined && !argv[i + 1].startsWith("--") ? argv[++i] : "true";
    }
  }
  return out;
}

function normalizeStatus(s) {
  const v = (s || "").trim().toLowerCase().replace(/_/g, "-");
  const n = STATUS_ALIASES[v] || v;
  return STATUSES.includes(n) ? n : "";
}

function findMainRoot(checkout) {
  try {
    const common = execSync("git rev-parse --git-common-dir", { cwd: checkout, stdio: ["ignore", "pipe", "ignore"] }).toString().trim();
    return path.resolve(checkout, common, "..");
  } catch {
    return checkout;
  }
}

function gitBranch(cwd) {
  try { return execSync("git branch --show-current", { cwd, stdio: ["ignore", "pipe", "ignore"] }).toString().trim(); } catch { return ""; }
}

function sleep(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

# Suivi d'avancement

Mise en œuvre du tableau D9 de `docs/D-orchestration-backend.md`.

- `events/` : un fichier JSON par mise à jour, écrit une seule fois, jamais modifié (`make log …`, skill `progress-log`).
- `index.html` : page générée par `make progress`, non commitée. Elle lit les événements du dépôt principal et de chaque worktree sous `.claude/worktrees/`, ce qui montre aussi les tâches en cours sur des branches pas encore fusionnées.

Pourquoi des événements et pas un tableau édité : deux agents qui terminent au même moment écrivent chacun leur fichier, sans verrou ni conflit, et ces fichiers fusionnent proprement entre branches. La page, elle, est regénérée sous un verrou court et réécrite atomiquement.

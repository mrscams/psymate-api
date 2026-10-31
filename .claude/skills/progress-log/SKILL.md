---
name: progress-log
description: Enregistre l'avancement d'une tâche (M0-01, M1-03…) dans le suivi du projet (docs/progress, D9) à la fin d'une tâche, d'une revue, ou quand on se bloque. À utiliser par tout agent et par Claude lui-même, jamais en modifiant un fichier existant du suivi.
---

# Journal d'avancement du projet

Le suivi d'avancement de docs/D-orchestration-backend.md D9 est tenu par des **événements** :
un fichier JSON par mise à jour dans `docs/progress/events/`, et une page `docs/progress/index.html`
regénérée à partir de tous les événements (dépôt principal et worktrees).

## Quand enregistrer un événement

- À la **fin de ta tâche** : `./mvnw verify` vert et PR ouverte → `STATUS=done`, avec l'URL de la PR.
- Quand tu **passes la main au reviewer** ou qu'une revue est faite → `STATUS=review`.
- Quand tu es **bloqué** (classe d'un autre module absente, décision manquante, test rouge que tu ne dois pas corriger) → `STATUS=blocked`, avec la raison dans `NOTES` et ce qu'il faudrait dans `NEXT`.
- Au **début** d'une tâche longue, si tu veux que l'état « en cours » soit visible → `STATUS=in-progress`.
- Après la **fusion** d'une PR → `STATUS=merged` (c'est en général Claude, pas l'agent, qui le fait).

## Comment

Une seule commande, depuis la racine du dépôt ou du worktree :

```bash
make log TASK=M0-04 AGENT=backend-module-dev STATUS=done SUMMARY="Signup, login, logout, tenant ; 14 tests verts" PR=https://github.com/mrscams/psymate-api/pull/5 NOTES="Argon2 via Spring Security ; reset par e-mail vu dans Mailpit" NEXT="M0-05 peut démarrer"
```

- `TASK` : l'ID de la tâche (`M0-04`), ou `PROJET` pour une note générale.
- `AGENT` : ton nom d'agent (`backend-module-dev`, `reviewer`, `seed-perf`, `frontend-feature-dev`, `claude`).
- `STATUS` : `todo`, `in-progress`, `review`, `blocked`, `done`, `merged`.
- `SUMMARY` : une phrase, obligatoire. `NOTES` : décisions prises, écarts, dette, chiffres (tests, latences). `NEXT` : ce qui reste ou ce que ça débloque.
- Évite les guillemets doubles à l'intérieur des valeurs ; utilise des guillemets simples ou des parenthèses.

La commande écrit le fichier d'événement, puis regénère la page. Si `make` manque, l'équivalent est
`node tools/progress/progress.mjs add --task … --agent … --status … --summary …`.

## Règles

- **N'édite jamais** `docs/progress/index.html` ni un fichier existant de `docs/progress/events/` : la page est dérivée (non commitée), et chaque événement est écrit une fois avec un nom unique. C'est ce qui permet à deux agents de terminer au même instant sans se gêner, et aux événements de fusionner sans conflit entre branches.
- **Commite le fichier d'événement avec ta tâche** (`git add docs/progress/events`), pour qu'il arrive sur `main` avec la PR.
- Un événement est un constat, pas une consigne : il ne remplace ni le rapport de fin de tâche ni la PR.
- Si `make log` échoue (verrou tenu plus de 15 s, Node absent), signale-le dans ton rapport : ne tente pas d'écrire la page à la main.

## Lire l'avancement

`make progress` regénère la page ; ouvre `docs/progress/index.html` dans un navigateur
(tuiles par état, barres par jalon, tableau filtrable des tâches, journal complet).

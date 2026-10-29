# Écran 1 · Liste des patients

> Spécification pour agents (frontend-feature-dev). Maquette : `01-liste-patients.html` / `.png`. Palette : Nordique (voir `Etude-couleurs-Nordique.md`). Document B, écran 1 ; flow 1 « ouvrir le bon patient en moins de 3 secondes ».

## Objectif
Le praticien connecté retrouve un patient par la recherche ou en balayant la liste, repère ceux qui demandent attention (drapeau « à surveiller », séance en cours) et ouvre la vue d'ensemble en un clic.

## Route & module
- Route Angular : `/patients` (feature `patients`, lazy, standalone components).
- API : `GET /api/patients?q=&status=&sort=&cursor=` (module backend `patients`, pagination curseur, RFC 9457 en erreur).
- Store : `PatientsListStore` (@ngrx/signals) : `query`, `filters`, `sort`, `items`, `nextCursor`, `loading`.

## Structure de l'écran (de haut en bas)
1. **Barre d'application** (partagée) : logo + « Patients » (actif), recherche globale `/`, bascule FR/EN, avatar.
2. **En-tête de page** : titre « Patients », compteur (`N patients suivis`), bouton primaire **« Nouveau patient »** (ouvre le formulaire de création en modale).
3. **Barre d'outils** : champ de recherche (nom, initiales, tags ; `pg_trgm` + `unaccent` côté API), filtres par puces : *Tous · À surveiller · Séance en cours · Inactifs > 90 j*, tri (*Dernière séance ↓* par défaut, *Nom A→Z*, *Date de création*).
4. **Table** (colonnes) :
   | Colonne | Contenu | Note |
   |---|---|---|
   | Patient | avatar initiales + nom affiché + âge | nom = `display_name` ; pas de nom complet si pseudonymisé |
   | Statut | puce : `à surveiller` (ambre), `séance en cours` (vert), vide | drapeau porté par `patient.flag` |
   | Dernière séance | date relative + modalité (cabinet / visio / contact) | `max(session.started_at)` |
   | Prochaine séance | date ou « — » | V1 : champ manuel |
   | Tendance 30 j | mini-sparkline intensité ressentis | issue de `overview` ; peut être vide (« pas assez de données ») |
   | Tags | 2 premiers tags + `+n` | |
   | Action | icône `→` « Ouvrir » (aria-label) | toute la ligne est cliquable (`<a>` englobant) |
5. **Pied de table** : « Afficher plus » (curseur) ; jamais de pagination numérotée.
6. **État vide** : illustration simple + « Aucun patient ne correspond » + bouton *Effacer les filtres*.

## Interactions
- Clic ligne → `/patients/:id` (vue d'ensemble).
- `/` focalise la recherche ; `Entrée` ouvre le 1er résultat ; `Échap` vide la recherche.
- Recherche debounce 250 ms, min 2 caractères, surlignage des correspondances (`--search-highlight`).
- Filtre « séance en cours » : `session.ended_at IS NULL` pour le praticien courant.
- Tri persistant par praticien (localStorage, clé `patients.sort`).

## Tokens à utiliser (extraits)
- fond page `--surface-ground #EEF1F6`, cartes/table `--surface-raised #FFFFFF`, bordures `--border-subtle #D6DCE6`.
- Primaire `--primary #3A63C2`, survol `#2C4C99`, teinte `#E3EAF8`.
- Drapeau « à surveiller » `--flag #B8862E` sur `#FBF1DC` ; « séance en cours » `--success #1E6B45` sur `#E4F2EA`.
- Sparkline : trait `--feeling #8B7BD6` (ressentis uniquement), aire `#ECE8F8`.
- Texte secondaire `#5A6679` (contraste ≥ 4.5:1 sur blanc), désactivé `#8A94A6` uniquement ≥ 24 px ou décoratif.
- Police : Inter (UI), JetBrains Mono (dates/heures, identifiants).

## Accessibilité
- Table réelle `<table>` avec `<th scope="col">`, tri annoncé via `aria-sort`.
- Ligne cliquable = `<a>` sur le nom + `tabindex` cohérent ; pas de `div onClick`.
- Puces de filtre = `<button aria-pressed>`.
- Cibles ≥ 44 px ; focus visible (`outline 2px #3A63C2 offset 2px`).

## Critères d'acceptation
- [ ] Recherche « mar » trouve « Marie D. » et « Omar B. » (trigram + sans accent).
- [ ] Filtre « À surveiller » ne montre que les patients `flag = watch`.
- [ ] 500 patients : première page < 300 ms (Testcontainers, seed `seed-perf`).
- [ ] Aucune couleur codée en dur hors tokens (`eslint no-hardcoded-color`).
- [ ] Test Playwright : ouvrir un patient depuis la recherche au clavier uniquement.

## Hors périmètre V1
Tri par score IA, colonnes personnalisables, export CSV.

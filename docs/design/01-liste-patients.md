# Écran 1 · Liste des patients

> Spécification pour agents (frontend-feature-dev). Maquette : `01-liste-patients.html` / `.png`. Palette : Nordique (voir `Etude-couleurs-Nordique.md`). Document B, écran 1 ; flow 1 « ouvrir le bon patient en moins de 3 secondes ».
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : source des colonnes précisée (le contrat de la liste ne porte ni séance, ni ressentis, ni situations : synthèse `overview` par patient) ; colonne « Prochaine séance » retirée (agenda hors V1, G Q25) ; maquette HTML corrigée.

## Objectif
Le praticien connecté retrouve un patient par la recherche ou en balayant la liste, repère ceux qui demandent attention (drapeau « à surveiller », séance en cours) et ouvre la vue d'ensemble en un clic.

## Route & module
- Route Angular : `/patients` (feature `patients`, lazy, standalone components).
- API : `GET /api/patients?q=&archived=&tag=&sort=name|created|updated&cursor=&limit=` (module backend `patients`, pagination curseur, RFC 9457 en erreur). `PatientDto` ne porte que l'identité, les drapeaux, les tags et les dates de création et de modification.
- Résumé de chaque ligne (dernière séance, émotions dominantes, situations actives, brouillons) : `GET /api/patients/{id}/overview?window=30d`, une synthèse par patient, lue après la liste sans la retarder (le contrat n'a pas de liste enrichie).
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
   | Dernière séance | date relative + modalité (cabinet / visio / contact) | `overview.recentSessions[0]` |
   | Émotions dominantes 30 j | 3 pastilles de famille | `overview.emotions` ; peut être vide (« pas encore de ressenti ») |
   | Situations actives | nombre · première situation | `overview.activeSituations` |
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

## Écarts avec la maquette
- **Colonne « Prochaine » (UX-F1)** : aucune séance planifiée n'existe au contrat et l'agenda est hors V1 (G Q25) ; la colonne et le tri « Prochaine séance, puis dernière » sont retirés. **Maquette corrigée** ; le tri propose « Dernière séance », « Flags d'abord » (calculés sur les résumés) et les tris du contrat (`name`, `updated`, `created`). L'ancienne mention « V1 : champ manuel » de cette fiche est abandonnée : `PatientDto` n'a pas ce champ.
- **Source des colonnes (UX-F1)** : `GET /api/patients` ne renvoie ni dernière séance, ni émotions, ni situations ; ces colonnes viennent de la synthèse `overview` de chaque ligne (contrat). Une liste enrichie côté API reste souhaitable (à proposer en tâche API).
- **Au-delà des premiers patients (UX-F1, comportement livré par UX-B1, à confirmer)** : le web lit la synthèse pour les 20 premières lignes seulement ; au-delà de 20 patients ou d'une page, le tri retombe sur « Dernière modification », les filtres « Aujourd'hui » et « Brouillons » sont désactivés avec une explication et les comptes partiels (« n séances aujourd'hui ») sont masqués. Non dessiné dans la maquette (qui montre 6 patients sur 24).
- « 24 suivis actifs · 3 séances aujourd'hui », compteurs des filtres, noms et pastilles : valeurs fictives, **ignorer**.

## Hors périmètre V1
Tri par score IA, colonnes personnalisables, export CSV.

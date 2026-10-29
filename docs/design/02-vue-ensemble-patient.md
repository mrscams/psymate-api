# Écran 2 · Vue d'ensemble d'un patient

> Spécification pour agents. Maquette : `02-vue-ensemble-patient.html` / `.png`. Palette : Nordique. Document B, écran 2 (page d'accueil d'un patient) ; Document A, module `overview` (A4.8, UC-9).

## Objectif
En moins de 10 secondes, avant ou pendant une séance, le praticien voit **où en est le patient** : tendance des ressentis, situations actives, dernière séance, traitement en cours, alertes. C'est une **lecture** (projection calculée), jamais une saisie.

## Route & module
- Route : `/patients/:id` (onglet 1 « Vue d'ensemble » ; onglets 2 Timeline, 3 Graphe).
- API : `GET /api/patients/{id}/overview?window=30d|90d|12m` → module backend `overview` (projection recalculée via événements `EntryRecorded`, `LinkAccepted`, `SessionClosed` de l'outbox ; cache par patient, invalidé par événement).
- Store : `PatientOverviewStore` : `window`, `summary`, `feelingsTrend`, `activeSituations`, `medications`, `recentSessions`, `alerts`, `stale` (true si projection > 5 min derrière).

## Structure de l'écran
1. **Barre d'application** + **en-tête patient** (partagés) : nom, âge, « suivie depuis », drapeau, sélecteur de dernière séance, bouton **« Démarrer la séance »** ; onglets 1/2/3.
2. **Barre de saisie repliée** (partagée) : champ « Noter quelque chose… » → s'ouvre en éditeur d'entrée ; séance obligatoire → pré-remplie avec la séance ouverte ou la dernière.
3. **Grille 12 colonnes**, gap 20 px :
   - **Résumé (12 col.)** : 4 tuiles chiffrées : *ressentis 30 j* (nombre + variation), *intensité moyenne* (/10, tendance ↑↓), *séances* (nombre sur la fenêtre), *liens actifs*. Chaque tuile a une ligne de contexte (« 3 de plus qu'en septembre »).
   - **Tendance des ressentis (8 col.)** : graphe ECharts ligne + aire, x = temps, y = intensité 0–10, une série par famille d'émotion présente (couleurs `--emo-*`), points = entrées ; survol = tooltip (date, émotion, intensité, séance). Sélecteur de fenêtre *30 j / 90 j / 12 mois*. État vide : « Pas encore de ressentis sur cette période ».
   - **Alertes (4 col.)** : liste courte, calculée par `overview` : *intensité ≥ 8 trois fois en 7 j*, *aucune séance depuis 45 j*, *médicament sans suivi depuis 30 j*. Chaque alerte → lien vers la timeline filtrée. Pas d'IA : règles explicites en V1 (`OverviewRules`).
   - **Situations actives (6 col.)** : cartes : titre, « depuis … », nombre de ressentis liés, mini barre d'intensité, bouton « Voir dans le graphe ».
   - **Traitement en cours (6 col.)** : capsules médicament (nom, dose, depuis, prescripteur si psychiatre), état *en cours / arrêté*.
   - **Dernières séances (12 col.)** : 3 dernières : date, durée, modalité, nombre d'entrées, 1ère ligne de la note de séance ; lien « Toutes les séances » → timeline groupée par séance.
4. **Pied** : « Dernière mise à jour de la synthèse : il y a 2 min » + lien *Recalculer* (si `stale`).

## Règles de calcul (rappel A4.8)
- Fenêtre par défaut 30 j, bornée par `occurred_from`.
- Intensité moyenne = moyenne des `entry_feeling.intensity` des entrées non archivées, visibilité `clinical` ou `private` du praticien courant.
- Situations actives = entrées `situation` sans `occurred_to` ou `occurred_to ≥ now − 30 j`.
- Une entrée avec `occurred_precision = month|year|period` est placée au milieu de sa période ; `life_phase` exclue des courbes, listée à part.

## Tokens
- Tuiles : fond `#FFFFFF`, bordure `#D6DCE6`, chiffre 28 px/700, variation `--success` ou `--danger` + flèche (jamais couleur seule).
- Courbes : familles `--emo-joy #D9A426`, `--emo-fear #7A68C9`, `--emo-sadness #3C66A6`, `--emo-anger #B8463C`… (voir étude couleurs §4) ; grille `#E4E8F0`.
- Alertes : `--warning #7A5A12` sur `#FBF1DC` ; critique `--danger #9A2F22` sur `#FBE9E7`.
- Situations : icône barre `#2F4F73`, teinte `#E1E9F2` ; médicaments : capsule `#B07A2A`, teinte `#FAF0DC` ; séances : `#3E8E7E`, teinte `#E2F1EE`.

## Accessibilité
- Chaque graphe a un `role="img" aria-label` résumant la tendance + tableau de données masqué (`sr-only`) ou bouton « Voir les données ».
- Tuiles = `<section aria-labelledby>` ; alertes = `<ul>` avec niveau annoncé en texte.
- Onglets patient : `role="tablist"`, flèches gauche/droite.

## Critères d'acceptation
- [ ] Après `POST /entries` (ressenti), la vue d'ensemble reflète l'entrée < 2 s (événement outbox → projection).
- [ ] Fenêtre 90 j : recalcul côté serveur, pas côté client.
- [ ] Alerte « aucune séance depuis 45 j » apparaît/disparaît selon seed.
- [ ] Mode sombre : tokens dark appliqués, contraste vérifié (axe-core en CI).
- [ ] Snapshot Vitest de `OverviewSummaryComponent` avec données vides.

## Hors périmètre V1
Résumé textuel automatique, comparaisons inter-patients, prédictions (V2, module `ai`).

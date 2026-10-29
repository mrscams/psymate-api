# Écran 6 · Éditeur de lien (panneau latéral sur le graphe)

> Spécification pour agents. Maquette : `06-editeur-de-lien.html` / `.png`. Palette : Nordique. Document A, module `linking` (`POST/PATCH/DELETE /api/patients/{id}/links`) ; Document B, écran 6.

## Objectif
Poser, en moins de dix secondes, un lien typé et orienté entre deux entrées d'un dossier, sans quitter le graphe ni la timeline. Le lien est **lisible en français** avant d'être enregistré, pour que le praticien vérifie le sens.

## Route & module
- Ouverture : depuis le graphe (clic droit / bouton « Lier » sur un nœud), la timeline (menu d'une entrée) ou la barre de commande (`/lier`). Route enfant `/patients/{id}/graphe?lier={entryId}` pour rendre l'état partageable.
- API : `POST /api/patients/{id}/links` `{ sourceId, targetId, type, rationale? }` → `201` avec le lien ; `PATCH` pour changer le type ou le sens ; `DELETE` → soft delete (visible dans l'historique des versions).
- Types V1 (`link_type` enum) : `triggers`, `triggered_by`, `worsens`, `worsened_by`, `relieves`, `co_occurs`, `follows`, `part_of`, `treated_by`. `triggered_by` / `worsened_by` sont des **vues inversées** de `triggers` / `worsens` : le serveur stocke toujours la forme active et le sens source→cible.
- Règles de compatibilité (validées côté serveur, grisées côté client) : `treated_by` uniquement depuis une entrée de type `medication` ; `part_of` cible une `situation` ou une `session` ; `co_occurs` est symétrique (pas de flèche).

## Structure
1. **Fond** : le graphe du patient assombri (overlay `rgba(30,38,51,.45)`), nœud source mis en évidence.
2. **Panneau droit** (420 px, `role="dialog"`, `aria-labelledby`) :
   - En-tête : « Lier deux entrées » + rappel patient + bouton fermer.
   - **Depuis** : carte de l'entrée source (forme + couleur du type, texte, date, séance) + « Changer ».
   - **Relation** : bouton « Inverser le sens » puis chips des types ; types incompatibles affichés désactivés avec une aide (« “est traité par” n'est possible que depuis un médicament »).
   - **Vers** : champ de recherche (même moteur que l'écran 7, limité au dossier), puis liste radio « Récentes et situations en cours » ; chaque option = texte, type, date, nombre de liens, mention « déjà lié (aggrave) » si un lien existe déjà avec la source.
   - **Le lien se lira** : phrase recomposée `<source> <verbe> <cible>` + description de la forme dans le graphe (trait plein + flèche, pointillé pour `co_occurs`).
   - **Pourquoi** (textarea facultatif, `rationale`, 500 caractères) — champ pensé pour l'IA V2.
   - **Liens déjà posés sur cette entrée · n** : liste compacte avec « Modifier ».
   - Pied : « Enregistrer le lien ⏎ », « Enregistrer et lier une autre », « Annuler Échap ».

## Interactions
- Le panneau s'ouvre avec la source préremplie et le focus sur la recherche de cible.
- Sélectionner une cible déjà liée avec le même type → bouton principal devient « Modifier le lien existant ».
- « Inverser le sens » échange source et cible **et** retourne les chips (déclenche ↔ est déclenché par) ; la phrase lisible se met à jour.
- Entrée = enregistrer ; Échap = fermer sans enregistrer (confirmation seulement si un type ou une cible a été choisi).
- Après enregistrement : le graphe reçoit l'arête animée (300 ms), toast « Lien enregistré · Annuler » (10 s).
- Un lien ne peut pas relier une entrée à elle-même ; doublon exact (`source,target,type`) → `409`, message « Ce lien existe déjà ».

## Tokens
- Chip sélectionnée : `#E3EAF8` fond / `#2C4C99` texte / bordure `#3A63C2`. Chips désactivées : texte `#8A94A6`, bordure `#D6DCE6`.
- Carte source : bordure gauche par type (ressenti `#8B7BD6`, événement `#3A63C2`, situation `#2F4F73`, médicament `#B07A2A`, séance `#3E8E7E`).
- Phrase lisible : fond `#EEF1F6`, le verbe en `font-weight: 600`.

## Accessibilité
- `role="dialog"` modal, focus piégé, retour du focus sur le nœud source à la fermeture.
- Types = `<button aria-pressed>` dans un `role="group"` nommé « Relation » ; cibles = `<input type="radio">` dans un `<fieldset>`.
- La phrase lisible est en `aria-live="polite"` : un lecteur d'écran entend le lien tel qu'il sera enregistré.

## Critères d'acceptation
- [ ] Source = ressenti, type « déclenche », cible = événement → phrase « <ressenti> déclenche <événement> » ; après « Inverser le sens » → « <événement> déclenche <ressenti> » et le lien enregistré a `source = événement`, `type = triggers`.
- [ ] Choisir « est déclenché par » sans inverser → le serveur reçoit `type = triggers` avec source et cible échangées (jamais un type passif stocké).
- [ ] `treated_by` grisé si la source n'est pas un médicament ; `POST` forcé → `422`.
- [ ] Doublon exact → `409`, panneau propose « Modifier le lien existant ».
- [ ] Échap avec aucun choix → fermeture immédiate ; avec un type choisi → confirmation.
- [ ] Le lien apparaît dans le graphe et la timeline sans rechargement (store `LinksStore` mis à jour depuis la réponse `201`).

## Hors périmètre V1
Liens multi-cibles en une fois, liens entre patients, suggestions automatiques de liens (V2 IA, qui utilisera `rationale`).

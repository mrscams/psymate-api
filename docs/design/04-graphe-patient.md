# Écran 4 · Graphe patient (carte mentale centrée sur une entrée)

> Spécification pour agents. Maquette : `04-graphe-patient.html` / `.png`. Palette : Nordique. Document B, écran 4 ; Document A, module `linking` (table `link`, CTE récursive profondeur ≤ 3, UC-7/UC-8).

## Objectif
Voir **ce qui est relié à quoi** autour d'une entrée (souvent une situation) : les ressentis déclenchés, les événements déclencheurs, les médicaments qui traitent, et créer/retirer des liens directement.

## Route & module
- Route : `/patients/:id/graph?root=<entryId>&depth=2`.
- API : `GET /api/patients/{id}/graph?root=&depth=1..3&types=&linkTypes=&from=&to=` → `{nodes[], edges[], truncated, superNodes[]}` (module `linking`, CTE récursive sur `link`, limite 150 nœuds puis regroupement en **super-nœuds**).
- `POST /api/links`, `PATCH /api/links/{id}` (statut), `DELETE` = statut `superseded` (jamais de suppression physique).
- Rendu : **Cytoscape.js** avec layout `cose-bilkent` ; store `PatientGraphStore` : `root`, `depth`, `filters`, `graph`, `selectedNode`, `selectedEdge`, `pendingLink`.

## Structure de l'écran
1. **Barre d'application + en-tête patient** (partagés), onglet 3 actif.
2. **Barre d'outils du graphe** : « Centré sur » (sélecteur d'entrée avec icône de type), **Profondeur** 1/2/3 (segmenté, défaut 2), filtres *Types d'entrées*, *Liens*, *Période* ; compteur `N nœuds · M liens` ; **Recentrer** ; bouton **« Lier »** (raccourci `L`).
3. **Zone graphe** (fond blanc, bordure subtile) :
   - Nœud racine : forme de son type, bordure 2 px `--primary`, teinte, légèrement plus grand.
   - **Formes par type** : ressenti = **cercle** (taille ∝ intensité, lavande `#8B7BD6`, chiffre d'intensité à l'intérieur), événement = **losange** cobalt, situation = **barre** / rectangle `#2F4F73`, séance = **carré arrondi** `#3E8E7E`, médicament = **capsule** ocre `#B07A2A`, note = **rectangle** gris `#7F8EA8`.
   - Étiquette sous le nœud : titre court · date (JetBrains Mono pour la date).
   - **Styles de liens** : `triggers` trait plein + flèche ; `worsens` tirets ; `co_occurs` pointillés sans flèche ; `treats` / `relieves` double trait ; `caused_by`, `follows`, `part_of` plein fin. Lien **proposé** (V2) = gris clair `#B9C2D1` + étiquette « suggéré ».
   - **Super-nœud** : rectangle en pointillés « 12 ressentis plus anciens · anxiété majoritaire » → clic = développe (ajoute les nœuds) ou recentre.
   - Zoom +/− en bas à gauche, molette, double-clic nœud = recentrer.
   - **Légende** en bas à droite (formes + styles de lien), repliable.
4. **Panneau latéral (détail)** s'ouvre à la sélection d'un nœud : type, titre, *Quand* (date + précision), *Séance*, émotion/famille, tags, texte, **Liens (N)** avec phrase lisible (« *Entretien d'évaluation* déclenche ce ressenti ») + « Modifier » ; liens dérivés marqués *dérivé* ; actions : *Voir dans la timeline*, *Recentrer le graphe ici*, *Modifier l'entrée · version n*.
5. **Pied du panneau** : aide : « Glisser un nœud vers un autre crée un lien · Suppr retire le lien sélectionné (confirmation) ».

## Interactions
- **Créer un lien** : glisser-déposer nœud A → nœud B, ou `L` puis deux clics → ouvre l'**éditeur de lien** (écran 5) : type, sens, justification (`rationale`), provenance `manual`.
- Sélection d'une arête → panneau : type, provenance, confiance (si `ai_*`), *Accepter / Rejeter* si `proposed`, *Retirer* sinon.
- Changement de profondeur ou de filtres = nouvelle requête (pas de filtrage client au-delà de 150 nœuds).
- Le layout est **stable** : positions mémorisées par `root` dans la session (évite que tout bouge à chaque lien ajouté) ; « Recentrer » relance le layout.
- Rejeter un lien le conserve (`status = rejected`), masqué par défaut, filtre « Liens : rejetés » pour le revoir.

## Tokens
- Fond graphe `#FFFFFF`, grille/axes `#E4E8F0`, trait de lien `--link #8A94A6`, proposé `#B9C2D1`, sélection `--primary #3A63C2` (halo 4 px `#E3EAF8`).
- Les ressentis sont **les seuls** nœuds lavande ; l'intensité pilote la taille (r = 14 + 2.2 × intensité px) et la saturation légère de la teinte, jamais la couleur de famille (réservée aux courbes).
- Panneau : `#FFFFFF`, bordure `#D6DCE6` ; puces de type avec icône + libellé (jamais couleur seule).

## Accessibilité
- Alternative non graphique obligatoire : bouton « Liste des liens » → tableau `source · type · cible · provenance · statut` (même données), navigable au clavier.
- Nœud sélectionné annoncé (`aria-live="polite"`), panneau = `role="complementary"`.
- Contrastes : chiffre d'intensité blanc sur `#8B7BD6` OK ≥ 24 px seulement → sinon texte `#4E3F9E` sur `#ECE8F8`.

## Critères d'acceptation
- [ ] `depth=3` sur un patient de 2 000 entrées → réponse < 500 ms (index GIN sur `link(source_id, target_id)`), `truncated=true` + super-nœuds.
- [ ] Glisser A→B crée un lien `manual/accepted` et met à jour le compteur sans recharger.
- [ ] Un lien rejeté n'apparaît plus mais reste en base (test d'intégration).
- [ ] Vue liste équivalente passe axe-core sans erreur.
- [ ] Aucun cycle infini sur `part_of` réciproque (test CTE avec `cycle` guard).

## Hors périmètre V1
Suggestions de liens IA (V2, `ai_suggested`), graphe multi-patients, export image.

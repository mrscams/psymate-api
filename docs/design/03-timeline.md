# Écran 3 · Timeline du patient

> Spécification pour agents. Maquette : `03-timeline.html` / `.png`. Palette : Nordique. Document B, écran 3 ; Document A, module `clinical` (M3-01), A6. Tâches web : M3-02, M3-03, M3-04.

## Objectif
Parcourir l'histoire du patient à n'importe quelle échelle (jour → décennie), filtrer par type, émotion, tag ou période, voir les situations en cours comme des plages, et ouvrir une entrée, la corriger, la lier ou la voir dans le graphe sans quitter l'onglet. L'onglet garde filtres, zoom et position de défilement pendant toute la session de travail (B2).

## Route & module
- Route : `/patients/:id/timeline?from=&to=&types=&emotion=&tag=&drafts=&mode=chronological|session&zoom=` ; onglet **2** de l'en-tête patient (touche `2`) ; `M` bascule le mode ; `F` donne le focus aux filtres ; `+` `-` et `Ctrl+molette` changent le zoom.
- API fenêtre (états A et B) : `GET /api/patients/{id}/timeline?from=&to=&types=&emotions=&tags=&draftsOnly=&includePrivate=&cursor=&limit=` → `TimelinePageDto {items[], facets {types, emotions, tags}, ongoingSituations[], nextCursor}`. Fenêtre `[from, to[` sur le début d'occurrence, tri `occurred.from` décroissant puis `id` ; `limit` 1 à 200 (50 par défaut) ; curseur keyset opaque : renvoyer `nextCursor` tel quel dans `cursor`, `null` sur la dernière page. `types`, `emotions`, `tags` : OU à l'intérieur d'une liste, ET entre listes ; `emotions` exclut les autres types. Facettes calculées sur toute la fenêtre filtrée, pas sur la page. `ongoingSituations` : situations dont la plage chevauche la fenêtre, même commencées avant `from` (`ongoing = true` sans fin) ; seuls `draftsOnly` et `includePrivate` s'y appliquent.
- Chaque `TimelineItemDto` : `id, type, title, status (draft|final), visibility (private|clinical), authorType (lecture seule, toujours practitioner en V1), occurred {from, to, precision, label}, recordedAt, sessionId (null pour une séance), emotion {code, family}, intensity, tags[], linkCount, ongoing`. `occurred.precision` ∈ `exact | day | month | year | period | life_phase` ; `label` ne sert que pour `period` et `life_phase` ; pour `life_phase` les bornes sont déduites de la date de naissance.
- API décennie (état C) : `GET /api/patients/{id}/timeline/summary?granularity=month&from=&to=&includePrivate=` → `TimelineSummaryDto {granularity, periods[] {period AAAA-MM, from, counts {type → n}, feelingCount, averageIntensity}}`, périodes non vides seulement, de la plus ancienne à la plus récente, calculé à la volée sans pagination.
- Entrées privées : exclues par défaut (`includePrivate=false`) ; le filtre est serveur, jamais client. Brouillons inclus par défaut ; « Brouillons seulement » = `draftsOnly=true`.
- Préférence `timeline.groupBy` (`session | month | chronological`, fiche 14, défaut `session`) : lue au premier affichage et écrite par la bascule `M` via `PATCH /api/me/preferences`. Correspondance : `chronological` = état A, `session` = état B, `month` = état C forcé (agrégats même sur une plage courte). Le paramètre `mode=` de l'URL l'emporte sur la préférence pendant la session, sans l'écrire.
- Store : `PatientTimelineStore` : `window {from, to}`, `pages` (trois fenêtres : précédente, courante, suivante), `filters`, `mode`, `zoom`, `selectedId`, `summary`, `loading`, `error` (par fenêtre).

## Structure
1. **Barre d'application** et **en-tête patient permanent** (onglet 2 actif, bascule « Vue » reprise de l'écran 2), **barre de capture** repliée (B5).
2. **Barre d'outils** : puces **Types** (Tous + une par type, forme + compteur venant de `facets.types`), puces émotion (`facets.emotions`, pastille de famille) et tag (`facets.tags`), « Brouillons seulement », période (30 j / 90 j / 1 an / Tout), bascule **Chronologique | Par séance** avec raccourci `M`, zoom `−` / `+` avec curseur et granularité courante (jour, semaine, mois).
3. **Ligne d'état** : `N entrées · 3 fenêtres en mémoire`, bornes de la fenêtre courante, facettes.
4. **État A · chronologique** (défaut quand `groupBy = chronological`) : grille `bandes | axe | cartes`. Bandes verticales continues à gauche de l'axe pour chaque élément de `ongoingSituations` (de `occurred.from` à `occurred.to` ou jusqu'en haut si `ongoing`, flèche en tête, libellé vertical). Axe avec date et jour, marqueur de la forme du type. Une carte par item : bordure gauche 3 px et icône du type, titre, extrait, tags, `linkCount`, statut brouillon, date en JetBrains Mono rendue selon `precision` (`exact` → `24 oct. 2026 · 07:40` ; `day` → `18 oct. 2026` ; `month` → `oct. 2026` ; `year` → `2014` ; `period` / `life_phase` → `label`, bornes en secondaire), mention « précision : … » ; ressenti : pastille de famille à l'opacité de l'intensité (35 / 55 / 80 / 100 %) et chip `n/10` sur `feeling-tint` ; médicament : ancienne posologie barrée → nouvelle. Pastille « dit en séance du … » quand `sessionId` est renseigné et que la séance n'est pas le jour de `occurred.from`. En bas : squelette de la fenêtre suivante, puis zone **Avant le suivi** (fond `#E4E8F0`, cartes réduites) pour les items antérieurs à la date de début de suivi du patient.
5. **État B · par séance** (`groupBy = session`) : une carte par item `type = session`, de la plus récente à la plus ancienne, dépliable ; en-tête = date complète, horaires, durée, modalité, statut (non clôturée en avertissement), `n entrées` ; corps = items dont `sessionId` est celui de la séance (icône, titre, moment réel d'occurrence, liens) et compte rendu à droite avec « Clôturer la séance » / « Ouvrir ». La plus récente est dépliée à l'ouverture, ou celle désignée par la vue d'ensemble (B2).
6. **État C · décennie** (`groupBy = month`, ou automatique dès que la plage affichée dépasse 3 ans) : SVG (ECharts en Angular) avec barres mensuelles empilées par type aux couleurs des types, courbe d'intensité moyenne en `feeling` sur l'axe droit 0–10, repères d'années, marqueur « début du suivi », bandes horizontales des situations longues, étiquette « Agrégé par mois · double-clic pour zoomer », légende.
7. **Menu contextuel** d'une carte (`⋯`, clic droit ou `Maj+F10`) : Ouvrir · Corriger (crée une version) · Lier `L` · Voir dans le graphe.

## Interactions
- Molette = défilement virtuel (CDK) ; `Ctrl+molette`, `+` `-` = zoom. Le zoom change la granularité d'affichage sans requête tant que les trois fenêtres couvrent la plage ; au-delà, la fenêtre manquante est demandée avec `from`/`to` et le `nextCursor` courant. Au-delà d'une plage de 3 ans, passage automatique à l'état C via `/timeline/summary` (M3-04) ; double-clic sur un mois ramène la timeline au zoom « semaine » sur ce mois ; `Échap` revient.
- Clic sur une carte → panneau de l'entrée (`GET /api/entries/{id}`) ; `Ctrl+clic` sur deux cartes → « Lier » ; « Voir dans le graphe » → onglet 3 centré, retour avec position conservée (B2).
- Glisser l'extrémité d'une bande de situation en cours → confirmation puis `PATCH /api/entries/{id}` avec `occurred.to`.
- Chips : compteurs rafraîchis à chaque réponse depuis `facets` ; « Tous » réinitialise `types` ; « Effacer les filtres » dans l'état vide.
- Chargement : la fenêtre courante s'affiche, les adjacentes se chargent en arrière-plan, squelette au-delà. Erreur : la fenêtre en échec affiche « Réessayer », les autres restent. Vide : « Aucune entrée pour ces filtres » ; patient sans entrée : flèche vers la barre de capture.
- Performance (A6, M3-02) : 200 éléments rendus en moins de 150 ms après réponse, défilement fluide sur 5 000 entrées, jamais plus de trois fenêtres en mémoire.

## Tokens
- Types : note rectangle `#7F8EA8`, événement losange `#3A63C2`, situation barre `#2F4F73`, ressenti cercle `#8B7BD6`, séance carré arrondi contour `#3E8E7E`, médicament capsule `#B07A2A` ; bordure gauche 3 px de la carte, jamais d'aplat.
- Familles d'émotions (pastilles et courbe uniquement) : peur `#7A68C9`, tristesse `#3C66A6`, joie `#D9A426`, confiance `#3E8E7E`, colère `#B8463C`, surprise `#2E9AA8`, dégoût `#6E7F3E`, anticipation `#D07A2C`.
- Bandes de situations : `#2F4F73` à 25 % d'opacité, libellé à 100 % ; axe et graduations `#D6DCE6` ; carte sélectionnée bordure 1,5 px `--primary` ; chips actives `#E3EAF8` / `#2C4C99` ; brouillon et séance non clôturée `#FBF1DC` / `#7A5A12` ; zone « Avant le suivi » `--surface-sunken`.
- Dates, horaires, compteurs, curseur : JetBrains Mono.

## Accessibilité
- Liste des cartes dans un `role="list"` étiqueté, chaque carte `role="listitem"` ; bandes de situations `role="listitem"` avec `aria-label` complet (nom, bornes, statut) ; séances dépliables en `<button aria-expanded aria-controls>`.
- Barre d'outils `role="toolbar"`, puces `aria-pressed`, groupes `role="group" aria-label` ; raccourcis annoncés dans les libellés.
- Menu contextuel `role="menu"` / `role="menuitem"`, ouvert par `aria-haspopup="menu"`, navigable `↑ ↓ Entrée Échap`, focus rendu à la carte à la fermeture.
- Graphique décennie : `role="img"` avec `aria-label` qui résume plage, tendance et bandes ; `<title>` par mois ; la légende reste en texte.
- Rien n'est porté par la couleur seule : forme du type, texte de l'intensité, mention de la précision.

## Critères d'acceptation
- [ ] Défilement fluide sur le seed de 5 000 entrées (pas de trame perdue mesurable en e2e), trois fenêtres au plus en mémoire (M3-02).
- [ ] Zoom `+` / `-` / `Ctrl+molette` sans nouvelle requête tant que la fenêtre couvre la plage ; une requête `from`/`to` sinon (M3-02).
- [ ] e2e : filtrer « anxiété » (`emotions=anxiety`, compteurs mis à jour depuis `facets`), basculer par séance avec `M` (préférence écrite), déplier la dernière séance (M3-03).
- [ ] Bascule automatique en vue décennie au-delà d'une plage de 3 ans, retour par double-clic sur un mois (M3-04).
- [ ] Une situation en cours de `ongoingSituations` commencée avant `from` est affichée en bande sur toute sa durée visible, avec flèche « en cours » si `ongoing`.
- [ ] La pastille « dit en séance du … » apparaît quand `occurred.from` n'est pas le jour de la séance de `sessionId`, et pas autrement.
- [ ] Les items antérieurs au début du suivi sont rendus dans la zone « Avant le suivi », sous le marqueur de début, avec leur `precision` (`period`, `year`, `life_phase`) lisible.
- [ ] Le menu contextuel s'ouvre et se parcourt au clavier (`Maj+F10`, flèches, `Entrée`, `Échap`) et `L` ouvre l'éditeur de lien.

## Écarts avec la maquette
- Compteurs (214, 61, 38…, « 2 014 entrées », facettes) et agrégats mensuels : valeurs fictives, **ignorer** ; tout vient de `facets` et de `periods[]`.
- Situations « Séparation » (15 août → 30 sept.) et « Reconversion professionnelle » : inventées pour montrer une bande clôturée et une bande longue, **ignorer** ; le seed Marie D. fait foi.
- « Les entrées saisies hors séance (37) restent visibles en mode chronologique » : indication inventée, **ignorer** (en V1 toute entrée appartient à une séance).
- « Par séance » et zoom arrière désactivés en vue décennie : **suivre** (l'état C est purement chronologique ; `-` n'a plus de cran).
- Format « précision : exacte / jour / mois » et pastilles tiretées pour `period` / `year` / `life_phase` : **suivre**, en traduisant les codes via i18n.
- Classes CSS `.c .d .chip .tg …` du HTML : artefact de taille de la maquette, **ignorer** ; reproduire avec Material M3 et les tokens SCSS.
- Bascule « Vue Simple / Complète » dans l'en-tête : composant partagé de l'écran 2, sans effet sur la timeline, **suivre** l'en-tête partagé tel quel.

## Hors périmètre V1
Entrées du compagnon patient (`authorType` autre que `practitioner`), suggestions de liens IA dans les cartes, agenda Google sur l'axe (G Q25), export de la vue, déclinaison mobile de la grille bandes/axe/cartes.

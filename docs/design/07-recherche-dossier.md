# Écran 7 · Recherche dans le dossier d'un patient

> Spécification pour agents. Maquette : `07-recherche-dossier.html` / `.png`. Palette : Nordique. Document B, écran 7 ; Document A, module `search` (pg_trgm + unaccent, UC-10).

## Objectif
Retrouver une entrée du dossier par un mot, un tag, une émotion ou une période, en moins de 5 secondes, puis l'ouvrir dans la timeline ou le graphe. La recherche est **scopée au patient courant** ; la recherche multi-patients est hors V1.

## Route & module
- Route : `/patients/:id/search?q=&types=&from=&to=&session=&archived=` ; ouverte par `/` depuis n'importe quel écran patient (le champ de la barre d'application devient le champ de recherche).
- API : `GET /api/patients/{id}/search?q=&types[]=&from=&to=&sessionId=&includeArchived=&sort=relevance|recent&cursor=` → `{total, countsByType, items[], nextCursor, tookMs}`. Chaque item : `entryId, type, title, snippet (HTML avec <mark>), occurredFrom, occurredPrecision, sessionId, sessionLabel, linksCount, tags[], version, matchedVia: text|tag|emotion|link`.
- Index : colonne générée `search_text` (titre + corps + tags + libellé d'émotion) avec `unaccent`, index GIN `gin_trgm_ops` ; tri pertinence = `similarity()` puis `occurred_from DESC`.
- Syntaxe reconnue dans `q` (parseur côté API, documenté dans l'aide du panneau) : `tag:travail`, `émotion:anxiété`, `avant:2026-09`, `après:2026-06`, `séance:17oct`, guillemets pour une expression exacte.
- Store : `PatientSearchStore` : `query`, `filters`, `results`, `counts`, `selectedId`, `loading`.

## Structure
1. **Barre d'application** (fil d'Ariane Patients / Marie D. / Recherche).
2. **Zone de recherche** (fond blanc) : champ 48 px bordure primaire, valeur en cours, mention « dans le dossier de … », touche `Échap`. Dessous : puces **Types** (Tous + une par type avec sa forme et son compteur), sélecteurs **Période** et **Séance**, case « inclure les entrées archivées ».
3. **Résultats** (colonne gauche, `flex: 999 1 520px`) : ligne de synthèse (`N résultats pour « … » · sans accents, par trigrammes · 42 ms`), tri *Pertinence / Plus récent*. Liste `<ol>` : icône de type, titre avec `<mark>`, méta (type · date mono · précision), extrait avec `<mark>`, ligne de contexte (séance, liens, tags). Le résultat sélectionné a une bordure primaire + halo `#E3EAF8`. Une entrée trouvée **par ses liens** porte l'explication (« Trouvée par ses liens : … »). Bouton « Afficher les N autres ».
4. **Aperçu** (colonne droite 320–420 px) : type, titre, `dl` Quand / Séance / Saisi / Visibilité, texte complet, liens, boutons **Ouvrir dans la timeline** (primaire), *Voir dans le graphe*, *Modifier* ; pied d'aide clavier et syntaxe.

## Interactions
- Saisie : debounce 250 ms, min 2 caractères ; `Entrée` ouvre le résultat sélectionné dans la timeline ; `↑ ↓` déplacent la sélection ; `Échap` ferme et revient à l'écran précédent.
- Clic sur une puce de type = filtre exclusif ; `Tous` réinitialise ; les compteurs viennent de `countsByType` (calculés sans le filtre de type).
- Le surlignage utilise `--search-highlight #FBF1DC` ; jamais de couleur seule : le `<mark>` garde le texte en `#1E2633`.
- Entrées archivées : exclues par défaut, affichées en opacité 0,85 avec mention « archivée » quand incluses.
- Entrées `private` d'un autre praticien : jamais retournées (filtre serveur, pas client).

## Tokens
- Champ actif : bordure 2 px `--primary`, rayon 12 px ; puces actives `#E3EAF8` / `#2C4C99` bordure `--primary`.
- Icônes de type : note rectangle `#7F8EA8`, événement losange `#3A63C2`, situation barre `#2F4F73`, ressenti cercle `#8B7BD6`, séance carré arrondi contour `#3E8E7E`, médicament capsule `#B07A2A`.
- Dates et durées : JetBrains Mono.

## Accessibilité
- Champ `type="search"` avec `<label>` masqué ; résultats dans `<ol aria-label>` ; sélection courante `aria-current="true"` et annoncée (`aria-live="polite"` sur la synthèse).
- Aperçu : `<aside aria-label>` ; `<dl>` pour les métadonnées.
- Navigation clavier complète sans souris (critère Playwright).

## Critères d'acceptation
- [ ] « entretien manager » trouve l'événement du 18 oct., le ressenti lié, la séance du 17 oct. et la note de juin (seed Marie D.) ; « entretien manger » (faute) trouve les mêmes grâce aux trigrammes.
- [ ] `tag:travail` ne renvoie que les entrées portant le tag.
- [ ] Filtre « Ressentis » → 3 résultats, compteurs inchangés.
- [ ] 2 000 entrées : réponse < 150 ms (index GIN, Testcontainers).
- [ ] Aucune entrée `private` d'un autre praticien dans les résultats (test d'intégration).
- [ ] Parcours clavier `/ → saisie → ↓ → Entrée` ouvre la timeline sur l'entrée.

## Hors périmètre V1
Recherche multi-patients, recherche sémantique (V2 `ai`), recherche dans les pièces jointes.

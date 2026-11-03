# Écran 8 · Annuaire public

> Spécification pour agents. Maquette : `08-annuaire-public.html` / `.png`. Palette : Nordique. Document B, écran 8, flux 7 ; Document A, module `directory` (M5-02, G Q15, Q20). Tâche web : M5-05 (avec 09 et 24).
>
> Écrit le 2 novembre 2026 depuis B4 écran 8, B3 flux 7 et `api-reference.md` (GET /api/public/directory, GET /api/vocabulary/directory). La section « Écarts avec la maquette » l'emporte sur le HTML.

## Objectif
Un **visiteur non connecté** trouve en moins d'une minute un psychologue, un psychiatre ou un coach qui lui correspond, puis ouvre sa fiche (écran 9). Seuls les profils **publiés** par des praticiens au **dossier approuvé** apparaissent ; aucune donnée patient n'est jamais exposée.

## Route & module
- Route publique : `/annuaire?ville=&profession=&specialite=&public=&modalite=&langue=&page=` (layout `PublicLayout`, sans garde ; prérendu souhaitable pour le SEO ; pas de numéros d'urgence, G Q20). L'URL porte l'état complet de la recherche : un lien copié rejoue la même page.
- API : `GET /api/public/directory` (M5-02) — paramètres `q`, `city`, `professions`, `modalities`, `audiences`, `languages`, `accepting`, `sort` (`relevance` | `distance` | `name`), `page` ; réponse `SearchResult` : `items` (`Card`[], 20 au plus), `facets` (professions, modalities, audiences, languages avec compteurs, toutes pages confondues), `page`, `pageSize` = 20, `total` (500 fiches examinées au plus), `totalPages`. `Cache-Control: max-age=60`. Limite 60 requêtes / min / IP → 429 `RATE_LIMITED` avec `retryAfterSeconds` (G Q24). 400 `INVALID_PARAMETERS` avec `errors[]` par champ.
- Liste fermée (G Q15) : `GET /api/vocabulary/directory` (`specialties`, `audiences`, `modalities`, `languages`, codes + libellés FR/EN). Attention : cet endpoint exige une **session praticien** ; voir « Écarts ».
- Store : `PublicDirectoryStore` : `query`, `result`, `facets`, `loading`, `error` (dont `rateLimited`), `vocabulary`.

## Structure
1. **En-tête public** partagé (09/24) : logo PsyMate + « Annuaire des praticiens », liens *Annuaire* (courant), *Comment ça marche*, bouton secondaire **Connexion**, lien « Vous êtes praticien ? ».
2. **Bandeau de recherche** (`<form role="search">`) : champ *Mot-clé* (→ `q`), champ *Ville ou code postal* (→ `city`), sélecteur *Profession* (Toutes / Psychologue / Psychiatre / Coach → `professions`), sélecteur *Spécialité* (liste fermée → `q` avec le code, voir « Écarts »), bouton cobalt **Rechercher**.
3. **Colonne de filtres** (272 px, `<aside aria-label="Filtres">`) : `fieldset` par facette avec compteurs en JetBrains Mono — profession, publics, modalités (au cabinet, en visio, à domicile), langues ; case « Accepte de nouveaux patients » (→ `accepting=true`) ; lien « Tout effacer ».
4. **Résultats** : ligne « *N* praticiens à *Ville* et alentours », sélecteur de tri, liste de **fiches résumées** (`Card`) : initiales rondes sur `#E3EAF8` (`photoUrl` est null en V1), `displayName`, puce profession, badge *Profil vérifié* (`verified`), disponibilité (`availability` : accepting / waitlist / closed ; `unknown` n'est pas rendu), `bioExcerpt`, 4 spécialités max (libellés FR du vocabulaire), publics, modalités, `city`, langues, boutons **Voir la fiche** (→ `/praticiens/{slug}`, écran 9) et *Contacter* (→ écran 9 ancre `#contact`).
5. **Pagination** par page : « ‹ Précédent · 1 2 3 … N · Suivant › » calculée depuis `page` / `totalPages` ; note discrète « Seuls les praticiens dont le dossier a été vérifié apparaissent ici ».
6. **Pied de page** public : « Praticiens inscrits, responsables de leur profil », mentions légales, confidentialité, accessibilité, contact.

### États
- **A · Résultats** (défaut) : liste ci-dessus, facettes actives, pagination.
- **B · Carte** : liste compacte à gauche, carte SVG stylisée avec marqueurs numérotés et bulle du marqueur sélectionné. **Hors V1** (voir « Écarts »).
- **C · Aucun résultat** (`total = 0`) : filtres actifs rappelés en chips retirables, facettes à 0 grisées (`disabled`), message « Aucun praticien à *Ville* pour ces critères », trois suggestions en boutons secondaires (élargir, retirer le dernier filtre, inclure la visio) et lien « Comment ça marche » (écran 24).
- **Chargement** : 3 fiches squelette ; la liste précédente reste visible, grisée. **Erreur** : « L'annuaire est momentanément indisponible », saisie conservée ; 429 → « Trop de recherches, réessayez dans *n* s » (`retryAfterSeconds`), bouton Rechercher désactivé pendant ce délai.

## Interactions
- Filtres et tri sans rechargement ; chaque changement met à jour l'URL (`replaceState`) et relance la requête après 300 ms d'inactivité ; `Entrée` dans un champ lance la recherche ; un changement de filtre revient en page 1.
- Les facettes de la réponse remplacent les compteurs ; une facette à 0 reste visible mais désactivée.
- Clic sur la fiche résumée entière ou sur « Voir la fiche » → écran 9 ; « Contacter » → écran 9 + `#contact`. Aucun compte créé, aucun cookie posé.

## Tokens
- Fond `#EEF1F6`, fiches `#FFFFFF`, bordure `#D6DCE6`, rayon 14 px (registre grand public) ; tags de spécialité en `surface-sunken` `#E4E8F0`.
- Un seul élément cobalt `#3A63C2` par zone : « Rechercher » dans le bandeau, « Voir la fiche » dans chaque fiche, page courante dans la pagination.
- Profession : psychologue `feeling-tint #ECE8F8` / `feeling-text #4E3F9E` (exception délibérée de l'étude), psychiatre `#E1E9F2` / `#2F4F73`, coach `#E4E8F0` / `#5A6679`.
- Badge vérifié `--success #1E6B45` sur `#E4F2EA` **avec icône coche**, jamais la couleur seule ; chip « Accepte de nouveaux patients » en `primary-tint #E3EAF8` / `#2C4C99` ; « Liste d'attente » et « Complet » neutres.
- Compteurs, distances, tarifs : JetBrains Mono.

## Accessibilité
- Landmarks : `header`, `nav aria-label`, `main`, `form role="search"`, `aside aria-label="Filtres"`, `nav aria-label="Pagination"` avec `aria-current="page"`, `footer`.
- `<label for>` sur chaque champ ; facettes en `fieldset`/`legend` ; compte de résultats dans une zone `aria-live="polite"` ; boutons de bascule avec `aria-pressed` ; icônes décoratives `aria-hidden`.
- Initiales en `role="img" aria-label="Photo de … (initiales …)"` ; cibles tactiles ≥ 40 px ; fonctionne à 390 px (filtres repliés sous un bouton « Filtres (n) », fiches en une colonne, boutons pleine largeur).
- Balises `<title>`, `meta description`, `rel="canonical"` sans paramètres de pagination au-delà de la page 1.

## Critères d'acceptation
- [ ] e2e sans connexion : trouver (ville + filtre), ouvrir une fiche, contacter ; Lighthouse accessibilité ≥ 90 (C2 M5-05).
- [ ] Seuls les profils publiés **et** approuvés apparaissent : un profil publié d'un praticien non approuvé n'est ni listé ni atteignable par le filtre `q`.
- [ ] Le sélecteur *Spécialité* ne propose que les codes de la liste fermée (G Q15) ; aucune saisie libre, un code inconnu dans l'URL est ignoré.
- [ ] `total = 0` → état C avec les trois suggestions et le lien « Comment ça marche » ; les filtres actifs restent retirables un par un.
- [ ] Les compteurs des facettes reflètent `facets` de la réponse et se mettent à jour à chaque recherche.
- [ ] Un 429 affiche le message avec le délai `retryAfterSeconds`, conserve la saisie et ne relance pas la requête avant la fin du délai.

## Écarts avec la maquette
- **Tarif** : absent du schéma `Card` → ne pas l'afficher dans la liste en V1 (il est sur la fiche 09). La maquette le montre ; ignorer.
- **Tri par distance** : exige `lat`/`lng`, et aucun profil n'a de position en V1 → tri par défaut **pertinence**, puis **nom** ; l'option *Distance* est masquée tant que le contrat ne renvoie pas `distanceKm`. Les mentions « 1,2 km » de la maquette ne sont pas rendues.
- **Carte (état B)** : hors V1 tant qu'il n'y a pas de géolocalisation → **omettre** la bascule Liste | Carte plutôt qu'un bouton désactivé (pas d'affordance pour une fonction absente) ; l'état B reste une référence visuelle pour la V1.1.
- **Pagination** = ce que renvoie l'API (`page`, `totalPages`, 20 par page) ; le « 1 2 3 … 5 » de la maquette est illustratif.
- **Mot-clé `q`** : présent dans le contrat (nom, codes de spécialités, ville, présentation, 100 caractères) → ajouter le champ *Mot-clé* de B-ux que la maquette omet. Le sélecteur *Spécialité* envoie le code de la liste fermée dans `q`, faute de paramètre `specialties` dans le contrat ; la facette *Spécialités* avec compteurs n'existe pas dans `facets` → ne pas l'afficher en V1.
- **Vocabulaire** : `GET /api/vocabulary/directory` exige une session → le layout public ne peut pas l'appeler. En V1, embarquer les libellés FR/EN des codes dans un fichier de traduction du front (`directory-vocabulary.fr.json`) généré depuis le seed V04, jusqu'à un endpoint public (à proposer dans G).
- **Modalité « à domicile »** (`home`) : facette à ajouter à la colonne de filtres, absente de la maquette.

## Hors périmètre V1
Carte interactive et géolocalisation, photos (`photoUrl` null), avis et classement, prise de rendez-vous, mot-clé par suggestion automatique, signalement d'une fiche (V1.1, G Q26), page d'accueil distincte de l'annuaire.

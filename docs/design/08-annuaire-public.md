# Écran 8 · Annuaire public

> Spécification pour agents. Maquette : `08-annuaire-public.html` / `.png`. Palette : Nordique. Document B, écran 8, flux 7 ; Document A, module `directory` (M5-02, G Q15, Q20). Tâche web : M5-05 (avec 09 et 24).
>
> Écrit le 2 novembre 2026 depuis B4 écran 8, B3 flux 7 et `api-reference.md` (GET /api/public/directory, GET /api/vocabulary/directory). La section « Écarts avec la maquette » l'emporte sur le HTML.
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : maquette HTML corrigée selon les écarts : distance, tarif, bascule Liste | Carte et état B retirés, « et alentours » retiré (ville exacte), champ *Mot-clé* et modalité « à domicile » ajoutés, pas de compteur sur « Accepte de nouveaux patients » ; panneau « Filtres (n) » sous 760 px (fiche seule).

## Objectif
Un **visiteur non connecté** trouve en moins d'une minute un psychologue, un psychiatre ou un coach qui lui correspond, puis ouvre sa fiche (écran 9). Seuls les profils **publiés** par des praticiens au **dossier approuvé** apparaissent ; aucune donnée patient n'est jamais exposée.

## Route & module
- Route publique : `/annuaire?ville=&profession=&specialite=&public=&modalite=&langue=&page=` (layout `PublicLayout`, sans garde ; prérendu souhaitable pour le SEO ; pas de numéros d'urgence, G Q20). L'URL porte l'état complet de la recherche : un lien copié rejoue la même page.
- API : `GET /api/public/directory` (M5-02) — paramètres `q`, `city`, `professions`, `specialties` (M10-02), `modalities`, `audiences`, `languages`, `accepting`, `sort` (`relevance` | `distance` | `name`), `page` ; réponse `SearchResult` : `items` (`Card`[], 20 au plus), `facets` (professions, specialties avec libellés, modalities, audiences, languages avec compteurs, toutes pages confondues ; exacts jusqu'à 500 fiches, G Q48), `page`, `pageSize` = 20, `total` (500 fiches examinées au plus), `totalPages`. `Cache-Control: max-age=60`. Limite 60 requêtes / min / IP → 429 `RATE_LIMITED` avec `retryAfterSeconds` (G Q24). 400 `INVALID_PARAMETERS` avec `errors[]` par champ.
- Liste fermée (G Q15) : `GET /api/vocabulary/directory` (`specialties`, `audiences`, `modalities`, `languages`, codes + libellés FR/EN). Cet endpoint exige une **session praticien** ; les pages publiques utilisent `GET /api/public/directory/vocabulary` (M10-01), même contenu, sans session, limité à 60 requêtes par minute et par IP ; voir « Écarts ».
- Store : `PublicDirectoryStore` : `query`, `result`, `facets`, `loading`, `error` (dont `rateLimited`), `vocabulary`.

## Structure
1. **En-tête public** partagé (09/24) : logo PsyMate + « Annuaire des praticiens », liens *Annuaire* (courant), *Comment ça marche*, bouton secondaire **Connexion**, lien « Vous êtes praticien ? ».
2. **Bandeau de recherche** (`<form role="search">`) : champ *Mot-clé* (→ `q`), champ *Ville ou code postal* (→ `city`), sélecteur *Profession* (Toutes / Psychologue / Psychiatre / Coach → `professions`), sélecteur *Spécialité* (liste fermée → `specialties`, voir « Écarts »), bouton cobalt **Rechercher**.
3. **Colonne de filtres** (272 px, `<aside aria-label="Filtres">`) : `fieldset` par facette avec compteurs en JetBrains Mono — profession, spécialités, publics, modalités (au cabinet, en visio, à domicile), langues ; case « Accepte de nouveaux patients » (→ `accepting=true`), **sans compteur** (aucune facette `accepting` au contrat) ; lien « Tout effacer ». **Sous 760 px**, la colonne se replie sous un bouton « Filtres (*n*) » (*n* = filtres actifs, mot-clé et ville exclus) qui l'ouvre en panneau.
4. **Résultats** : ligne « *N* praticiens à *Ville* » (sans « et alentours » : `city` est une ville exacte), sélecteur de tri (*Pertinence* | *Nom*), liste de **fiches résumées** (`Card`) : initiales rondes sur `#E3EAF8` (`photoUrl` est null en V1), `displayName`, puce profession, badge *Profil vérifié* (`verified`), disponibilité (`availability` : accepting / waitlist / closed ; `unknown` n'est pas rendu), `bioExcerpt`, 4 spécialités max (libellés FR du vocabulaire), publics, modalités, `city`, langues, boutons **Voir la fiche** (→ `/praticiens/{slug}`, écran 9) et *Contacter* (→ écran 9 ancre `#contact`).
5. **Pagination** par page : « ‹ Précédent · 1 2 3 … N · Suivant › » calculée depuis `page` / `totalPages` ; note discrète « Seuls les praticiens dont le dossier a été vérifié apparaissent ici ».
6. **Pied de page** public : « Praticiens inscrits, responsables de leur profil », mentions légales, confidentialité, accessibilité, contact.

### États
- **A · Résultats** (défaut) : liste ci-dessus, facettes actives, pagination.
- **B · Carte** : **hors V1** (voir « Écarts »), retiré de la maquette par UX-F1 ; l'ancienne version reste dans l'historique git (commit `1498e50` de ce dépôt, ou `597f638` de psymate-web) comme référence V1.1.
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
- Compteurs : JetBrains Mono.

## Accessibilité
- Landmarks : `header`, `nav aria-label`, `main`, `form role="search"`, `aside aria-label="Filtres"`, `nav aria-label="Pagination"` avec `aria-current="page"`, `footer`.
- `<label for>` sur chaque champ ; facettes en `fieldset`/`legend` ; compte de résultats dans une zone `aria-live="polite"` ; boutons de bascule avec `aria-pressed` ; icônes décoratives `aria-hidden`.
- Initiales en `role="img" aria-label="Photo de … (initiales …)"` ; cibles tactiles ≥ 40 px ; fonctionne à 390 px (filtres repliés sous un bouton « Filtres (n) » dès 760 px, fiches en une colonne, boutons pleine largeur).
- Balises `<title>`, `meta description`, `rel="canonical"` sans paramètres de pagination au-delà de la page 1.

## Critères d'acceptation
- [ ] e2e sans connexion : trouver (ville + filtre), ouvrir une fiche, contacter ; Lighthouse accessibilité ≥ 90 (C2 M5-05).
- [ ] Seuls les profils publiés **et** approuvés apparaissent : un profil publié d'un praticien non approuvé n'est ni listé ni atteignable par le filtre `q`.
- [ ] Le sélecteur *Spécialité* ne propose que les codes de la liste fermée (G Q15) ; aucune saisie libre, un code inconnu dans l'URL est ignoré.
- [ ] `total = 0` → état C avec les trois suggestions et le lien « Comment ça marche » ; les filtres actifs restent retirables un par un.
- [ ] Les compteurs des facettes reflètent `facets` de la réponse et se mettent à jour à chaque recherche.
- [ ] Un 429 affiche le message avec le délai `retryAfterSeconds`, conserve la saisie et ne relance pas la requête avant la fin du délai.

## Écarts avec la maquette
Maquette HTML corrigée le 11 novembre 2026 (UX-F1) pour les tarifs, distances, carte, « et alentours », mot-clé, « à domicile » et le compteur d'« Accepte de nouveaux patients ». Pas de `.png` pour cet écran.
- **Tarif** : absent du schéma `Card` → pas affiché dans la liste en V1 (il est sur la fiche 09) (contrat ; maquette corrigée).
- **Tri par distance** : exige `lat`/`lng`, et aucun profil n'a de position en V1 → tri par défaut **pertinence**, puis **nom** ; l'option *Distance* est masquée tant que le contrat ne renvoie pas `distanceKm` ; pas de « 1,2 km » ni de « rayon de 25 km » (contrat ; maquette corrigée, la suggestion « Élargir le rayon » devient « Chercher dans toutes les villes »).
- **Carte (état B)** : hors V1 tant qu'il n'y a pas de géolocalisation → **omettre** la bascule Liste | Carte plutôt qu'un bouton désactivé (pas d'affordance pour une fonction absente) (maquette corrigée : bascule et état B retirés, référence V1.1 dans l'historique git).
- **« et alentours »** : `city` filtre une ville exacte (contrat) → « *N* praticiens à *Ville* » (maquette corrigée).
- **« Accepte de nouveaux patients » sans compteur** : `Facets` n'a pas de répartition `accepting` (contrat ; maquette corrigée).
- **Filtres (n) sous 760 px** : la colonne de filtres se replie en panneau sous un bouton ; la maquette statique ne montre que le 1 440 px (fiche seule).
- **Pagination** = ce que renvoie l'API (`page`, `totalPages`, 20 par page) ; le « 1 2 3 … 5 » de la maquette est illustratif.
- **Mot-clé `q`** : présent dans le contrat (nom, codes de spécialités, ville, présentation, 100 caractères) → champ *Mot-clé* de B-ux en tête du bandeau de recherche (maquette corrigée, états A et C).
- **Spécialités** (M10-02, contrat v0.10.0) : le sélecteur *Spécialité* envoie le paramètre `specialties` (codes de la liste fermée, répétable, au moins un des codes), plus dans `q`. La facette `facets.specialties` (`code`, `labelFr`, `labelEn`, `count`) est disponible : elle est calculée sans le filtre `specialties`, donc elle propose les autres spécialités avec le nombre de résultats que chacune donnerait seule. Les compteurs sont exacts jusqu'à 500 fiches examinées, plafonnés aux 500 premières au-delà (G Q48). Seuls les codes avec au moins une fiche sont rendus : pour afficher aussi les codes à 0, compléter la liste avec `GET /api/public/directory/vocabulary` (compteur 0 pour les codes absents de la facette). Un code hors liste ou désactivé → `422` `VALIDATION_FAILED`, `errors[{field: "specialties", code: "INVALID_VALUE"}]`.
- **Vocabulaire** : `GET /api/vocabulary/directory` exige une session → le layout public ne l'appelle pas. Il utilise `GET /api/public/directory/vocabulary` (M10-01, contrat v0.10.0) : mêmes listes (spécialités et publics actifs, modalités, langues, libellés FR/EN), sans session, limité à 60 requêtes par minute et par IP, `Cache-Control: max-age=60, public`. Plus besoin d'embarquer les libellés dans un fichier de traduction du front.
- **Modalité « à domicile »** (`home`, facette `modalities` du contrat) : dans la colonne de filtres (maquette corrigée).

## Hors périmètre V1
Carte interactive et géolocalisation, photos (`photoUrl` null), avis et classement, prise de rendez-vous, mot-clé par suggestion automatique, signalement d'une fiche (V1.1, G Q26), page d'accueil distincte de l'annuaire.

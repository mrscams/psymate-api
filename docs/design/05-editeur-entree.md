# Écran 5 · Éditeur d'entrée (panneau)

> Spécification pour agents. Maquette : `05-editeur-entree.html` / `.png`. Palette : Nordique. Document B, écran 5 et B5 ; Document A, module `clinical` (M1-05) ; tâches web M1-08, M2-05, M6-02.
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : « Restaurer » et « valence » retirés de la maquette (écarts déjà notés ci-dessous) ; bloc Séance sans modalité ni durée. Maquette HTML corrigée.

## Objectif
Saisir une entrée en séance en moins de 20 secondes au clavier, la compléter après, la corriger en gardant l'historique. Un seul panneau pour les six types (note, événement, situation, ressenti, séance, médicament). La maquette montre deux états : **A** création d'un ressenti (Anxiété 8/10, « n'a pas dormi », lié à l'événement « Entretien annuel ») par-dessus la vue d'ensemble, barre de capture remplie dans l'en-tête ; **B** correction d'un événement existant (2 versions, hors fenêtre de grâce) avec le panneau « Historique des versions » accolé à gauche.

## Route & module
- Route : panneau ouvert par-dessus `/patients/:id/*` (vue d'ensemble, timeline, graphe) ; ouverture par la touche `N`, le bouton `+`, ou un préfixe `/note` `/evt` `/sit` `/res` `/med` dans la barre de capture ; correction : `?entry=<id>` (le panneau se rouvre au rechargement).
- Création : `POST /api/patients/{id}/entries` (`CreateEntryRequest`) → 201 + `Location: /api/entries/{id}`. Champs minimaux par type : note = `body` ; event = `title` ; situation = `title` + `occurred.from` ; feeling = `feeling.emotionCode` + `feeling.intensity` (0 à 10) ; medication = `medication.molecule` ; session = `session.modality`. `occurred {from, to, precision: exact|day|month|year|period|life_phase, label}` ; `sessionId` absent = séance courante (`SESSION_REQUIRED` s'il n'y en a aucune, `SESSION_INVALID` si elle n'est pas du patient) ; `status draft|final` ; `visibility private|clinical` ; `tags[]` (`UNKNOWN_TAG`) ; `links[]` (20 au plus, tout ou rien, `links[i].targetId / linkType / direction`). `authorType` et `submissionId` ne s'envoient **jamais** (`UNKNOWN_FIELD`, A11).
- 422 `VALIDATION_FAILED` avec `errors[] {field, code, message}` : `REQUIRED`, `OUT_OF_RANGE`, `INVALID_VALUE`, `INVALID_FORMAT`, `INVALID_TIME` (`occurred.to` avant `from`, période sans libellé ou sans fin), `UNKNOWN_EMOTION` (`feeling.emotionCode`), `DETAIL_NOT_ALLOWED`, `NOT_ALLOWED_ON_CREATE` (titre, texte, tags, liens sur une séance), `LINK_TYPE_NOT_ALLOWED` (avec `allowedLinkTypes[]`). 409 `SESSION_ALREADY_OPEN` quand on crée une séance alors qu'une est ouverte.
- Correction : `PATCH /api/entries/{id}` (`PatchEntryRequest` : `title, body, occurred, feeling|medication|session, tags, status, visibility, reason`). Champ absent = inchangé ; `occurred` et `tags` remplacés en entier ; le type ne se modifie pas ; `links` refusé (`NOT_SUPPORTED_ON_PATCH`) ; 409 `ENTRY_ARCHIVED`. Règle de grâce (G Q31, `versioning.grace-period = 75m`) : moins de 1 h 15 après la création, même auteur, entrée sans lien ni autre version → la version 1 est modifiée **en place**, sans nouvelle version ; sinon une nouvelle version est créée et `reason` y est conservé. Un changement de tags seul ne crée jamais de version.
- Historique : `GET /api/entries/{id}/versions` → `EntryVersionDto[] {version, title, body, reason, authorId, authorType, createdAt}`, plus récente en premier, sans pagination.
- Séance présélectionnée : `GET /api/patients/{id}/sessions/current` (séance ouverte, sinon dernière clôturée, sinon accueil ; 404 `NO_CURRENT_SESSION`).
- Rail « Lié à… » : à la création, les liens partent dans `links[]` du `POST` ; en correction, chaque lien passe par `POST /api/entries/{id}/links` (`CreateLinkRequest {targetId, linkType, direction outgoing|incoming, rationale ≤ 500}`), 409 `LINK_EXISTS` / `ENTRY_ARCHIVED`, 422 `SELF_LINK`, `DIFFERENT_PATIENT`, `UNKNOWN_LINK_TYPE`, `LINK_TYPE_NOT_ALLOWED`.
- Vocabulaire : `GET /api/vocabulary/emotions` (29 globales + personnalisées actives du praticien, `{code, labelFr, family, intensityLevel, scope}`), `GET /api/vocabulary/entry-types`, `GET /api/vocabulary/link-types` (matrice source/cible). Création d'une émotion en ligne : `POST /api/me/emotions {labelFr, family}` → 201, disponible aussitôt dans la liste ; 409 `EMOTION_LABEL_EXISTS`.
- Store : `EntryEditorStore` : `mode (create|edit)`, `type`, `draft`, `currentSession`, `emotions`, `linkCandidates`, `versions`, `graceDeadline`, `saving`, `errors` (par champ).

## Structure
1. **Barre d'application** et **en-tête patient** (M1-07) restent visibles derrière le voile `rgba(30,38,51,0.18)` ; l'en-tête montre la séance courante (« Séance du 24 oct. · en cours depuis 18 min », bouton « Clôturer la séance ») et réserve la place de la barre de capture et de son aperçu interprété (M6-02).
2. **Panneau** `role="dialog"` 480 px à droite, en-tête « Nouvelle entrée » / « Modifier l'entrée · v2 », bouton `×`. En correction, bandeau `#FBF1DC` sous l'en-tête : « Modifiée il y a 2 h · hors fenêtre de grâce (1 h 15) : cette correction créera la version 3 » (ou « dans la fenêtre de grâce : la version 1 sera modifiée en place » avant l'échéance).
3. **Type** : six puces (forme + couleur), une seule pressée ; verrouillées en correction.
4. **Champ principal** selon le type : ressenti = combobox Émotion (pastille de famille, liste filtrée par préfixe, dernière ligne « Créer l'émotion personnalisée « … » » + famille) + curseur d'intensité 1–10 avec valeur en JetBrains Mono + texte ; note/événement/situation = titre et/ou texte ; médicament = molécule + dose ; séance = durée + modalité.
5. **Quand** : bouton « Maintenant », précision (exact · jour · mois · année · période · phase de vie), date mono ; situation : début et « en cours / fin ».
6. **Séance** : séance courante présélectionnée, modifiable (liste des séances du patient, lue par `GET /api/patients/{id}/timeline?types=session`) ; chaque option = « Séance du 24 oct. », « (en cours) » pour la séance ouverte, sans lieu, modalité ni durée (absents de `TimelineItemDto`).
7. **Lié à…** : recherche + cinq entrées récentes ou actives, case à cocher et type de lien proposé (pointillé `#B9C2D1` tant qu'il n'est pas confirmé, plein `--primary` une fois coché). En correction : liens existants en lecture, bouton « Modifier » vers l'éditeur de lien (écran 6).
8. **Plus** (`<details>` replié) : visibilité clinique/privée, tags. En correction : champ « Motif de la correction » (`reason`).
9. **Pied** : « Enregistrer » (`⏎`, « Enregistrer la version 3 » en correction), « Brouillon » (`Ctrl+S`), « Annuler » (`Échap`), ligne « Version 2 · Historique (2) » si plus d'une version.
10. **Historique des versions** (`<aside>` 420 px, accolé à gauche du panneau, état B) : liste `v2 · 24 oct. 15:12 · Claire M. · courante · motif`, `v1 · 24 oct. 14:03`, puis différences champ par champ (titre, texte, date) ancien barré / nouveau surligné `#E3EAF8`, ligne « Inchangés : … ».

## Interactions
- `Tab` suit l'ordre des blocs 3 → 9 ; `⏎` enregistre (`Ctrl+⏎` depuis le texte multi-ligne) ; `Ctrl+S` enregistre en `status: draft` ; `Échap` ferme **en conservant le brouillon** (local, renvoyé à la réouverture) ; `Espace` coche une entrée du rail.
- Erreur 422 : message sous le champ fautif (`errors[].field`), le reste reste saisi ; erreur réseau : brouillon gardé localement et renvoyé.
- Émotion inconnue : la dernière ligne de la liste crée l'émotion (`POST /api/me/emotions`) sans quitter le panneau, puis la sélectionne.
- Correction : le bandeau se calcule depuis `createdAt` de la version 1 + 75 min ; après l'échéance, `reason` est proposé et le pied annonce le numéro de la future version.

## Tokens
- Puces de type : note rectangle `#7F8EA8`, événement losange `#3A63C2`, situation barre `#2F4F73`, ressenti cercle `#8B7BD6`, séance carré arrondi contour `#3E8E7E`, médicament capsule `#B07A2A` ; puce pressée : teinte du type (ressenti `#ECE8F8` / `#4E3F9E` ; autres `#E3EAF8` / `#2C4C99`).
- Lavande `#8B7BD6` réservé au ressenti (curseur, pastille de type) ; pastilles de famille d'émotion : `--emotion-*` (peur `#7A68C9`). Jamais de rouge sur une émotion ou un patient.
- Champ actif : bordure `--primary` + halo `#E3EAF8` ; bandeau de grâce `--warning-bg #FBF1DC` / `--warning #7A5A12` ; version courante `#E3EAF8` / `#2C4C99` ; valeurs, dates, raccourcis en JetBrains Mono.

## Accessibilité
- `role="dialog" aria-modal="true" aria-labelledby`, focus piégé dans le panneau, retour du focus à l'élément déclencheur à la fermeture ; bandeau de grâce en `role="status"` et `aria-describedby` du dialogue.
- Type et précision : `role="group"` + `aria-pressed` ; émotion : `role="combobox" aria-expanded aria-controls aria-activedescendant` sur un `role="listbox"` ; intensité : `role="slider" aria-valuemin/max/now/valuetext` pilotable aux flèches ; rail : `<fieldset>` + cases à cocher avec `<label>`.
- Historique : `<aside aria-labelledby>`, `<ol>` avec `aria-current` sur la version courante, différences en `<dl>` avec `<s>` pour l'ancien ; aucune information portée par la couleur seule.

## Critères d'acceptation
- [ ] C2 M1-08 : saisir un ressenti 8/10 au clavier (`N` → Ressenti → « anx » → `↓ ⏎` → 8 → texte → `⏎`) en moins de 20 s ; corriger l'entrée après la grâce et voir « 2 versions » dans le pied et l'historique.
- [ ] Panneau ouvert pendant une séance en cours : la séance courante est présélectionnée (`sessions/current`) et l'entrée créée porte son `sessionId`.
- [ ] Patient sans aucune séance : le `POST` renvoie 422 `SESSION_REQUIRED` et le panneau affiche le message sous le bloc Séance, le reste de la saisie conservé.
- [ ] Correction moins de 1 h 15 après la création, même auteur, entrée sans lien : `GET /versions` renvoie toujours une seule version, le bandeau l'avait annoncé.
- [ ] « Créer l'émotion personnalisée « … » » crée l'émotion (`POST /api/me/emotions`), la sélectionne, et elle est proposée dans un second ressenti sans rechargement ; un libellé déjà pris affiche 409 `EMOTION_LABEL_EXISTS` sous le champ.
- [ ] `Échap` puis réouverture : le brouillon (type, émotion, intensité, texte, liens cochés) est intact ; `Ctrl+S` crée l'entrée en `draft`, visible avec la pastille « brouillon ».
- [ ] Rail : cocher « Entretien annuel » depuis un ressenti pose un lien `triggers` en `direction: incoming` dans la même transaction ; un type hors matrice n'est jamais proposé.
- [ ] Aucune requête ne contient `authorType` ni `submissionId` (test unitaire du store).

## Écarts avec la maquette
- « Restaurer une version » n'existe pas dans le contrat : `POST /api/entries/{id}/restore` ne restaure qu'une entrée **archivée** (`archivedAt` null), pas une version. L'historique est en lecture seule. **Maquette corrigée (UX-F1)** : lien « Restaurer » et phrase d'explication retirés.
- Les types de liens proposés par défaut dans le rail suivent la matrice E5.3 (`docs/E-modele-donnees.md`, G Q12) et non une règle inventée : `triggers` E/S/X → F/S/E (« déclenché par »), `caused_by` F/S/E → E/S, `worsens` S/E/M → F/S (« aggravé par »), `relieves` E/S/M/X → F/S, `co_occurs` symétrique N/E/S/F/M, `follows` N/E/S/F → N/E/S/F (« fait suite à » ; la maquette écrit « suit », à corriger), `part_of` E/F/N → S/X, `treats` M → S/F (« traité par »). Le premier type autorisé pour le couple (type cible, type de l'entrée) est proposé, les autres offerts dans le menu ; source de vérité : `GET /api/vocabulary/link-types`.
- La barre de commande et son aperçu interprété (texte « anx 8 n'a pas dormi > entretien », ligne « Ressenti · Anxiété 8/10 · … »), montrés dans l'en-tête de l'état A, sont la tâche **M6-02** ; cette fiche ne fait que réserver leur place dans l'en-tête (M1-07). Le rail « Lié à… » est M2-05.
- Les textes des versions, le motif de correction et la « valence » du bloc Plus sont des exemples de seed, pas des champs du contrat (la valence n'existe pas dans `EntryFeelingRequest`). **Maquette corrigée (UX-F1)** : « valence » retirée du bloc Plus.
- **Bloc Séance sans lieu (UX-F1)** : « · cabinet », « · 55 min » et « · clôturée » retirés des options de séance de la maquette (états A et B) : la liste vient de la timeline, qui ne porte ni modalité ni durée.

## Hors périmètre V1
Restauration d'une version, `authorType` autre que `practitioner` et entrées du compagnon patient (A11), suggestions de liens par l'IA (provenance `ai_suggested`, V2), modification du type d'une entrée, pièces jointes, saisie tactile optimisée (formulaire guidé B5 alternative B derrière `Ctrl+⏎`, lot 6).

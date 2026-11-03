# Fiche 00 · Composants partagés (shell, en-têtes, layouts, panneaux, états)

> Spécification pour agents. Pas de maquette propre : chaque composant est visible dans les maquettes citées. Palette : Nordique. Document B, B2 (navigation, en-tête patient permanent), B7 ; `design/README.md` « Conventions communes ». Posés une fois par **M1-07** (shell praticien) et **M0-07** (layout public) ; la barre admin par **M7-07**. Toute fiche d'écran suppose ces composants et ne les redéfinit pas.

## Objectif
Qu'un seul agent construise la barre d'application, l'en-tête patient, la navigation Mon compte, le layout public, la barre admin, le panneau latéral et le composant « état », dans `src/app/shared/ui/*` et `src/app/core/*`, pour que les écrans suivants les réutilisent sans les réinventer. Règle : **un écran ne modifie pas un composant partagé** ; s'il lui manque quelque chose, il ouvre une tâche séparée.

## Emplacement et stores
- `core/auth` : `SessionStore` (chargé par `GET /api/auth/me` au démarrage : identité, profession, `verificationStatus`, et, avec le contrat `v0.10.0`, `role`, `twoFactorEnabled`, `passwordChangeRequired`) ; gardes `authGuard` (401 → `/connexion?next=`), `roleGuard('admin')` (praticien sur `/admin/**` → `/patients`), redirections d'état : `email_pending` → `/confirmer-email`, `rejected` / `suspended` → `/compte-suspendu` (fiche 27).
- `core/preferences` : `PreferencesStore` (M1-16, fiche 14) : chargé au login, optimiste, `localStorage.prefs` = cache de démarrage seulement ; toutes les bascules visibles écrivent ses clés.
- `core/shortcuts` : registre des raccourcis globaux `N` `/` `L` `1` `2` `3` `M` `Échap` `?` (B2), désactivés quand un champ a le focus.
- `shared/ui/app-bar`, `shared/ui/patient-header`, `shared/ui/account-nav`, `shared/ui/public-layout`, `shared/ui/admin-bar`, `shared/ui/side-panel`, `shared/ui/state`, `shared/ui/toast`, `shared/ui/confirm-dialog`, `shared/ui/status-banner`.

## Composants

### 1. Barre d'application (praticien) — maquettes 01, 02, 10, 14
- 48 px, fond `#FFFFFF`, bordure basse `#D6DCE6` ; logo + **PsyMate** (jamais « Suivi »), section courante (« Patients », « Mon compte »), champ de recherche globale ouvert par `/` (dans un écran patient il devient la recherche du dossier, fiche 07), bouton langue FR/EN, avatar avec menu (Mon compte, Déconnexion `POST /api/auth/logout`).
- Sous la barre, quand `verificationStatus ≠ approved` : **bandeau d'état global** ambre (`#FBF1DC` / `#7A5A12`) de la fiche 13 (« Votre dossier professionnel est en cours de vérification · Voir le dossier »), sur toutes les pages praticien, jamais sur les pages publiques ni admin.

### 2. En-tête patient permanent — maquettes 02, 03, 04, 05, 07, 16 (B2)
- Sous la barre, sur toutes les routes `/patients/:id/**` et au-dessus de tout panneau : nom affiché, âge, « suivie depuis », drapeaux (puce ambre `#B8862E` / `#FBF1DC`, jamais rouge), **séance courante** (`GET /api/patients/{id}/sessions/current` : « Séance du 24 oct. · en cours depuis 18 min », sinon « Dernière séance : 17 oct. ▾ »), bouton **Démarrer la séance** (`POST /api/patients/{id}/sessions/start`) ou **Clôturer** (`POST /api/sessions/{id}/close`, durée par défaut `sessions.defaultDurationMin`), onglets **1 Vue d'ensemble · 2 Timeline · 3 Graphe** (touches `1` `2` `3`, `aria-current`), bascule **Vue : Simple · Complète** à droite des onglets (écrit `overview.view`, visible seulement sur l'onglet 1).
- **Barre de capture rapide** repliée (« Noter quelque chose… ») : en M1-07 elle ouvre l'éditeur d'entrée (fiche 05) ; la grammaire et l'aperçu sont M6-02. La place est réservée dès M1-07.
- L'état de chaque onglet (filtres, zoom, position) est conservé en mémoire pendant la session de travail : changer d'onglet et revenir ne recharge rien (B2).
- Emplacement réservé, non dessiné en V1 (B9) : marqueur « rapporté par le patient » à côté du nom ; bloc « Reçu du patient » dans la grille de l'écran 2.

### 3. Navigation Mon compte — maquettes 10, 13, 14, 17, 18, 19
- Colonne gauche 220 px : nom, profession · ville ; entrées **Profil public** (pastille verte « publié » si `published`), **Préférences**, **Émotions personnalisées**, **Dossier professionnel** (pastille d'état : en attente / complément demandé / approuvé), **Compte et sécurité**, **Données et export**. À 390 px : onglets horizontaux.
- Routes : `/mon-compte/profil-public`, `/mon-compte/preferences`, `/mon-compte/emotions`, `/mon-compte/dossier-professionnel`, `/mon-compte/securite`, `/mon-compte/donnees`.

### 4. Layout public — maquettes 08, 09, 11, 12, 24, 25, 26, 27
- Sans garde ni `SessionStore` obligatoire ; bandeau : logo PsyMate + « Annuaire des praticiens », liens *Annuaire*, *Comment ça marche*, bouton secondaire **Espace praticien** (→ `/connexion`, ou `/patients` si déjà connecté). Pied : mentions légales, confidentialité, accessibilité. **Pas de numéros d'urgence** (G Q20). Prérendu (SSR ou prerender) pour 08, 09, 24.
- Les formulaires publics (connexion, inscription, mot de passe oublié, contact) partagent le même conteneur centré 480 px et la même validation inline (`errors[]` sous les champs, résumé en haut avec liens vers les champs).

### 5. Barre d'administration — maquettes 20, 21, 22, 28
- Fond `#1E2633`, texte `#E6EAF2`, étiquette `ADMIN`, onglets **Validation (n) · Praticiens · Journal · Vocabulaire** (pas de Signalements en V1, G Q26), rappel « Aucune donnée patient n'est accessible depuis l'administration », avatar ambre. Module lazy `/admin/**` derrière `roleGuard('admin')` ; aucun import d'un service ou composant des features patient (règle testée, C6).

### 6. Panneau latéral — maquettes 05, 06, 18
- `role="dialog"` `aria-modal="true"` `aria-labelledby`, 480 px (420 px pour l'éditeur de lien), ancré à droite, page visible derrière sous un voile `rgba(30,38,51,.45)` ; piège de focus, retour du focus à l'élément déclencheur, `Échap` ferme (le brouillon est conservé, B2) ; pied fixe avec l'action principale cobalt et les raccourcis en JetBrains Mono. Un seul panneau ouvert à la fois ; l'éditeur de lien peut s'ouvrir par-dessus l'éditeur d'entrée (pile de deux).

### 7. Composant « état », toasts, confirmation — planche 29
- `shared/ui/state` : entrées `kind = empty | loading | error`, `title`, `message`, `action?`, `details?` ; `aria-busy` et `role="status"` en chargement, `role="alert"` en erreur ; squelettes gris `#E4E8F0` sans animation obligatoire ; illustration SVG gris-bleu. Chaque écran l'utilise pour ses trois états (C5).
- `shared/ui/toast` : succès / information / avertissement, 6 s, `aria-live="polite"`, action optionnelle (« Annuler ») ; jamais d'erreur système en toast (elle va dans le composant état ou sous le champ).
- `shared/ui/confirm-dialog` : variante destructive avec le nom à retaper (archiver un patient), bouton `#9A2F22` ; le patient DEMO se supprime sans retaper le nom (fiche 29).
- Erreurs HTTP transverses dans un intercepteur : `401` → `/connexion?next=` ; `403 EMAIL_NOT_CONFIRMED` → `/confirmer-email` ; `403 ACCOUNT_SUSPENDED` → `/compte-suspendu` ; `423` → compte à rebours `Retry-After` ; `429` → message avec `Retry-After`, saisie conservée ; hors ligne → bandeau « Connexion perdue · vos brouillons sont conservés ».

## Tokens
- Surfaces : `#EEF1F6` marges, `#FFFFFF` sous le texte, bordures `#D6DCE6` ; texte `#1E2633` / `#5A6679` ; un seul élément cobalt `#3A63C2` par zone ; lavande `#8B7BD6` réservée aux ressentis ; drapeaux ambre, jamais rouge sur un patient. Variables CSS de `Etude-couleurs-Nordique.md` §8, thème `data-theme` light / dark / soft (M6-04), appliqué par `PreferencesStore.theme`.
- Formes par type d'entrée (icônes partagées `shared/ui/entry-icon`) : note rectangle, événement losange, situation barre, ressenti cercle, séance carré arrondi, médicament capsule.

## Accessibilité
- Landmarks : `<header>` (barre), `<nav aria-label>` (onglets, Mon compte, admin), `<main>`, `<aside>` ; lien « Aller au contenu ».
- Tous les raccourcis ont un équivalent cliquable ; focus visible 2 px cobalt ; cibles 44 px sur tablette ; zoom 200 % sans perte ; `prefers-reduced-motion` respecté.
- Textes via `$localize`, français d'abord (G Q23) ; libellé « patient » remplacé par « client » selon `vocabulary.subjectLabel`.

## Critères d'acceptation
- [ ] Un composant de `shared/ui/*` n'importe rien de `features/**` (règle ESLint `import/no-restricted-paths`, testée).
- [ ] La barre, l'en-tête patient et les onglets sont rendus une seule fois par route ; changer d'onglet puis revenir conserve filtres, zoom et position (e2e).
- [ ] `Démarrer la séance` crée la séance et l'en-tête passe à « en cours » sans rechargement ; `Clôturer` propose la durée par défaut des préférences.
- [ ] Le bandeau d'état global apparaît sur toutes les pages praticien tant que `verificationStatus ≠ approved`, jamais sur `/annuaire` ni `/admin`.
- [ ] Un praticien qui tape `/admin` arrive sur `/patients` ; un visiteur sans session sur `/patients` arrive sur `/connexion?next=/patients`.
- [ ] `Échap` ferme le panneau, rend le focus au déclencheur et conserve le brouillon.
- [ ] Chaque composant a ses trois états (vide, chargement, erreur) via `shared/ui/state` ; axe-core 0 violation « serious » sur la page patient, Mon compte, une page publique et une page admin.

## Hors périmètre V1
Marqueur « rapporté par le patient » et bloc « Reçu du patient » (B9, compagnon) ; onglet Signalements (V1.1, G Q26) ; agenda dans l'en-tête (G Q25) ; menu de langue au-delà de FR/EN.

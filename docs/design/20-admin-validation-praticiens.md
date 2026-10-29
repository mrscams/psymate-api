# Écran 20 · Administration › Validation des praticiens

> Spécification pour agents. Maquette : `20-admin-validation-praticiens.html` / `.png`. Palette : Nordique (barre sombre pour distinguer l'espace admin). Document A : module `identity` (rôle `admin`, sous-domaine *verification*) ; nouveau module front `admin` (lazy, garde de rôle). Pendant des écrans 12 et 13.

## Objectif
Un administrateur de la plateforme passe en revue les dossiers soumis, consulte les justificatifs, coche une liste de contrôle, puis **approuve**, **demande un complément** ou **refuse**. Chaque décision est tracée et notifiée. L'administration n'a **aucun accès aux données patients**.

## Rôles et sécurité
- `practitioner.role ENUM('practitioner','admin')` ; un admin est un compte distinct (pas un praticien promu) avec 2FA **obligatoire**.
- Routes `/admin/**` : garde `role = admin` côté front **et** `@PreAuthorize("hasRole('ADMIN')")` côté API ; les contrôleurs admin vivent dans `identity.admin` et n'ont aucune dépendance vers `patients`, `clinical`, `linking`, `overview` (vérifié par Spring Modulith / ArchUnit).
- Journal : toute décision écrit `verification_decision {id, practitioner_id, admin_id, decision ENUM(approved, needs_more, rejected), internal_note, message_to_practitioner, checklist JSONB, decided_at}` — append-only.
- Visionneuse : URL signée 5 min vers le stockage chiffré ; pas de téléchargement par défaut (bouton « Ouvrir » = nouvel onglet signé) ; accès consigné (`document_access_log`).

## Route & API
- Route : `/admin/validation` (liste + dossier sélectionné), `/admin/validation/:practitionerId`.
- API : `GET /api/admin/verifications?status=under_review|needs_more|decided&sort=oldest&cursor=` → `{items[{practitionerId, displayName, profession, city, documentsCount, submittedAt, overdue, missingHints[]}]}` ; `GET /api/admin/verifications/{id}` → identité, coordonnées, n° pro, documents (avec URL signée), historique des décisions ; `PATCH /api/admin/verifications/{id}/documents/{docId} {reviewedStatus}` ; `POST /api/admin/verifications/{id}/decision {decision, internalNote?, messageToPractitioner?, checklist}`.
- Règles : `needs_more` et `rejected` exigent `messageToPractitioner` non vide (`400` sinon) ; `approved` exige que tous les documents soient `conform` ou que la checklist complète soit cochée (`409` sinon, contournable avec `force: true` + note interne obligatoire).
- Effets : `approved` → `verification_status = approved`, `verified_at/by`, e-mail « dossier validé », badge actif ; `needs_more` → `status = needs_more`, e-mail avec le message, le praticien peut rajouter des documents et resoumettre ; `rejected` → `status = rejected`, compte suspendu (sessions révoquées), e-mail avec motif et voie de recours (adresse support).
- SLA : `overdue = now − submittedAt > 2 jours ouvrés` (affiché en rouge), rappel quotidien aux admins par e-mail.

## Structure
1. **Barre d'application admin** (fond `#1E2633`) : logo + étiquette `ADMIN`, onglets *Validation (n)* · Praticiens · Signalements · Vocabulaire · Journal, rappel « Aucune donnée patient n'est accessible depuis l'administration », avatar admin ambre.
2. **File d'attente** (gauche, 340–420 px) : titre « À vérifier · n », filtre (En attente / Complément demandé / Traités cette semaine), liste ordonnée du plus ancien au plus récent : initiales (teinte par profession : psychologue `#E3EAF8`, psychiatre `#E1E9F2`, coach `#E2F1EE`), nom, profession · ville · nombre de documents, indice de manque en ambre, ancienneté en mono (rouge si hors délai). Sélection = bordure primaire + halo.
3. **Dossier ouvert** (droite) :
   - **En-tête** : initiales, nom, puce profession, compte créé le, e-mail confirmé, soumis il y a ; `dl` e-mail, téléphone, n° pro déclaré, cabinet.
   - **Justificatifs · n** : liste avec intitulé · établissement · année, type · format · taille, case **Conforme** par document ; document sélectionné surligné.
   - **Visionneuse** : nom de fichier mono, pagination, zoom ±, pivoter, « Ouvrir » ; zone grise avec le rendu du document.
   - **Liste de contrôle** : nom concordant, diplôme ↔ profession, n° retrouvé dans le registre public (lien externe ARS/RPPS), documents lisibles et non altérés.
   - **Décision** : note interne (jamais envoyée), message au praticien (obligatoire si complément ou refus), boutons **Approuver** (vert `#1E6B45`), *Demander un complément* (ambre), *Refuser et suspendre* (rouge) ; rappel des effets.
4. À 390 px : file en haut (liste déroulante), dossier en dessous, visionneuse pleine largeur.

## Interactions
- Sélection d'un dossier dans la file → chargement du détail sans quitter la page ; `↑ ↓` naviguent ; `Entrée` ouvre.
- Cocher « Conforme » envoie le `PATCH` immédiatement.
- Approuver avec checklist incomplète → confirmation inline « 2 points non cochés — approuver quand même ? » (nécessite la note interne).
- Après décision : le dossier sort de la file, le suivant est sélectionné, toast « Claire Moreau approuvée · e-mail envoyé ».
- Onglet « Praticiens » (hors maquette) : liste de tous les comptes avec statut, recherche, suspension/réactivation ; « Journal » : décisions et accès aux documents.

## Tokens
- Barre admin `#1E2633`, liens `#C9D2E3`, actif `rgba(255,255,255,.12)`, étiquette `ADMIN` `#FBF1DC` / `#1E2633`.
- Décision : approuver `#1E6B45` plein ; complément texte `#7A5A12` ; refus texte `#9A2F22` ; les deux derniers en contour pour ne pas inviter au clic.
- Hors délai : texte `#9A2F22` (jamais seul : l'ancienneté est aussi écrite).

## Accessibilité
- File = `<ol aria-label>` avec `aria-current` ; cases « Conforme » et checklist = `<input type="checkbox">` dans `<label>`.
- Visionneuse : `role="img" aria-label` + bouton « Ouvrir » (vrai document pour lecteurs d'écran/zoom natif).
- Boutons de décision : libellés explicites, pas d'icône seule ; confirmation inline accessible (`role="alertdialog"` si modale).

## Critères d'acceptation
- [ ] Un praticien (`role = practitioner`) sur `/api/admin/**` → `403` ; sans 2FA, un admin ne peut pas se connecter.
- [ ] ArchUnit : aucune dépendance de `identity.admin` vers les modules cliniques.
- [ ] `POST decision {needs_more}` sans message → `400` ; avec message → `status = needs_more`, e-mail Mailpit contenant le message.
- [ ] `approved` → badge visible sur la fiche publique dès publication ; `rejected` → sessions du praticien révoquées, login `403 suspended`.
- [ ] Chaque ouverture de document crée une ligne `document_access_log` avec admin, document, horodatage.
- [ ] Dossier soumis il y a 3 jours ouvrés → `overdue = true`, affiché en rouge.

## Hors périmètre V1
Vérification automatique des registres, workflow à deux validateurs, délégation à des modérateurs externes, tableau de bord statistique admin.

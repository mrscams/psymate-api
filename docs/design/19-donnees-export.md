# Écran 19 · Données et export

> Spécification pour agents. Maquette : `19-donnees-export.html` / `.png`. Palette : Nordique. Document A, module `clinical` (M6-05, G Q18) ; complète `27-compte-suspendu.md` pour un compte actif. Tâche web : **M6-06** (nouvelle, à ajouter au plan C dans M6).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : la maquette suit désormais le contrat : plus de mot de passe d'archive, de progression, d'estimation ni d'« Exports récents » ; un seul bloc « Dernier export » ; nom du fichier attendu affiché à côté d'« Exporter ». Voir « Écarts avec la maquette ».

## Objectif
Permettre au praticien de récupérer ses dossiers dans des formats ouverts : un patient à la volée (PDF ou Markdown), ou tout le compte en une archive zip préparée en arrière-plan et téléchargeable 7 jours. Un export ne modifie et n'efface rien : rien n'est jamais supprimé physiquement (G Q37). Le même mécanisme sert au compte suspendu ou refusé (fiche 27) ; ici, il est présenté pour un compte actif et connecté.

## Route & module
- Route : `/mon-compte/donnees` (navigation « Mon compte », entrée « Données et export ») ; point d'entrée secondaire « Exporter ce dossier ▾ » dans la fiche patient (16) et dans l'en-tête patient (01/02/04/07), menu PDF / Markdown.
- Export patient : `GET /api/patients/{id}/export?format=pdf|md` → fichier en pièce jointe (`export-nom-AAAAMMJJ.pdf` ou `.md`), généré à la volée, séances et entrées en ordre chronologique avec le texte de leur version courante ; entrées privées et archivées jamais exportées, lien vers une entrée non exportée omis ; PDF au thème clair ; langue = `Accept-Language`. Erreurs : 401 `UNAUTHENTICATED`, 403 `PATIENT_FORBIDDEN` / `EMAIL_NOT_CONFIRMED`.
- Export de compte : `POST /api/me/export` → 202 `{jobId, statusUrl}` (compte actif : session, corps ignoré ; compte suspendu ou refusé : `{email, password, otp?}` sans session, voir fiche 27). 409 `EXPORT_IN_PROGRESS` si un job est déjà en file ou en cours (un seul par compte) ; 423 `ACCOUNT_LOCKED` après 5 échecs d'identification.
- État : `GET /api/me/export/{jobId}` → `ExportStatusDto` `{status: queued|running|done|failed, requestedAt, finishedAt, expiresAt, downloadUrl, error}` ; `downloadUrl` seulement si `done` et lien non expiré ; 404 `EXPORT_NOT_FOUND` pour un job inconnu ou d'un autre praticien.
- Fichier : `GET /api/me/export/{jobId}/file?exp=&sig=` (URL signée de `downloadUrl`, valable 7 jours) → zip `export-psymate-AAAAMMJJ.zip` contenant `patients.md` puis un `.md` et un `.pdf` par patient ; 403 `SIGNED_URL_INVALID` ; 410 `EXPORT_EXPIRED` passé l'échéance ou après purge.
- Store : `AccountExportStore` : `lastJobId` (conservé côté client, `localStorage`), `status`, `expiresAt`, `downloadUrl`, `error`, `polling`.

## Structure
1. **Barre d'application** (fil d'Ariane Patients / Mon compte) et **navigation « Mon compte »** (colonne 220 px, « Données et export » actif).
2. **Exporter un dossier patient** : combobox patient avec recherche (`role="combobox"` + `listbox`, patients archivés listés en gris), groupe PDF | Markdown (`aria-pressed`), bouton primaire « Exporter » suivi du **nom du fichier attendu** en JetBrains Mono (« Fichier `export-marie-d-20261026.pdf` », recalculé au choix du patient et du format), rappel « entrées privées jamais exportées · thème clair · ordre chronologique ».
3. **Exporter tout mon compte** : trois tuiles (contenu de l'archive, archive conservée chiffrée puis remise en clair au téléchargement, lien 7 jours), bouton « Préparer l'export » ; désactivé avec note quand un job est `queued` ou `running`.
4. **Dernier export** : une seule ligne (le dernier job connu), date de demande en JetBrains Mono, état (pastille) et action ; pas de liste d'exports.
5. **Vos données** : hébergement, journal des accès (texte seul, sans lien), rien n'est effacé, lien « Comment ça marche » (24).
6. **État B** : dialogue de confirmation « Préparer l'export du compte » (`role="dialog"`, `aria-modal`) : contenu de l'archive, « lien valable 7 jours », avertissement « données de santé », bouton primaire « Lancer l'export », Annuler. Ni mot de passe, ni estimation de temps ou de taille.
7. **État C** : aside de la fiche 16 avec le bouton « Exporter ce dossier ▾ » (menu PDF / Markdown) et la note « Entrées privées exclues ».

## Interactions
- « Exporter » (patient) : navigation vers l'URL de l'export (téléchargement direct, pas de job, pas d'historique) ; le bouton reste inactif tant qu'aucun patient n'est sélectionné.
- « Préparer l'export » ouvre l'état B ; « Lancer l'export » envoie `POST /api/me/export`, mémorise `jobId`, ferme le dialogue et interroge `GET …/{jobId}` toutes les 3 s jusqu'à `done` ou `failed` ; 409 → message « Un export est déjà en cours » et affichage du job courant.
- Ligne d'état : `queued` « En attente » ; `running` « En préparation » (indicateur indéterminé) ; `done` « Prêt · lien valable jusqu'au {expiresAt} » + bouton « Télécharger » vers `downloadUrl` ; `failed` « Échec » + `error` + « Relancer » ; expiré (`done` sans `downloadUrl`, ou 410 au téléchargement) « Expiré » + « Relancer » (nouveau `POST`).
- Quitter la page n'annule rien : au retour, le store reprend le `lastJobId` et réinterroge l'état ; 404 → la ligne disparaît.

## Tokens
- Pastilles d'état : prêt `#E4F2EA` / `#1E6B45` (`--success`), en préparation `#FBF1DC` / `#7A5A12` (`--warning`), expiré et en attente `#E4E8F0` / `#5A6679`, échec `#FBE9E7` / `#9A2F22` (`--danger`, texte seulement : aucune action destructive sur cet écran).
- Boutons : primaire `#3A63C2` / `#FFFFFF`, secondaire bordure `#B9C2D1`, désactivé `#B9C2D1` ; groupe de format actif `#E3EAF8` / `#2C4C99`.
- Dates, noms de fichiers et codes d'erreur : JetBrains Mono.

## Accessibilité
- Sélecteur de patient : `role="combobox"` avec `aria-expanded`, `aria-controls`, liste `role="listbox"` / `option` + `aria-selected` ; format en `role="group"` avec `aria-pressed`.
- Ligne d'état du job dans un conteneur `aria-live="polite"` ; indicateur `role="progressbar"` ; bouton désactivé relié à sa note par `aria-describedby`.
- Dialogue : `role="dialog"`, `aria-modal="true"`, `aria-labelledby` / `aria-describedby`, focus piégé, `Échap` ferme ; menu « Exporter ce dossier » : `aria-haspopup="menu"`, `role="menu"` / `menuitem`, flèches et `Échap`.
- Parcours clavier complet sans souris (critère Playwright).

## Critères d'acceptation
- [ ] Export PDF de Marie D. (seed) : fichier ouvrable, séances et entrées en ordre chronologique, thème clair (C2 de M6-05).
- [ ] Un patient avec une entrée privée : l'entrée est absente du PDF et du Markdown, et le lien qui pointait vers elle est omis.
- [ ] Lancer un export de compte depuis l'état B, attendre `done`, télécharger le zip : il contient `patients.md` et un `.md` + un `.pdf` par patient.
- [ ] Lien expiré : la ligne affiche « Expiré » avec « Relancer » ; un 410 au téléchargement bascule la ligne dans cet état sans erreur bloquante.
- [ ] Second `POST` pendant un job `queued`/`running` : 409 affiché, bouton « Préparer l'export » désactivé, job courant visible.
- [ ] Parcours clavier `Tab → combobox → ↓ → Entrée → format → Exporter` et `Préparer → dialogue → Lancer → Télécharger` sans souris.

## Écarts avec la maquette
Le mot de passe d'archive, la progression par patient, l'estimation de temps/taille, l'e-mail de fin et la liste « exports récents » ne sont pas dans le contrat → en V1 : un seul job visible (le dernier, via son id conservé côté client), états queued / running / done (lien jusqu'au …) / failed / expiré (410) ; le format s'appelle `md` dans l'API, affiché « Markdown ». Le journal des accès de la carte « Vos données » n'a pas d'endpoint : texte seul, sans lien.
- **UX-F1 (11 novembre 2026), maquette corrigée** : tuile et bloc « Mot de passe de l'archive » retirés (la case « Je l'ai enregistré » et sa note aussi) ; « Archive zip chiffrée » devient « Archive zip » et la deuxième tuile « Conservée chiffrée » (contrat de `POST /api/me/export` : zip conservé chiffré, remis déchiffré par `…/file`) ; « Exports récents · 3 » devient « Dernier export » avec une seule ligne (`running`, indicateur indéterminé, sans « 14 patients sur 23 · environ 1 min ») ; estimations de temps et de taille retirées du dialogue ; « vous recevrez un e-mail » retiré ; lien « Voir dans Compte et sécurité » du journal des accès retiré. Le nom de fichier attendu, déjà dans la maquette, est maintenant demandé par la fiche (Structure 2).
- **« 23 patients (dont 2 archivés) »** dans le dialogue : aucune route d'export ne renvoie ce décompte ; le front peut le tirer de la liste des patients ou l'omettre. Maquette inchangée.

## Hors périmètre V1
Historique des exports côté serveur, mot de passe d'archive choisi par le praticien, notification par e-mail, export sélectif (période, types), export au format JSON, inclusion des entrées privées sur demande.

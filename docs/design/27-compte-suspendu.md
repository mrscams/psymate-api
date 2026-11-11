# Écran 27 · Compte suspendu / refusé

> Spécification pour agents. Maquette : `27-compte-suspendu.html` / `.png`. Palette : Nordique, layout public. Module `identity` (`verification_status = rejected` ou `suspended`), `identity.admin` (recours, **M1-14b**, G Q28), `clinical` (export du compte, **M6-05**, G Q18). ADR-12 du document A (suspension = lecture seule, alternative « tout bloquer » documentée).
>
> Mis à jour le 29 octobre 2026 (G) : recours dans la tâche M1-14b ; export du compte = table `export_job` (E3.6, migration V11) dans M6-05, zip chiffré (Markdown + PDF par patient + liste des patients), lien valable 7 jours ; aucun agenda ni synchronisation Google en V1 (Q25).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : la page est **sans session**, comme le contrat l'impose (le login d'un compte refusé ou suspendu répond `403 ACCOUNT_SUSPENDED` et révoque ses sessions) : titre générique, pas de date, de référence ni de motif en clair, pas de « Consulter mes dossiers », bloc « Confirmez votre identité » qui sert à l'export et au recours ; deux colonnes conservées, colonne latérale dans l'ordre identité → export → recours ; recours sans pièces jointes. Maquette HTML corrigée.

## Objectif
Dire clairement au praticien ce qui lui arrive et ce qu'il peut encore faire : exporter ses dossiers, contester une fois. Rien n'est supprimé pendant la suspension ; la protection des dossiers patients prime sur la sanction. Le motif détaillé est dans l'e-mail de décision.

## Route & module
- Route publique : `/compte-suspendu` (layout public, `data.wide`). Le client y envoie tout visiteur dont le login répond `403 ACCOUNT_SUSPENDED` (compte `rejected`, `suspended` ou `closed`) ; une session ouverte avant la décision est révoquée par le serveur, et sa requête suivante reçoit la même erreur (intercepteur, fiche 00 §7).
- Le compte n'a **plus de session** : il ne lit ni ses patients, ni l'état de sa décision. Export et recours sont publics et s'authentifient dans le corps `{ email, password, otp? }` (contrat).
- API :
  - `POST /api/me/export` `{ email, password, otp? }` → `202 { jobId, statusUrl }`, ou `202 { otpRequired: true }` sans job si la 2FA est active et que `otp` manque ; job asynchrone (`export_job`), zip chiffré contenant `patients.md` et, par patient, l'export Markdown et PDF de M6-05 ; `GET /api/me/export/{jobId}` → statut puis lien ; lien valable 7 jours, fichier purgé ensuite (`410`). Un seul export en cours (`409`). `401` identifiants faux, `423` après 5 échecs.
  - `POST /api/me/appeal` `{ email, password, otp?, message }` → `201 { id, status: open, submittedAt }` ; message de 20 à 4 000 caractères ; **un seul recours par décision** (`practitioner_appeal`, E3.3 ; `409 APPEAL_ALREADY_OPEN` / `APPEAL_ALREADY_USED` / `APPEAL_NOT_ALLOWED`, `409 ACCOUNT_NOT_SUSPENDED` si le compte n'est ni refusé ni suspendu) ; examiné par un administrateur **différent** de celui qui a décidé ; réponse sous 5 jours ouvrés (indicatif). Pas de pièces jointes au contrat.
- Clôture définitive (refus confirmé après recours ou absence de recours sous 30 jours) : compte `closed`, données conservées **90 jours** supplémentaires pour export, puis purge (A10).

## Structure
Bandeau public de la fiche 00 (sans « Connecté·e en tant que ») ; deux colonnes dès 1 024 px, empilées en dessous :
1. **Carte principale** (640 px) : bande rouge « Accès à l'application suspendu » (`role="status"`), titre générique « Votre compte est suspendu ou votre dossier a été refusé », paragraphe sur ce qui est désactivé et ce qui reste possible, **Motif communiqué** (encart : « le motif détaillé et la référence de la décision figurent dans l'e-mail », rappel « aucune donnée patient n'a été consultée »), **Ce que vous pouvez faire** (3 items : exporter, contester, dossiers protégés), **Besoin de parler à quelqu'un ?** (l'adresse de l'équipe vérification est dans l'e-mail de décision ; ne jamais joindre de données patient).
2. **Colonne latérale** (400 px), dans l'ordre d'usage : **Confirmez votre identité** (e-mail, mot de passe ; champ « Code de vérification » ajouté après un `202 otpRequired`) → **Exporter mes dossiers** (bouton, état du job, lien) → **Contester la décision** (explication avec compteur mono « 0 / 4000 · 20 caractères au moins », « Envoyer le recours », note « un seul recours »). Export et recours réutilisent l'identité saisie dans le premier bloc.

## Interactions
- Identifiants faux sur l'export ou le recours → message unique sous le bloc d'identité, focus sur le mot de passe ; `423` → compte à rebours `Retry-After`.
- « Exporter mes dossiers » → « Export en préparation… » (interrogation de `statusUrl`) puis lien de téléchargement ; un seul export en cours à la fois (`409`).
- Recours envoyé (`201`) → le formulaire est remplacé par « Recours envoyé le … · en examen » ; un `409 APPEAL_ALREADY_OPEN` ou `APPEAL_ALREADY_USED` affiche le même état sans date.
- `409 ACCOUNT_NOT_SUSPENDED` (compte réactivé entre-temps) → message avec lien vers la connexion.
- Levée de la suspension : le praticien se reconnecte normalement par l'écran 11.

## Tokens
- Bande d'état : fond `#FBE9E7`, bordure `#F0C9C4`, texte `#9A2F22`.
- Encart motif : fond `#EEF1F6`, référence en `JetBrains Mono` `#8A94A6`.
- Icônes d'action sur fond `#E3EAF8` ; compteur du recours en `JetBrains Mono` `#5A6679`.

## Accessibilité
- La bande d'état est `role="status"` ; le titre `<h1>` reprend l'information.
- Colonne latérale en `<aside>` ; bloc d'identité en `<fieldset>` avec `<legend>` « Confirmez votre identité » ; recours : `textarea` avec `<label>` et `aria-describedby` vers le compteur.

## Critères d'acceptation
- [ ] Compte `suspended` : `POST /api/auth/login` → `403 ACCOUNT_SUSPENDED`, le client affiche cette page sans session ; `POST /api/me/export` avec e-mail et mot de passe → `202`.
- [ ] Fiche publique absente de l'annuaire et de la recherche publique en moins d'une minute après la suspension.
- [ ] `POST /api/me/appeal` une 2ᵉ fois pour la même décision → `409` ; le formulaire n'est plus affiché après le premier envoi.
- [ ] Le recours est assigné à un admin ≠ auteur de la décision (test avec un seul admin → file « à réassigner », pas d'auto-examen).
- [ ] Export → archive contenant tous les patients, entrées et liens (entrées privées exclues, A4.7), aucun justificatif de vérification ; lien expiré après 7 jours (`410`), fichier purgé.
- [ ] Réactivation → le login répond de nouveau `200`.

## Écarts avec la maquette
Maquette HTML corrigée le 11 novembre 2026 (UX-F1) ; le `.png` montre encore l'ancienne version.
- **Pas de session** : le contrat révoque les sessions d'un compte refusé ou suspendu et refuse son login (`403 ACCOUNT_SUSPENDED`) → plus de « Connecté·e en tant que … · Se déconnecter », plus de « Consulter mes dossiers (lecture seule) » (l'ADR-12 « lecture seule » n'est pas réalisable sans session), plus de rangée d'actions dans la carte principale (contrat ; maquette corrigée).
- **Titre et motif génériques** : aucune route ne renvoie l'état de la décision (`GET /api/me/suspension` n'existe pas au contrat) → titre « suspendu ou refusé », ni date, ni référence `VD-…`, ni motif en clair ; le motif est dans l'e-mail (contrat ; maquette corrigée). Afficher l'état réel après identification attend une route au contrat.
- **Confirmez votre identité** : export et recours prennent `{ email, password, otp? }` dans le corps → bloc d'identité en tête de la colonne latérale, puis export, puis recours (contrat ; maquette corrigée).
- **Recours sans pièces jointes** : `AppealRequest` n'a que `message` (20 à 4 000 caractères) → zone « Ajouter des pièces » retirée, compteur ajouté (contrat ; maquette corrigée).
- **Export** : archive Markdown + PDF par patient, lien valable 7 jours (G Q18) au lieu de « JSON + PDF, sans limite de durée » ; « l'agenda » retiré du texte (G Q25) (maquette corrigée).

## Hors périmètre V1
Suspension partielle (annuaire seul : couverte par « dépublier » dans l'écran 23, V1.1), transfert de dossiers à un confrère, médiation externe, agenda (hors V1, G Q25).

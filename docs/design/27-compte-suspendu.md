# Écran 27 · Compte suspendu / refusé

> Spécification pour agents. Maquette : `27-compte-suspendu.html` / `.png`. Palette : Nordique, layout public. Module `identity` (`verification_status = rejected` ou `suspended`), `identity.admin` (recours, **M1-14b**, G Q28), `clinical` (export du compte, **M6-05**, G Q18). ADR-12 du document A (suspension = lecture seule, alternative « tout bloquer » documentée).
>
> Mis à jour le 29 octobre 2026 (G) : recours dans la tâche M1-14b ; export du compte = table `export_job` (E3.6, migration V11) dans M6-05, zip chiffré (Markdown + PDF par patient + liste des patients), lien valable 7 jours ; aucun agenda ni synchronisation Google en V1 (Q25).

## Objectif
Dire clairement au praticien ce qui lui arrive, pourquoi, et ce qu'il peut encore faire : consulter et exporter ses dossiers, contester une fois. Rien n'est supprimé pendant la suspension ; la protection des dossiers patients prime sur la sanction.

## Route & module
- Route : `/compte-suspendu`. Tout compte `rejected` ou `suspended` y est redirigé après connexion et à chaque tentative d'accès à une route interdite (`403 ACCOUNT_SUSPENDED`).
- Droits en suspension (serveur, filtre de M1-11 complété par M1-14b et M6-05) : **autorisé** → `GET` sur ses patients, entrées, liens ; `POST /api/me/export`, `GET /api/me/export/{jobId}` ; `GET/POST /api/me/appeal` ; `GET /api/auth/session`, déconnexion. **Interdit** → toute écriture clinique (`POST/PATCH/DELETE` sur `patients`, `clinical`, `linking`), fiche publique (dépubliée par `PractitionerSuspended` / `PractitionerRejected`, M5-06), préférences hors export.
- API :
  - `GET /api/me/suspension` → `{ since, kind (rejected|suspended), decisionRef, reason, emailSentAt, appeal: { allowed, submittedAt?, decidedAt?, outcome? }, purgeAt? }`.
  - `POST /api/me/appeal` `{ message, documents[] }` → `201` ; **un seul recours par décision** (`practitioner_appeal`, E3.3) ; examiné par un administrateur **différent** de celui qui a décidé (contrainte vérifiée côté serveur) ; réponse sous 5 jours ouvrés (SLA affiché, non garanti). Pièces jointes = `verification_document` avec `purpose = 'appeal'`.
  - `POST /api/me/export` → `202 { jobId }` ; job asynchrone (`export_job`), zip chiffré contenant `patients.md` et, par patient, l'export Markdown et PDF de M6-05 ; `GET /api/me/export/{jobId}` → statut puis URL signée ; lien valable 7 jours, fichier purgé ensuite (`410`), e-mail à la fin.
- Clôture définitive (refus confirmé après recours ou absence de recours sous 30 jours) : compte `closed`, données conservées **90 jours** supplémentaires pour export, puis purge (A10).

## Structure
Bandeau public avec « Connecté·e en tant que … · Se déconnecter » ; deux colonnes :
1. **Carte principale** (640 px) : bande rouge « Compte suspendu depuis le 23 oct. 2026 », titre, paragraphe sur ce qui reste possible / désactivé, **Motif communiqué** (encart : référence de décision `VD-2026-0418`, motif en clair, date d'envoi de l'e-mail, rappel « aucune donnée patient n'a été consultée »), **Ce que vous pouvez faire** (3 items : exporter, contester, dossiers protégés), actions « Exporter mes données », « Contester la décision », « Consulter mes dossiers (lecture seule) ».
2. **Colonne latérale** (400 px) : formulaire **Contester la décision** (explication, pièces jointes — mêmes règles que l'écran 13 : PDF/JPG/PNG, 10 Mo, chiffrées, purge 90 j —, « Envoyer le recours », note « un seul recours »), encart **Besoin de parler à quelqu'un ?** (e-mail de l'équipe vérification, délai, rappel de ne jamais joindre de données patient).

## Interactions
- Si un recours a déjà été envoyé : le formulaire est remplacé par son état (« Recours envoyé le … · en examen » puis « Décision du … : maintenue / levée »).
- « Consulter mes dossiers (lecture seule) » → application avec bandeau persistant « Compte suspendu · lecture seule » ; barre de saisie, boutons d'ajout et glisser-déposer désactivés avec explication au survol/focus.
- « Exporter mes données » → état « Export en préparation… » puis lien ; un seul export en cours à la fois (`409` sinon).
- Levée de la suspension (admin « Réactiver » ou recours accepté) → à la prochaine requête, redirection vers `/patients` avec toast « Votre compte est de nouveau actif ».
- Compte **refusé** (jamais approuvé) : même page, titre « Votre dossier a été refusé », sans mention de fiche publique (il n'y en avait pas).

## Tokens
- Bande d'état : fond `#FBE9E7`, bordure `#F0C9C4`, texte `#9A2F22`.
- Encart motif : fond `#EEF1F6`, référence en `JetBrains Mono` `#8A94A6`.
- Icônes d'action sur fond `#E3EAF8` ; zone de dépôt bordure `1.5px dashed #B9C2D1`.

## Accessibilité
- La bande d'état est `role="status"` ; le titre `<h1>` reprend l'information.
- Formulaire de recours : `textarea` avec `<label>`, zone de dépôt doublée d'un `<input type="file" multiple>` réel.
- Les actions désactivées dans l'application en lecture seule sont `aria-disabled` avec `aria-describedby` vers l'explication (pas `disabled`, pour rester lisibles au clavier).

## Critères d'acceptation
- [ ] Compte `suspended` : `GET /api/patients/{id}` → `200` ; `POST /api/patients/{id}/entries` → `403 ACCOUNT_SUSPENDED` ; `POST /api/me/export` → `202`.
- [ ] Fiche publique absente de l'annuaire et de la recherche publique en moins d'une minute après la suspension.
- [ ] `POST /api/me/appeal` une 2ᵉ fois pour la même décision → `409` ; le formulaire n'est plus affiché après le premier envoi.
- [ ] Le recours est assigné à un admin ≠ auteur de la décision (test avec un seul admin → file « à réassigner », pas d'auto-examen).
- [ ] Export → archive contenant tous les patients, entrées et liens (entrées privées exclues, A4.7), aucun justificatif de vérification ; lien expiré après 7 jours (`410`), fichier purgé.
- [ ] Réactivation → prochaine requête `200`, bandeau disparu, écritures de nouveau acceptées.

## Hors périmètre V1
Suspension partielle (annuaire seul : couverte par « dépublier » dans l'écran 23, V1.1), transfert de dossiers à un confrère, médiation externe, agenda (hors V1, G Q25).

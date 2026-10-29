# Écran 13 · Dossier professionnel (justificatifs et vérification)

> Spécification pour agents. Maquette : `13-dossier-professionnel.html` / `.png`. Palette : Nordique. Document A : module `identity` (sous-domaine *verification*, M1-13) ; lié aux écrans 12 (inscription) et 20 (admin).
>
> Mis à jour le 29 octobre 2026 (G Q14) : règle de complétude unique pour toutes les professions — **diplôme et pièce d'identité obligatoires** ; attestation ADELI/RPPS **recommandée** pour psychologues et psychiatres, jamais bloquante. Stockage : disque local chiffré en dev, stockage de bêta décidé avec l'étude hébergement (Q17). Types et états en `text` + `CHECK` (E).

## Objectif
Après la création du compte, le praticien dépose un ou plusieurs justificatifs de qualification (diplômes, attestation ADELI/RPPS, certification de coaching, pièce d'identité), suit l'état de la vérification et peut continuer à travailler pendant ce temps.

## Règles de verrouillage (décision du 25 oct.)
| État `verification_status` | Outil clinique (patients, timeline…) | Annuaire / profil public | Badge « vérifié » | Bandeau |
|---|---|---|---|---|
| `email_pending` | non (connexion refusée) | non | non | — |
| `docs_pending` | **oui** | non (interrupteur « Apparaître » désactivé, motif affiché) | non | ambre « dossier à compléter » |
| `under_review` | oui | non | non | ambre « en cours de vérification » |
| `needs_more` | oui | non | non | ambre avec le message de l'admin |
| `approved` | oui | oui | oui | aucun |
| `rejected` | **non** (compte suspendu, données conservées 30 j, export puis purge ; recours possible, écran 27) | non | non | page dédiée |

Alternative notée, non retenue : bloquer tout accès avant validation. Elle sécurise davantage mais casse la découverte produit en bêta ; à réévaluer si l'inscription ouverte (G Q21) amène trop de comptes non qualifiés.

## Route & module
- Route : `/mon-compte/dossier-professionnel` ; proposée automatiquement au premier login après confirmation ; accessible ensuite depuis Mon compte.
- API : `GET /api/me/verification` → `{status, submittedAt, decidedAt, adminMessage, documents[], missing[], recommended[]}` ; `POST /api/me/verification/documents` (multipart : `type`, `title`, `issuer`, `year`, `file`) → `201` ; `DELETE /api/me/verification/documents/{id}` (seulement tant que `status ∈ {docs_pending, needs_more}`) ; `POST /api/me/verification/submit` → passe en `under_review` et notifie les admins ; `PATCH /api/me/professional-info` (profession, n° pro, téléphone, adresse).
- Table `verification_document` (E3.3, créée par V03) : `id, practitioner_id, purpose, type (diploma, registration, certification, professional_card, identity, other), title, issuer, year, storage_key, mime, size_bytes, sha256, uploaded_at, reviewed_status (pending, conform, non_conform), reviewed_at, reviewed_by, purge_after, purged_at, deleted_at`.
- Stockage : objet chiffré hors base (AES-GCM applicatif ; disque local en dev, S3 / MinIO / hébergeur HDS décidé avec l'étude hébergement, G Q17) ; URL signée 5 min pour la visionneuse admin ; antivirus (ClamAV) à l'upload ; PDF/JPG/PNG ≤ 10 Mo vérifiés par signature ; EXIF supprimé.
- Rétention : fichiers purgés 90 j après la décision (`purge_after`), seules les métadonnées (`type, title, issuer, year, reviewed_*`) et la mention « vérifié le … » restent.

## Structure
1. **Barre d'application** + **bandeau d'état** (ambre, toujours visible sur toutes les pages tant que non `approved`).
2. **Titre + explication** (visibilité limitée aux administrateurs).
3. **Progression** en 4 jalons : Compte créé · E-mail confirmé · Justificatifs (courant) · Validation.
4. **Informations professionnelles** : profession déclarée, n° ADELI/RPPS (recommandé pour psychologues et psychiatres), téléphone, adresse.
5. **Justificatifs** : liste des documents déposés (icône, intitulé · établissement · année, type · nom de fichier mono · taille · date, statut *En attente / Conforme / Non conforme*, retirer) ; **formulaire d'ajout** en zone pointillée : type (liste), intitulé, établissement · année, fichier (bouton + glisser-déposer), bouton *Ajouter au dossier* ; mention rétention.
6. **Actions** : **Soumettre le dossier pour vérification** (activé dès que le diplôme et la pièce d'identité sont présents), lien *Plus tard — aller à mes patients*.
7. **Latéral** : justificatifs attendus (toutes professions : diplôme + pièce d'identité ; recommandé : attestation ADELI pour les psychologues, RPPS pour les psychiatres, certification pour les coachs) ; « Comment ça se passe ».

## Règles de complétude (avant `submit`, G Q14)
- Toutes les professions : ≥ 1 `diploma` **et** 1 `identity`.
- Recommandé, non bloquant : `registration` (ADELI) pour psychologue, `registration` (RPPS) pour psychiatre, `certification` pour coach ; affiché dans `recommended[]` et en encart « conseillé » sous le bouton.
- Message explicite sous le bouton si incomplet (« Il manque : pièce d'identité »).
- Les règles vivent dans une classe `CompletenessRules` (M1-13) facilement modifiable.

## Tokens
- Bandeau `#FBF1DC` / `#7A5A12` ; jalons : fait `--success`, courant `--primary` (bordure 2 px), à venir `#D6DCE6`.
- Zone de dépôt : bordure pointillée `#B9C2D1` sur `#EEF1F6`.
- Statut document : *En attente* `#7A5A12` / `#FBF1DC`, *Conforme* `#1E6B45` / `#E4F2EA`, *Non conforme* `#9A2F22` / `#FBE9E7`.

## Accessibilité
- `<input type="file">` réel avec `<label>` ; zone glisser-déposer = amélioration progressive.
- Progression `<ol aria-label>` avec `aria-current="step"`.
- Chaque document : bouton « Retirer » avec `aria-label` nommant le document.

## Critères d'acceptation
- [ ] Upload PDF 12 Mo → `413` RFC 9457 ; fichier `.exe` renommé `.pdf` → `415` (détection par signature).
- [ ] Un diplôme sans pièce d'identité, quelle que soit la profession → bouton soumettre désactivé + message « Il manque : pièce d'identité » ; `submit` forcé → `409 {missing: ["identity"]}`.
- [ ] Psychologue avec diplôme et pièce d'identité mais sans ADELI → soumission possible, encart « conseillé » affiché.
- [ ] `submit` → `status = under_review`, e-mail aux admins, bandeau mis à jour sans rechargement.
- [ ] `DELETE` d'un document après `under_review` → `409`.
- [ ] 91 jours après `approved` : fichier absent du stockage, métadonnées présentes (test de purge planifiée).
- [ ] Un praticien ne peut jamais lire le document d'un autre (`403`, test d'intégration).

## Hors périmètre V1
Vérification automatique auprès des registres (API ANS/RPPS), OCR des diplômes, signature électronique.

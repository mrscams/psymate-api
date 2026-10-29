# Écran 23 · Admin › Signalements de l'annuaire

> **V1.1 (décision G Q26, 29 octobre 2026)** : cette fiche n'a aucune tâche dans le plan C ni dans l'orchestration D en V1. Le DDL de `directory_report` et `directory_profile_version` est prêt en E6.1. L'onglet « Signalements » de la barre admin n'apparaît pas en V1.

> Spécification pour agents. Maquette : `23-admin-signalements.html` / `.png`. Palette : Nordique (barre admin sombre). Document A, modules `directory` (`directory_report`) et `identity.admin`, section A10.

## Objectif
Traiter les signalements envoyés par les visiteurs depuis « Signaler un problème » sur une fiche publique (écran 9). Ils ne concernent **que l'annuaire** : jamais le suivi clinique, jamais un patient.

## Route & module
- Route : `/admin/signalements` (liste à gauche, détail à droite ; `?id=` dans l'URL).
- Création côté public : `POST /api/public/directory/{slug}/report` `{ reason (misleading_title|wrong_contact|inappropriate_content|not_a_practitioner|other), message (≤ 1000), reporterEmail? }`, anti-abus : honeypot + limite 3 / IP / jour (Bucket4j). Accusé de réception si e-mail fourni.
- Admin : `GET /api/admin/reports?status` ; `GET /api/admin/reports/{id}` → signalement + **extrait de la fiche publiée au moment du signalement** (snapshot `directory_profile_version`) + résumé du dossier de vérification (date d'approbation, pièces, profession déclarée) ; `POST /api/admin/reports/{id}/resolve` `{ action, messageToPractitioner? }`.
- Actions (`directory_report.action`) :
  - `request_fix` — e-mail au praticien avec le passage concerné ; fiche maintenue ; relance automatique à 7 jours ; le signalement reste « en attente de correction » jusqu'à republication.
  - `unpublish` — fiche retirée de l'annuaire immédiatement (`practitioner_profile.published = false`, motif stocké) ; le praticien garde l'outil et doit republier après correction (écran 10 affiche le motif).
  - `dismiss` — sans suite ; conservé dans le journal.
  - `suspend` — le dossier repasse en examen dans la file de validation (écran 20) et le compte est suspendu (écran 21).
- Chaque résolution → `verification_decision` (`kind = 'report_resolved'`, `related_id` = signalement) + ligne de journal ; le visiteur reçoit un accusé « votre signalement a été traité », sans détail.
- Première dépendance de `identity.admin` vers `directory` (lecture et dépublication), à autoriser explicitement dans `package-info` le jour venu.

## Structure
1. **Barre admin** (Signalements actif, badge n à traiter).
2. **Colonne gauche** (380 px) : « Signalements · 2 à traiter », segments À traiter / Traités / Tous, note de périmètre, liste de cartes (fiche concernée, motif en badge, extrait du message, auteur « visiteur », ancienneté ; traités : « Traité · sans suite · date · admin »).
3. **Panneau de détail** :
   - Titre « Signalement · fiche de Julien Vasseur », métadonnées (motif, reçu le, visiteur anonyme + e-mail masqué `v…@exemple.fr`), lien « Ouvrir la fiche publique ↗ ».
   - Message complet du visiteur (citation).
   - **Extrait de la fiche · version publiée le …** : avatar, nom, profession, présentation (passages litigieux surlignés par l'admin, facultatif).
   - Rappel du dossier de vérification (approuvé le, pièces, profession déclarée) — pour repérer un écart titre/profession.
   - **Suite à donner** : 4 radios avec description (Demander une correction / Dépublier la fiche / Classer sans suite / Suspendre et rouvrir la vérification), zone « Message au praticien » préremplie selon l'action, bouton « Envoyer et marquer traité », note sur l'accusé au visiteur.

## Interactions
- Sélection d'une carte → détail chargé, URL mise à jour ; raccourcis J/K pour naviguer.
- Changer d'action met à jour le modèle de message (constantes i18n).
- « Dépublier » demande une confirmation ; « Suspendre » n'exige pas de message mais l'autorise.
- Un même praticien avec plusieurs signalements ouverts → bandeau « 2 autres signalements sur cette fiche » avec lien de regroupement.
- Après traitement, la carte passe dans « Traités » et le détail affiche la décision et l'admin.

## Tokens
- Badges de motif : Titre trompeur `#FBF1DC`/`#7A5A12`, Coordonnées erronées `#E3EAF8`/`#2C4C99`, Contenu inapproprié `#FBE9E7`/`#9A2F22`, Traité `#EEF1F6`/`#5A6679`.
- Radio d'action sélectionnée : bordure `#3A63C2`, fond `#E3EAF8` ; « Dépublier » en variante danger (`#9A2F22`) une fois sélectionnée.
- Extrait de fiche : fond `#EEF1F6`, passages surlignés `#FBF1DC`.

## Accessibilité
- Liste de gauche = `<ul>` de `<a>` (une carte = un lien), `aria-current="true"` sur la carte ouverte.
- Actions = `<fieldset>` + radios réels ; descriptions liées par `aria-describedby`.
- Le panneau de détail est une `<section aria-labelledby>` ; au chargement d'un signalement, le focus va sur son titre.

## Critères d'acceptation
- [ ] `POST …/report` 4 fois depuis la même IP le même jour → `429` au 4ᵉ.
- [ ] `request_fix` → e-mail envoyé avec le message, signalement en `awaiting_fix` ; republication de la fiche par le praticien → statut `resolved` automatiquement.
- [ ] `unpublish` → fiche absente de l'annuaire et de la recherche publique dans la seconde ; l'écran 10 du praticien affiche le motif et un bouton « Republier » inactif tant que la présentation n'a pas été modifiée.
- [ ] `suspend` → praticien suspendu, visible dans les écrans 20 et 21, fiche retirée de l'annuaire.
- [ ] Détail → l'extrait affiché est la version publiée **à la date du signalement**, même si le praticien a modifié sa fiche depuis.
- [ ] Visiteur avec e-mail → accusé de traitement sans mention de l'action prise (test sur le contenu de l'e-mail).

## Hors périmètre
Signalement d'un praticien par un autre praticien, modération automatique du texte des fiches, historique public des sanctions.

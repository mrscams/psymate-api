# Écran 22 · Admin › Journal

> Spécification pour agents. Maquette : `22-admin-journal.html` / `.png`. Palette : Nordique (barre admin sombre). Document A, module `identity.admin`, section A10 ; Document C, C6, **tâche M1-14b** (G Q27).
>
> Mis à jour le 29 octobre 2026 (G Q29, E) : une seule source de journal, la table `security_event` (préfixes `kind` : `admin.*`, `directory.*`, `system.*`, `patient.read` / `patient.write` pour l'audit applicatif de M7-02), plus `verification_decision` ; les deux sont réunies par la vue en lecture seule `admin_journal_v` (E3.13, migration V10). Pas de tables `directory_event` ni `system_event`. Le rôle applicatif `psymate_app` n'a pas le droit d'`UPDATE`/`DELETE` sur ces tables (REVOKE, Q9).

## Objectif
Rendre vérifiable, après coup, tout ce que l'administration a fait : chaque décision, chaque ouverture d'un justificatif, chaque événement de sécurité. Le journal est **append-only** et en lecture seule depuis l'interface.

## Route & module
- Route : `/admin/journal`.
- API : `GET /api/admin/journal?kind&actor&practitionerId&from&to&cursor` → `{ items[], nextCursor }` ; item : `at, kind (decision|document_access|security|directory|system), summary, actor{ id, initials } | "system" | "practitioner", context{ ip (tronquée), city, userAgent } , link? { label, href } }`.
- Sources agrégées par la vue SQL `admin_journal_v` (lecture seule) : `security_event` (sécurité, accès aux documents via `document_access_log` journalisé aussi en `security_event`, annuaire, système, audit patient sans détail clinique) et `verification_decision` (décisions).
- Rétention 12 mois en ligne ; archivage mensuel chiffré au-delà (hors V1 : simple conservation en base).
- `POST /api/admin/journal/export` `{ from, to, kind? }` → CSV ; l'export est lui-même journalisé (`system.journal_export`).

## Structure
1. **Barre admin** (Journal actif).
2. **En-tête** : « Journal » + sous-titre (sources, rétention, lecture seule) + « Exporter la période ».
3. **Filtres** : segments Tout / Décisions / Accès aux documents / Sécurité / Annuaire ; select administrateur (Tous / HA / NB…) ; période (7 jours / 30 jours / Personnalisée…) ; champ « Filtrer par praticien » (autocomplétion).
4. **Tableau** : Horodatage (`JetBrains Mono`, secondes incluses), Type (badge), Événement (phrase complète : qui, quoi, détail utile — ex. « Ouverture de rpps-attestation.pdf (Dr Maya Kaddour) · URL signée 5 min »), Par (initiales admin / « système » / « praticien·ne »), Contexte (IP tronquée · ville · navigateur, ou lien « Voir la décision » / « Voir la fiche »).
5. **Pagination** par curseur « 8 sur 312 · 7 derniers jours · Afficher plus ».
6. **Note** : append-only, IP tronquées à l'affichage.

## Interactions
- Les filtres sont dans l'URL et combinables ; le compteur total suit les filtres.
- « Voir la décision » ouvre l'écran 20 sur le dossier et la décision concernée (ancre `#decision-{id}`).
- Un accès document (`document_access`) est écrit **par le serveur** au moment où l'URL signée est générée, pas au clic : l'ouverture effective n'est pas observable.
- Événements de sécurité des praticiens (verrouillage, 2FA activée) apparaissent ici **sans** le détail de leurs sessions cliniques ; les lignes `patient.read` / `patient.write` de l'audit applicatif (M7-02) ne montrent qu'un identifiant de patient opaque, jamais un nom.
- Export : limité à 12 mois et 50 000 lignes ; au-delà, message demandant d'affiner la période.

## Tokens
- Badges de type : Décision `#E3EAF8`/`#2C4C99`, Accès document `#ECE8F8`/`#4E3F9E`, Sécurité `#FBF1DC`/`#7A5A12` (verrouillage, échecs) ou `#E4F2EA`/`#1E6B45` (2FA activée, connexion réussie), Annuaire `#E2F1EE`/`#3E8E7E`, Système `#EEF1F6`/`#5A6679`.
- Horodatage et IP en `JetBrains Mono` `#5A6679`.

## Accessibilité
- Tableau avec `<caption>` (période et filtres actifs, pour les lecteurs d'écran) et `<th scope="col">`.
- Les badges contiennent le libellé textuel ; les filtres segmentés sont `aria-pressed`.
- « Afficher plus » conserve le focus sur la première nouvelle ligne.

## Critères d'acceptation
- [ ] Aucune route `PATCH`/`DELETE` sur `/api/admin/journal` (`405`) ; un `UPDATE` sur `security_event` ou `verification_decision` avec le rôle `psymate_app` est refusé (test SQL).
- [ ] Générer une URL signée pour un justificatif (écran 20) → une ligne `document_access` avec le nom du fichier, l'admin, l'IP tronquée, dans la seconde.
- [ ] Filtre « Sécurité » + praticien « r.lenoir@… » → uniquement ses événements de sécurité, aucun événement clinique.
- [ ] Export de 30 jours → CSV téléchargé **et** nouvelle ligne `system` « Export du journal … par HA ».
- [ ] Une IP complète n'apparaît jamais dans la réponse API (test : regex IPv4/IPv6 complète sur le JSON).
- [ ] Aucun champ patient (nom, entrée) dans aucune réponse de `/api/admin/journal` (test JSON).

## Hors périmètre V1
Alertes automatiques (ex. 3 accès documents en une minute), signature cryptographique des lignes, intégration SIEM.

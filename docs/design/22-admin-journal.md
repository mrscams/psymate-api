# Écran 22 · Admin › Journal

> Spécification pour agents. Maquette : `22-admin-journal.html` / `.png`. Palette : Nordique (barre admin sombre). Document A, module `identity.admin`, section A10 ; Document C, C6, **tâche M1-14b** (G Q27).
>
> Mis à jour le 29 octobre 2026 (G Q29, E) : une seule source de journal, la table `security_event` (préfixes `kind` : `admin.*`, `directory.*`, `system.*`, `patient.read` / `patient.write` pour l'audit applicatif de M7-02), plus `verification_decision` ; les deux sont réunies par la vue en lecture seule `admin_journal_v` (E3.13, migration V10). Pas de tables `directory_event` ni `system_event`. Le rôle applicatif `psymate_app` n'a pas le droit d'`UPDATE`/`DELETE` sur ces tables (REVOKE, Q9).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : la fiche et la maquette suivent `GET /api/admin/journal` (contrat `JournalDto` / `JournalItemDto`) : ni IP ni ville, pas d'export, pas de total ; lignes identiques consécutives regroupées ; onglet Signalements retiré. Voir « Écarts avec la maquette ».

## Objectif
Rendre vérifiable, après coup, tout ce que l'administration a fait : chaque décision, chaque ouverture d'un justificatif, chaque événement de sécurité. Le journal est **append-only** et en lecture seule depuis l'interface.

## Route & module
- Route : `/admin/journal`.
- API : `GET /api/admin/journal?kind&practitionerId&actorId&from&to&cursor` → `{ items[], nextCursor }` (pas de total) ; item (`JournalItemDto`) : `id, at, kind` (préfixe `admin.*` pour les décisions, nom de l'événement de sécurité sinon), `source` (`security_event` | `verification_decision`), `summary`, `actor` (null si l'auteur n'est pas un administrateur), `actorType` (`admin` | `practitioner` | `system`), `practitionerId`, `practitionerDisplayName`, `details` (objet libre : `reason`, `decisionId`, `appealId`…). **Jamais d'adresse IP, ni de ville, ni de navigateur** dans la réponse.
- Sources agrégées par la vue SQL `admin_journal_v` (lecture seule) : `security_event` (sécurité, accès aux documents via `document_access_log` journalisé aussi en `security_event`, annuaire, système, audit patient sans détail clinique) et `verification_decision` (décisions).
- Rétention 12 mois en ligne ; archivage mensuel chiffré au-delà (hors V1 : simple conservation en base).
- *Pas d'export en V1* : aucune route d'export du journal au contrat.

## Structure
1. **Barre admin** (Journal actif).
2. **En-tête** : « Journal » + sous-titre (sources, rétention, lecture seule).
3. **Filtres** : segments Tout / Décisions / Accès aux documents / Sécurité / Annuaire ; select administrateur (Tous / HA / NB…) ; période (7 jours / 30 jours / Personnalisée…) ; filtre par praticien (`practitionerId`, posé depuis une ligne ou depuis l'écran 21 ; le contrat n'a pas de recherche texte dans le journal).
4. **Tableau** : Horodatage (`JetBrains Mono`, secondes incluses), Type (badge), Événement (phrase complète : qui, quoi, détail utile — ex. « Ouverture de rpps-attestation.pdf (Dr Maya Kaddour) · URL signée 5 min »), Par (initiales admin / « système » / « praticien·ne », depuis `actorType`), Contexte (lien « Voir la décision » quand `details.decisionId` existe, sinon « — » ; colonne masquée quand aucune ligne affichée n'a de lien). Aucune IP, ville ni navigateur.
5. **Regroupement** : les lignes identiques consécutives (même `kind`, même `summary`, même auteur, même compte concerné) dans la même minute sont affichées en une seule ligne « 5 × Échec de connexion … » avec l'intervalle d'horodatage (« 03:16:12 → 03:16:58 ») et un bouton *Déplier* (`aria-expanded`) qui montre les lignes d'origine. Le regroupement est fait par le front sur les lignes chargées ; il ne change ni l'ordre ni le contenu.
6. **Pagination** par curseur « n lignes · 7 derniers jours · Afficher plus » (pas de total au contrat).
7. **Note** : append-only ; le journal ne contient ni IP ni ville.

## Interactions
- Les filtres sont dans l'URL et combinables ; le compteur total suit les filtres.
- « Voir la décision » ouvre l'écran 20 sur le dossier et la décision concernée (ancre `#decision-{id}`).
- Un accès document (`admin.document_read`) est écrit **par le serveur** à chaque lecture du fichier (`…/documents/{docId}/file`), pas à l'émission de l'URL signée (contrat de `…/url` : « rien n'est journalisé à l'émission »).
- Événements de sécurité des praticiens (verrouillage, 2FA activée) apparaissent ici **sans** le détail de leurs sessions cliniques ; les lignes `patient.read` / `patient.write` de l'audit applicatif (M7-02) ne montrent qu'un identifiant de patient opaque, jamais un nom.

## Tokens
- Badges de type : Décision `#E3EAF8`/`#2C4C99`, Accès document `#ECE8F8`/`#4E3F9E`, Sécurité `#FBF1DC`/`#7A5A12` (verrouillage, échecs) ou `#E4F2EA`/`#1E6B45` (2FA activée, connexion réussie), Annuaire `#E2F1EE`/`#3E8E7E`, Système `#EEF1F6`/`#5A6679`.
- Horodatage en `JetBrains Mono` `#5A6679`.

## Accessibilité
- Tableau avec `<caption>` (période et filtres actifs, pour les lecteurs d'écran) et `<th scope="col">`.
- Les badges contiennent le libellé textuel ; les filtres segmentés sont `aria-pressed`.
- « Afficher plus » conserve le focus sur la première nouvelle ligne.

## Critères d'acceptation
- [ ] Aucune route `PATCH`/`DELETE` sur `/api/admin/journal` (`405`) ; un `UPDATE` sur `security_event` ou `verification_decision` avec le rôle `psymate_app` est refusé (test SQL).
- [ ] Ouvrir un justificatif (écran 20) → une ligne `admin.document_read` avec l'admin et le compte concerné, dans la seconde ; obtenir l'URL signée sans l'ouvrir n'écrit rien (contrat).
- [ ] Filtre « Sécurité » + praticien « r.lenoir@… » → uniquement ses événements de sécurité, aucun événement clinique.
- [ ] Cinq échecs de connexion du même compte dans la même minute → une seule ligne « 5 × … » qui se déplie en cinq lignes.
- [ ] Une IP complète n'apparaît jamais dans la réponse API (test : regex IPv4/IPv6 complète sur le JSON).
- [ ] Aucun champ patient (nom, entrée) dans aucune réponse de `/api/admin/journal` (test JSON).

## Écarts avec la maquette
- **Onglet Signalements** : V1.1 (G Q26). Retiré de la maquette (UX-F1) ; onglets dans l'ordre de la fiche 00 ; nom affiché PsyMate.
- **IP et ville dans la colonne Contexte** : `JournalItemDto` n'en porte jamais (contrat : « Jamais d'adresse IP ») ; remplacées par « — » dans la maquette (UX-F1), placeholder de recherche sans « IP ».
- **« Exporter la période »** : aucune route d'export au contrat. Retiré de la maquette (UX-F1).
- **Total « 8 sur 312 »** : `JournalDto` n'a pas de total (curseur seul). Remplacé par « 9 lignes · 7 derniers jours » (UX-F1).
- **Lignes regroupées** : exemple « 5 × Échec de connexion » ajouté à la maquette (UX-F1) ; règle de présentation de l'audit (plan UX-E3), calculée sur les champs du contrat.
- **Badges et types** : la maquette montre Décision / Accès document / Sécurité / Annuaire / Système ; le contrat ne donne que `kind` et `source`, le badge se déduit du préfixe de `kind`. Maquette inchangée.

## Hors périmètre V1
Export du journal (aucune route en V1), alertes automatiques (ex. 3 accès documents en une minute), signature cryptographique des lignes, intégration SIEM.

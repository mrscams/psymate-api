# Document L (API) – Plan d'action · Facturation, jalon M15, partie API

28 novembre 2026. Copie de la partie API du document du web `psymate-web/docs/L-plan-facturation.md` (écrit le 22 novembre 2026, mis à jour et lancé le 28 novembre 2026), faite par la tâche M15-01. **Pour toute tâche M15, ce document fait foi (L4 DDL, L5 contrat).** Les décisions Q83 à Q95 sont inscrites dans `docs/G-decisions.md` (section G11) ; en cas de contradiction, G l'emporte sur ce document, et ce document sur C (section C14). La partie web (décisions Q68 à Q70 du G de psymate-web, tâches M15-13 à M15-22, prompts web L8) est dans le document du web.

**Qui a tranché quoi.** Le périmètre est tranché par l'utilisateur le 22 novembre 2026 (G Q67 de psymate-web, huit points, rappelés en L2.1). Les décisions techniques de L2.2 (Q83 à Q95), le modèle de L4 et le contrat de L5 **sont proposés par le plan L et n'ont pas été relus par l'utilisateur** ; le 28 novembre 2026, il a demandé de lancer le jalon sur cette base et de le mener au bout sans son intervention.

**Ce qui est copié.** Les sections L1, L2.2, L3, L4, L5, L6.1, L6.3 et L7 du document du web, telles quelles et sous leurs numéros (aucune renumérotation : les prompts de L7 citent « L2.2 Q85 », « L4 », « L5.1 », « L6.3 », qui désignent ici les mêmes sections), la partie API de L9 (prompt de revue) et la partie API de L10 (ce qui reste ouvert). Le DDL de L4 est repris octet pour octet dans `docs/E-modele-donnees.md` E3.15 ; M15-05 le reprend de même dans `V18__billing.sql`. Ne sont pas copiés, et restent dans le document du web : L2.1 au-delà du rappel ci-dessous, L6.2 (tâches web), L6.4 (lancement), L8 (prompts web), la partie web de L9 et, dans L10, l'ordre des écrans web.

**Renvois.** Un « Qn » sans précision est une décision du G de psymate-api ; « G Q67 » et « G Q67 point n » visent le G de psymate-web. Exception : en L5.5, « Q70 » (aperçu de la facture) est la décision Q70 du G de psymate-web, pas Q70 de ce dépôt (Profil 360). L'introduction de L7 renvoie à « la phrase de lancement de L6.4 », section non copiée : cette phrase est « Exécute la tâche M15-NN : ton prompt est le bloc M15-NN de la section L7 (API) ou L8 (web) de `docs/L-plan-facturation.md` ; applique aussi L6.3. », et côté API le bloc se lit dans L7 de ce document. Les prompts de L7 sont copiés tels quels : leur `../psymate-web/docs/L-plan-facturation.md` se lit, une fois ce document sur `main`, comme ce document (L6.3 règle 1) ; les fiches et les maquettes citées par M15-09 (`../psymate-web/docs/design/facturation/`) restent dans le dépôt du web.

**Numéros** (vérifiés le 28 novembre 2026 sur `main` de psymate-api, 148447f) : jalon **M15** ; décisions **Q83 à Q95**, section **G11** du G ; plan **C14** ; modèle **E3.15** ; migration **`V18__billing.sql`** (la dernière sur `main` est `V17`) ; contrat **`0.17.0`** (`contract-v0.16.0` est le dernier tag), tag `contract-v0.17.0` posé par M15-12.

**Écart constaté par M15-01** (le texte copié n'est pas corrigé) : L4, L6.1 et L7 parlent de « dix tables », mais le DDL de L4 en crée **neuf** (`billing_settings`, `billing_service`, `billing_patient`, `invoice`, `invoice_line`, `billable_session`, `billing_counter`, `payment`, `invoice_event`). Le DDL fait foi : M15-05 crée ces neuf tables, et « les dix tables de L4 » de ses critères se lit « les tables de L4 ».

## L1. Périmètre

Ce que le module doit permettre (G Q67) :

- Le praticien règle une fois ce qui figure sur ses factures : identité, SIRET, régime de TVA (trois régimes), prestations et tarifs, modalités de règlement.
- Une séance close arrive dans « À facturer ». Rien n'est facturé sans un geste du praticien.
- Il prépare une facture en brouillon (depuis des séances ou à la main), la relit, puis l'**émet** : elle reçoit un numéro et ne change plus.
- Il enregistre les paiements reçus (espèces, chèque, virement, carte, autre), en totalité ou en partie ; la facture payée devient une facture acquittée.
- Il corrige une facture émise par un **avoir**, total ou partiel, jamais en la modifiant.
- Il télécharge le PDF, l'envoie par e-mail au patient, consulte le journal des encaissements et l'exporte.
- Dans le dossier, un lien de la fiche patient ouvre la liste des factures filtrée sur le patient ; il n'y a pas d'onglet Facturation.

Hors M15 : feuilles de soins, télétransmission, tiers payant ; paiement en ligne ; facturation d'un rendez-vous non honoré ; onglet dans le dossier patient ; devis, acomptes, factures récurrentes, relances automatiques ; plusieurs taux de TVA sur une facture ; plusieurs devises ; logo sur la facture ; rapprochement bancaire ; reprise des séances closes avant la mise en service (L10) ; **ligne « Facturation » dans le détail d'un rendez-vous de l'agenda** (aucun rendez-vous n'est relié à une séance aujourd'hui : `Appointment.attachSession` n'a pas d'appelant, M11-08 a été remplacée ; la fiche 38 la garde pour plus tard).

## L2. Décisions

### L2.1 Web (rappel : inscrite dans le G de psymate-web, Q67, section G13)

Huit points tranchés par l'utilisateur le 22 novembre 2026 : France, psychologues et coachs, trois régimes de TVA ; pas de feuilles de soins ; pas de paiement en ligne ; envoi de la facture par e-mail depuis l'application ; réglages en quatrième onglet du module ; pas d'onglet dans le dossier, un lien de la fiche patient vers `/facturation?patient=:id` ; rendez-vous non honoré jamais facturé ; carreau du rail « Facture » inchangé.

Les décisions web complémentaires de ce plan (Q68 à Q70 du G de psymate-web : feature `billing`, montants, aperçu de la facture) sont dans le document du web.

### L2.2 API (à inscrire dans le G de psymate-api par M15-01, section G11)

| # | Question | Décision |
| --- | --- | --- |
| Q83 | Où vit la facturation | Nouveau module Spring Modulith **`billing`** (`com.psymate.billing`), structure par rôle (G Q33), JPA (G Q32). Dépendances autorisées : `identity :: api`, `patients :: api`, `clinical :: api`, `events :: api`, `shared`. **Aucun module ne dépend de `billing`** ; `search` et `overview` ne lisent pas ses tables. `billing` ne stocke aucun texte clinique : une ligne de facture porte une date et une désignation, jamais le titre, le corps ou le résumé d'une séance. |
| Q84 | Montants | **Centimes d'euro en entiers**, en base (`integer` / `bigint`) comme en JSON (champs entiers, décrits « centimes »). Devise unique EUR, sans champ de devise. Aucun `double`, aucun `BigDecimal` dans les DTO. Un taux de TVA est en points de base (2000 = 20 %). |
| Q85 | Ce qu'il manque aux façades | Trois petites tâches d'`api`, sans route ni migration : `clinical` publie **`SessionClosed`** et expose `SessionFacade.closedSession(id)` (M15-02) ; `identity` expose l'identité d'émetteur du praticien et la vérification du mot de passe (M15-03) ; `patients` expose l'identité de facturation d'un patient (M15-04). `billing` ne lit aucune table d'un autre module. |
| Q86 | Séance à facturer | Une séance de type `session` (ni `intake` ni `contact`) qui se clôt crée une ligne `billable_session` à l'état `pending`, par écoute de `SessionClosed`. États : `pending` → `invoiced` (dans une facture, brouillon compris) ou `dismissed` (« ne pas facturer », réversible). Une séance archivée (`EntryArchived`) encore `pending` passe à `dismissed`. Retirer une ligne d'un brouillon, supprimer le brouillon ou annuler la ligne par un avoir la remet à `pending`. Un rendez-vous de l'agenda ne crée jamais de ligne (G Q67 point 7). Si l'événement n'est pas encore traité quand le praticien agit, `billing` crée la ligne à la demande par `SessionFacade.closedSession`. |
| Q87 | Tarif d'une séance | Dans l'ordre : montant fixé sur la séance (`billable_session.amount_cents`), tarif du patient (`billing_patient.rate_cents`), prix de la prestation par défaut. Sans aucun des trois : `rateSource = none`, la séance ne peut pas entrer dans une facture (`422 RATE_REQUIRED`). Le tarif du patient vit dans `billing`, pas dans `patient.follow_up.fee` (texte libre, jamais exposé par la façade : inchangé, plus lu par le web à partir de M15-21). |
| Q88 | Cycle d'une facture | `draft` : tout se modifie, suppression physique permise (ligne ajoutée à E9). **Émission** (`issued`) : numéro attribué, date d'émission, instantané de l'émetteur et du destinataire, total figé ; plus aucune modification ni suppression, à jamais. `void` : annulée en totalité par un avoir. Le statut exposé est calculé : `draft`, `due`, `partial`, `paid`, `void` pour une facture, `issued` pour un avoir, plus le booléen `overdue` (échéance passée et reste dû supérieur à zéro, au jour de `Europe/Paris`). |
| Q89 | Numérotation | Continue, sans trou, par praticien, par nature (facture, avoir) et par année civile de la date d'émission : `F-2026-0001`, `AV-2026-0001` (préfixes des réglages, modifiables tant que rien n'est émis). Compteur `billing_counter`, verrouillé dans la transaction d'émission (`select … for update`). La demande d'émission porte `expectedNumber` ; s'il n'est plus le suivant : `409 INVOICE_NUMBER_CHANGED` avec le nouveau, rien n'est émis. |
| Q90 | Paiements | Table `payment`, jamais de suppression : une annulation pose `cancelled_at`. Un paiement ne dépasse pas le reste dû (`409 AMOUNT_EXCEEDS_BALANCE`). Un remboursement est un paiement de nature `refund`, rattaché à un avoir. `invoice.paid_cents` et `credited_cents` sont tenus à jour dans la même transaction ; le reste dû en découle. |
| Q91 | Avoir | Document de nature `credit_note`, numéroté dans sa suite, émis d'emblée (pas de brouillon d'avoir), rattaché à une facture émise. Portée `full` ou `lines` (lignes entières). Il réduit le reste dû ; s'il dépasse ce qui restait dû, `refundDue` dit ce qui est à rembourser. Un avoir total passe la facture à `void`. Les séances des lignes annulées reviennent à `pending`. |
| Q92 | TVA | Régime du praticien : `health_exempt` (mention « TVA non applicable, article 261-4-1° du Code général des impôts »), `franchise` (« TVA non applicable, article 293 B du Code général des impôts »), `liable` (un taux, prix saisis hors taxes, la facture montre HT, TVA, TTC ; TVA = arrondi au centime du total HT × taux). Le texte des mentions vient de `messages_fr.properties`, jamais d'un champ libre. Mentions à faire relire par un expert-comptable (L10). |
| Q93 | PDF | Produit par l'API avec **openhtmltopdf** (déjà au `pom.xml`, aucune dépendance ajoutée), par un moteur propre à `billing` (`clinical.export` n'est pas touché). Le PDF d'une facture ou d'un avoir est rendu **une fois, à l'émission**, et rangé chiffré par `EncryptedStorageFacade` (clé `billing/invoice/{id}.pdf`) : le document émis ne change pas si les réglages changent. Rendus à la demande, jamais rangés : le brouillon (filigrane « Brouillon »), la facture acquittée, le reçu d'un paiement. Toujours en clair, format A4. |
| Q94 | E-mail au patient | `InvoiceMailer` propre à `billing` (`JavaMailSender`, comme les autres modules). Expéditeur technique de l'application, nom affiché « {praticien} via PsyMate », `Reply-To` = e-mail affiché des réglages. Corps = le texte validé par le praticien, PDF joint ; aucun autre contenu. Plafond : **30 envois par heure et par praticien** (`429`). Chaque envoi est inscrit dans l'historique de la facture avec l'adresse. C'est, avec M14-06, le premier e-mail adressé à un non-praticien : base légale et mention à faire valider (L10). |
| Q95 | Données sensibles et conservation | L'IBAN n'est jamais en base : il est rangé chiffré (`EncryptedStorageFacade`, clé `billing/iban/{practitionerId}`), seuls ses quatre derniers caractères sont en base ; le changer exige le mot de passe du compte. Aucune facture émise, aucun avoir, aucun paiement, aucun événement d'historique n'est supprimé (obligation de conservation). Ni nom de patient, ni montant, ni IBAN, ni adresse e-mail dans les journaux applicatifs. L'archivage d'un patient ne change rien à ses factures. |

## L3. Règles métier

### L3.1 États

```text
séance close ──► billable_session.pending ──► invoiced (dans un brouillon ou une facture)
                        │  ▲                        │
            « ne pas facturer »  « remettre »        │ ligne retirée, brouillon supprimé, avoir sur la ligne
                        ▼  │                        │
                     dismissed ◄────────────────────┘ (retour à pending, pas à dismissed)

facture : draft ──émettre──► issued ──paiements──► (due | partial | paid, calculé)
            │ supprimer                 └─avoir total──► void
            ▼
        (ligne effacée)
```

### L3.2 Calculs (tous côté API)

- `line.amount = quantity × unitPrice` ; une remise est une ligne à prix négatif. `subtotal = somme des lignes` ; `subtotal` ne peut pas être négatif ni nul à l'émission (`422 EMPTY_INVOICE`).
- `vat = régime liable ? arrondi(subtotal × taux / 10000) : 0` ; `total = subtotal + vat`.
- `balanceDue = total − paid − credited` (jamais négatif ; l'excédent d'un avoir est `refundDue` sur l'avoir).
- Statut : `draft` si brouillon ; `void` si annulée ; sinon `paid` si `balanceDue = 0`, `partial` si `paid + credited > 0`, `due` sinon.
- Échéance : `on_receipt` → `dueOn = issueDate` ; `days_15`, `days_30` → `issueDate + n` ; ou une date choisie. `overdue = dueOn < aujourd'hui (Europe/Paris) et balanceDue > 0`.

### L3.3 Cas limites

- Deux onglets émettent en même temps : le second reçoit `409 INVOICE_NUMBER_CHANGED` et réaffiche le numéro.
- Une séance déjà dans un brouillon est demandée dans un autre : `409 SESSION_ALREADY_INVOICED` (avec `sessionEntryId`).
- Réglages incomplets à l'émission (SIRET, adresse, au moins une prestation ou un prix) : `422 BILLING_SETTINGS_INCOMPLETE` avec `errors[]` par champ manquant ; le brouillon reste intact.
- Patient archivé : ses factures restent lisibles, encaissables, annulables par avoir ; plus de nouveau brouillon (`409 PATIENT_ARCHIVED`).
- Paiement sur une facture `void` ou un brouillon : `409 INVOICE_NOT_PAYABLE`.
- Avoir sur une ligne déjà annulée : `409 LINE_ALREADY_CREDITED`.

## L4. Modèle de données (migration `V18__billing.sql`, écrite par M15-05)

Toutes les tables appartiennent au module `billing`. Le DDL ci-dessous est repris tel quel dans E3.15 par M15-01 et dans la migration par M15-05.

```sql
create table billing_settings (
  practitioner_id     uuid primary key,
  tenant_id           uuid not null,
  siret               varchar(14),
  address_lines       varchar(300),
  display_email       varchar(320),
  display_phone       varchar(30),
  vat_regime          varchar(20) not null default 'health_exempt'
                      check (vat_regime in ('health_exempt', 'franchise', 'liable')),
  vat_rate_bp         integer check (vat_rate_bp between 0 and 10000),
  vat_number          varchar(20),
  invoice_prefix      varchar(10) not null default 'F',
  credit_note_prefix  varchar(10) not null default 'AV',
  due_term            varchar(20) not null default 'on_receipt'
                      check (due_term in ('on_receipt', 'days_15', 'days_30')),
  cheque_payee        varchar(120),
  accepted_modes      jsonb not null default '["transfer","cheque","cash"]',
  show_bank_details   boolean not null default false,
  iban_last4          varchar(4),
  footer_note         varchar(500),
  email_templates     jsonb not null default '{}',
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  check (vat_regime <> 'liable' or vat_rate_bp is not null)
);

create table billing_service (
  id               uuid primary key,
  tenant_id        uuid not null,
  practitioner_id  uuid not null,
  label            varchar(120) not null,
  duration_min     integer check (duration_min between 1 and 600),
  price_cents      integer not null check (price_cents > 0),
  is_default       boolean not null default false,
  archived_at      timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);
create unique index billing_service_one_default
  on billing_service (practitioner_id) where is_default and archived_at is null;
create index billing_service_by_practitioner on billing_service (practitioner_id) where archived_at is null;

create table billing_patient (
  patient_id           uuid primary key,
  tenant_id            uuid not null,
  practitioner_id      uuid not null,
  rate_cents           integer check (rate_cents > 0),
  address_lines        varchar(300),
  third_party_name     varchar(200),
  third_party_address  varchar(300),
  updated_at           timestamptz not null default now()
);

create table invoice (
  id                 uuid primary key,
  tenant_id          uuid not null,
  practitioner_id    uuid not null,
  patient_id         uuid not null,
  kind               varchar(12) not null check (kind in ('invoice', 'credit_note')),
  status             varchar(8) not null check (status in ('draft', 'issued', 'void')),
  number             varchar(30),
  cancels_invoice_id uuid references invoice (id),
  recipient_name     varchar(200),
  recipient_address  varchar(300),
  issue_date         date not null,
  due_term           varchar(20) not null check (due_term in ('on_receipt', 'days_15', 'days_30', 'date')),
  due_on             date,
  footer_note        varchar(500),
  reason_code        varchar(30),
  reason_text        varchar(300),
  issuer_snapshot    jsonb,
  subtotal_cents     bigint not null default 0,
  vat_cents          bigint not null default 0,
  total_cents        bigint not null default 0,
  paid_cents         bigint not null default 0 check (paid_cents >= 0),
  credited_cents     bigint not null default 0 check (credited_cents >= 0),
  refund_due_cents   bigint not null default 0 check (refund_due_cents >= 0),
  pdf_storage_key    varchar(120),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  issued_at          timestamptz,
  voided_at          timestamptz,
  check ((status = 'draft') = (number is null)),
  check (status = 'draft' or issuer_snapshot is not null),
  check ((kind = 'credit_note') = (cancels_invoice_id is not null)),
  check (kind = 'invoice' or status <> 'draft')
);
create unique index invoice_number_per_practitioner on invoice (practitioner_id, number) where number is not null;
create index invoice_by_practitioner on invoice (practitioner_id, issue_date desc, id);
create index invoice_by_patient on invoice (patient_id, issue_date desc, id);

create table invoice_line (
  id                uuid primary key,
  invoice_id        uuid not null references invoice (id) on delete cascade,
  position          integer not null,
  service_id        uuid,
  label             varchar(200) not null,
  occurred_on       date,
  quantity          integer not null check (quantity between 1 and 999),
  unit_price_cents  integer not null,
  amount_cents      bigint not null,
  session_entry_id  uuid,
  source_line_id    uuid references invoice_line (id),
  unique (invoice_id, position)
);
create index invoice_line_by_session on invoice_line (session_entry_id) where session_entry_id is not null;

create table billable_session (
  session_entry_id  uuid primary key,
  tenant_id         uuid not null,
  practitioner_id   uuid not null,
  patient_id        uuid not null,
  occurred_on       date not null,
  modality          varchar(20) not null,
  duration_min      integer,
  state             varchar(10) not null check (state in ('pending', 'dismissed', 'invoiced')),
  service_id        uuid,
  amount_cents      integer check (amount_cents > 0),
  invoice_id        uuid references invoice (id) on delete set null,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  check ((state = 'invoiced') = (invoice_id is not null))
);
create index billable_session_pending on billable_session (practitioner_id, occurred_on) where state = 'pending';

create table billing_counter (
  practitioner_id  uuid not null,
  kind             varchar(12) not null check (kind in ('invoice', 'credit_note')),
  year             integer not null,
  last_number      integer not null check (last_number >= 0),
  primary key (practitioner_id, kind, year)
);

create table payment (
  id               uuid primary key,
  tenant_id        uuid not null,
  practitioner_id  uuid not null,
  invoice_id       uuid not null references invoice (id),
  kind             varchar(8) not null check (kind in ('payment', 'refund')),
  mode             varchar(10) not null check (mode in ('cash', 'cheque', 'transfer', 'card', 'other')),
  amount_cents     bigint not null check (amount_cents > 0),
  received_on      date not null,
  reference        varchar(120),
  note             varchar(500),
  cancelled_at     timestamptz,
  created_at       timestamptz not null default now()
);
create index payment_by_practitioner on payment (practitioner_id, received_on desc, id);
create index payment_by_invoice on payment (invoice_id);

create table invoice_event (
  id          uuid primary key,
  invoice_id  uuid not null references invoice (id) on delete cascade,
  kind        varchar(24) not null check (kind in ('draft_created', 'issued', 'sent', 'payment_recorded',
              'payment_cancelled', 'credit_note_issued', 'voided')),
  at          timestamptz not null default now(),
  detail      jsonb not null default '{}'
);
create index invoice_event_by_invoice on invoice_event (invoice_id, at desc);
```

Changements de E qui vont avec : E1 ajoute le module `billing` et ses dix tables ; E4 réserve `V18__billing.sql` (M15-05, vague M15-A, dépend de V01) ; E9 ajoute une ligne « `invoice` à l'état `draft`, ses `invoice_line` et ses `invoice_event` : suppression physique à la demande du praticien ; jamais une facture émise, un avoir, un paiement » ; `invoice_event` est append-only (`@Immutable`) ; `payment` ne se modifie que par `cancelled_at`.

## L5. Contrat d'API (cible `0.17.0`, tag `contract-v0.17.0` posé par M15-12)

Toutes les routes exigent une session praticien ; chaque ressource est filtrée par `practitioner_id` (un identifiant d'un autre praticien répond `404`). Erreurs RFC 9457, `errors[]` par champ. Champs inconnus refusés (`UNKNOWN_FIELD`), comme ailleurs. Montants en centimes entiers ; dates `AAAA-MM-JJ` ; instants en UTC ISO 8601.

### L5.1 Réglages, prestations, profil de facturation du patient

| Route | Tâche | Comportement |
| --- | --- | --- |
| `GET /api/me/billing-settings` | M15-06 | `{ identity: { name, title, professionalNumber } (lecture seule, de identity), siret, addressLines, displayEmail, displayPhone, vat: { regime, rateBp?, vatNumber?, mention }, numbering: { invoicePrefix, creditNotePrefix, nextInvoiceNumber, nextCreditNoteNumber, locked }, payment: { dueTerm, chequePayee, acceptedModes[], showBankDetails, ibanLast4? }, footerNote, emailTemplates: { invoice: { subject, body }, reminder: { subject, body } }, missing: [] }`. Sans ligne en base : valeurs par défaut, adresse et e-mail proposés depuis identity. `missing` liste ce qui empêche d'émettre (`siret`, `addressLines`, `service`). |
| `PUT /api/me/billing-settings` | M15-06 | Remplace les champs modifiables. `422` : `SIRET_INVALID` (14 chiffres et clé de Luhn), `VAT_RATE_REQUIRED`, `VAT_NUMBER_REQUIRED` (régime `liable`), `OUT_OF_RANGE`, `INVALID_VALUE`. `409 NUMBERING_LOCKED` si un préfixe change après une première émission. |
| `PUT /api/me/billing-settings/iban` | M15-06 | `{ iban, currentPassword }` → `204`. `422 IBAN_INVALID` (format et clé mod 97), `403 PASSWORD_INVALID`. `DELETE` de la même route efface l'IBAN (mot de passe dans le corps). |
| `GET /api/me/billing-services` | M15-05 | `{ services: [{ id, label, durationMin?, price, isDefault }] }`, non archivées, la prestation par défaut d'abord puis par libellé. |
| `POST /api/me/billing-services` | M15-05 | `{ label, durationMin?, price, isDefault? }` → `201`. La première créée devient la prestation par défaut. 50 au plus (`422 SERVICE_LIMIT_REACHED`). |
| `PATCH`, `DELETE /api/me/billing-services/{id}` | M15-05 | Fusion partielle ; `isDefault: true` retire le défaut de l'autre. `DELETE` archive (`204`, idempotent) ; `409 SERVICE_IS_DEFAULT` s'il reste d'autres prestations. |
| `GET`, `PUT /api/patients/{id}/billing-profile` | M15-05 | `{ rate?, addressLines?, thirdPartyName?, thirdPartyAddress? }`. `PUT` remplace ; `null` efface. `409 PATIENT_ARCHIVED` en écriture. |

### L5.2 Séances à facturer

| Route | Tâche | Comportement |
| --- | --- | --- |
| `GET /api/billing/billable-sessions?until=&patientId=` | M15-07 | `{ groups: [{ patient: { id, displayName, archived }, rate?, rateSource: session, patient, service, none, openDraftCount, lastInvoice?: { number, issueDate }, sessions: [{ sessionEntryId, occurredOn, modality, durationMin?, serviceId?, serviceLabel?, amount? }] }], dismissedCount }`. État `pending` seulement, groupes par nom affiché, séances par date. |
| `GET /api/billing/billable-sessions/dismissed` | M15-07 | Les séances écartées des 90 derniers jours, même forme sans regroupement. |
| `POST /api/billing/billable-sessions/{sessionEntryId}/dismiss`, `/restore` | M15-07 | `204`, idempotents. Crée la ligne à la demande si la séance est close et pas encore connue (Q86). `404 SESSION_NOT_BILLABLE` (séance ouverte, d'accueil, de contact, ou d'un autre praticien), `409 SESSION_ALREADY_INVOICED`. |
| `PATCH /api/billing/billable-sessions/{sessionEntryId}` | M15-07 | `{ amount?, serviceId? }` ; `null` rend le tarif calculé. |

### L5.3 Factures

| Route | Tâche | Comportement |
| --- | --- | --- |
| `POST /api/invoices` | M15-08 | `{ patientId, sessionEntryIds?: [], lines?: [] }` → `201` brouillon. Une ligne par séance (désignation et prix de Q87). `409 PATIENT_ARCHIVED`, `409 SESSION_ALREADY_INVOICED`, `422 RATE_REQUIRED`. |
| `POST /api/invoices/batch` | M15-08 | `{ sessionEntryIds: [] }` (200 au plus) → `201 { invoices: [{ id, patient, total }] }`, un brouillon par patient, tout ou rien. |
| `GET /api/invoices?status=&overdue=&kind=&patientId=&q=&from=&cursor=` | M15-08 | `{ items: [InvoiceSummary], nextCursor? }`. Brouillons d'abord, puis date d'émission décroissante. `status` ∈ `draft`, `due`, `partial`, `paid`, `void` ; `q` cherche dans le numéro et le nom affiché du patient (2 caractères au moins). `InvoiceSummary = { id, kind, number?, status, overdue, overdueDays?, patient: { id, displayName }, issueDate, dueOn?, subject: { sessionCount, firstOn?, lastOn?, label? }, total, balanceDue, lastPaymentMode?, creditNoteNumber?, cancelsNumber? }`. |
| `GET /api/invoices/{id}` | M15-08 | `Invoice = InvoiceSummary + { issuer: { name, title, professionalNumber, siret, addressLines, displayEmail, displayPhone, vatMention, vatRateBp?, vatNumber?, chequePayee, acceptedModes[], bankDetailsShown }, recipient: { name, addressLines, patientName? }, lines: [{ id, position, occurredOn?, serviceId?, label, quantity, unitPrice, amount, sessionEntryId?, credited }], dueTerm, footerNote?, subtotal, vat, paid, credited, refundDue, nextNumberPreview? (brouillon), payments: [Payment], creditNotes: [{ id, number, total, issueDate }], cancels?: { id, number }, history: [{ kind, at, detail }], updatedAt }`. Pour un brouillon, `issuer` reflète les réglages du moment ; pour un document émis, l'instantané. |
| `PATCH /api/invoices/{id}` | M15-08 | Brouillon seulement (`409 INVOICE_ISSUED` sinon). Fusion partielle de `patientId` (tant qu'aucune ligne de séance), `recipient`, `issueDate`, `dueTerm`, `dueOn`, `footerNote`, et `lines` (remplacement complet, 100 au plus ; une ligne de séance retirée rend la séance à `pending`). Réponse : la facture relue. |
| `DELETE /api/invoices/{id}` | M15-08 | Brouillon seulement, `204` ; ses séances reviennent à `pending`. |
| `POST /api/invoices/{id}/issue` | M15-09 | `{ expectedNumber, alreadyPaid?: { mode, receivedOn, reference? } }` → `200` la facture émise. `409 INVOICE_NUMBER_CHANGED` (`nextNumber` dans le problème), `422 BILLING_SETTINGS_INCOMPLETE`, `422 EMPTY_INVOICE`, `422` par champ. |
| `GET /api/invoices/by-number/{number}` | M15-09 | Même réponse que `GET /api/invoices/{id}`. |
| `GET /api/invoices/{id}/pdf?variant=` | M15-09 | `application/pdf`. Brouillon : rendu à la demande, filigrane. Émis : le fichier rangé à l'émission. `variant=receipted` : facture acquittée, `409 INVOICE_NOT_PAID` si elle ne l'est pas. Nom de fichier : le numéro. |
| `POST /api/invoices/{id}/duplicate` | M15-09 | `201` un brouillon avec les mêmes lignes sans leurs séances. |
| `POST /api/invoices/{id}/credit-notes` | M15-11 | `{ scope: full, lines, lineIds?, reasonCode: session_cancelled, wrong_amount, wrong_recipient, other, reasonText?, expectedNumber }` → `201` l'avoir (`refundDue` compris). `409 CREDIT_NOTE_NUMBER_CHANGED`, `409 LINE_ALREADY_CREDITED`, `409 INVOICE_VOID`, `422 REASON_TEXT_REQUIRED` (motif `other`). |
| `POST /api/invoices/{id}/send` | M15-11 | `{ to, subject, body, template: invoice, reminder }` → `202`. `422 EMAIL_INVALID`, `409 INVOICE_NOT_ISSUED`, `429`. Sujet 200 caractères, corps 5 000. |

### L5.4 Paiements, chiffres

| Route | Tâche | Comportement |
| --- | --- | --- |
| `POST /api/invoices/{id}/payments` | M15-10 | `{ amount, mode, receivedOn, reference?, note? }` → `201 Payment = { id, invoiceId, invoiceNumber, kind, mode, amount, receivedOn, reference?, note?, cancelledAt? }`. Sur un avoir dont `refundDue > 0` : nature `refund`, plafonnée à `refundDue`. `409 AMOUNT_EXCEEDS_BALANCE`, `409 INVOICE_NOT_PAYABLE`, `422 MODE_NOT_ACCEPTED` (mode absent des réglages, `other` toujours accepté), `422` date future. |
| `POST /api/payments/{id}/cancel` | M15-10 | `200` le paiement annulé ; idempotent. |
| `GET /api/payments?from=&to=&mode=&cursor=` | M15-10 | `{ items: [Payment + { patient: { id, displayName }, partial }], nextCursor?, totals: { amount, paymentCount, refundCount, byMode: [{ mode, amount }] } }`. Les totaux portent sur toute la période (remboursements déduits, annulés exclus). |
| `GET /api/payments/{id}/receipt` | M15-10 | PDF du reçu, rendu à la demande. |
| `GET /api/payments/export?from=&to=&format=csv,pdf&identity=document_number,full_name` | M15-10 | Fichier, téléchargement direct. Colonnes : date, numéro, mode, référence, montant ; le nom seulement avec `full_name`. Un an au plus par export. |
| `GET /api/billing/summary` | M15-10 | `{ month, collected, paymentCount, outstanding, outstandingCount, overdue, overdueCount, oldestOverdueDays?, billableCount, billableAmount, billableWithoutRate }`. |
| `GET /api/patients/{id}/billing` | M15-10 | `{ patient: { id, displayName, archived }, balanceDue, dueInvoiceCount, nextDueOn?, billableCount, rate?, rateSource, recipient: { name, addressLines } }`. |

### L5.5 Ce qui diffère des fiches 33 à 39 (à reporter par M15-13)

Montants en centimes ; pas de route d'aperçu (l'aperçu est dessiné par le web, Q70) ; pas de `sendByEmail` à l'émission (le web enchaîne émission puis envoi) ; tarif et adresse du patient par `billing-profile`, pas par `PATCH /api/patients/{id}` ; pas de champ `billing` sur la clôture de séance (le web appelle `dismiss`, ou crée puis émet une facture, après la clôture) ; export du journal en téléchargement direct ; `reasonCode` au lieu d'un libellé ; ligne de l'agenda hors M15.

### L5.6 Écarts constatés à l'implémentation (tenu par le couloir billing, M15-05 à M15-11)

Le contrat fusionné fait foi ; cette liste dit où il précise ou s'écarte de L5.

- **Toutes les routes** : une erreur de champ est rangée dans `errors[]` sous le code racine `422 VALIDATION_FAILED` (codes de champ `SIRET_INVALID`, `VAT_RATE_REQUIRED`, `VAT_NUMBER_REQUIRED`, `IBAN_INVALID`, `OUT_OF_RANGE`, `INVALID_VALUE`, `INVALID_FORMAT`, `REQUIRED`, `UNKNOWN_FIELD`), toutes rapportées ensemble. Un montant décimal (`60.0` compris) ou en chaîne est refusé (`INVALID_FORMAT`). Un patient d'un autre praticien répond `404 PATIENT_NOT_FOUND`. (M15-05, M15-06)
- **Prestations** (M15-05) : `POST` répond `201` sans `Location` ; `isDefault: false` sur la prestation par défaut → `422 INVALID_VALUE` ; `SERVICE_LIMIT_REACHED` sans `errors[]` ; `PATCH` avec `durationMin: null` efface la durée, tout autre champ absent ou null est inchangé ; archiver retire le défaut.
- **Profil de facturation** (M15-05) : `PUT` remplace tout (absent, null ou vide efface) ; `GET` sans ligne renvoie tous les champs à null.
- **Réglages** (M15-06) : `PUT` remplace tout et répond `200` avec les réglages relus ; un bloc absent reprend ses valeurs par défaut (le web renvoie toujours `numbering`, sinon `409 NUMBERING_LOCKED` dès que les préfixes ne sont plus F/AV) ; les champs en lecture seule du `GET` (`identity`, `vat.mention`, `numbering.next*`, `numbering.locked`, `payment.ibanLast4`, `missing`) sont refusés en `PUT` (`UNKNOWN_FIELD`) ; `vat.mention` vaut `null` en régime `liable` (Q92 ne donne pas de texte) ; `nextInvoiceNumber` et `nextCreditNoteNumber` sont formatés (`F-2026-0001`) ; `locked` = une ligne `billing_counter` avec `last_number > 0` ; `missing.addressLines` tient compte de l'adresse proposée par identity.
- **IBAN** (M15-06) : `429 TOO_MANY_ATTEMPTS` (en-tête `Retry-After`) après 5 mots de passe faux en 15 minutes par praticien, PUT et DELETE confondus ; `DELETE` répond `204` même sans IBAN.
- **Séances à facturer** (M15-07) : chaque séance porte, en plus de L5.2, son propre `rateSource` (`session`, `patient`, `service`, `none`) ; `amount` y est le tarif retenu (null si `none`), pas seulement un montant fixé à la main ; `serviceId` et `serviceLabel` sont la prestation retenue (celle choisie par `PATCH`, sinon la prestation par défaut). Q87 précisé : le troisième rang est le prix de la prestation retenue, et le tarif du patient passe avant une prestation choisie (Q87 appliquée à la lettre, à faire valider ; le web fixe alors `amount`). Groupe : `rate` est le tarif commun (tarif du patient, sinon prix de la prestation par défaut), sans montant fixé ; `rateSource` vaut `session` dès qu'une séance du groupe a un montant fixé, sinon l'origine de `rate`. Ordre : groupes par nom affiché sans tenir compte de la casse ni des accents, séances par date croissante ; écartées par date décroissante. `until` absent : sans limite ; `patientId` d'un autre praticien : `404 PATIENT_NOT_FOUND` ; `until` ou `patientId` illisible : `422 VALIDATION_FAILED` (`INVALID_FORMAT`). `dismissedCount` et `/dismissed` couvrent les séances écartées dont le jour (`occurred_on`, `occurredOn`) est aujourd'hui (au jour de `Europe/Paris`) ou l'un des 90 jours précédents ; `dismissedCount` suit `patientId`, pas `until`. `/dismissed` répond `{ sessions: [{ patient: { id, displayName, archived }, sessionEntryId, occurredOn, modality, durationMin?, serviceId?, serviceLabel?, amount?, rateSource }] }`. `openDraftCount` = brouillons de facture du patient ; `lastInvoice` = dernière facture (`kind invoice`) `issued` ou `void` par date d'émission. `PATCH` répond `200` avec la séance (forme d'un élément de `sessions`) ; absent = inchangé, `null` = effacé ; `amount` `OUT_OF_RANGE` ou `INVALID_FORMAT`, `serviceId` `INVALID_VALUE` (prestation archivée, inconnue ou d'un autre praticien) ou `INVALID_FORMAT` ; permis sur une séance écartée. `404 SESSION_NOT_BILLABLE` aussi pour une séance archivée, mais seulement à la création à la demande et pour `restore` d'une séance écartée dont la séance a été archivée depuis : `dismiss` et `PATCH` d'une ligne `pending` dont la séance vient d'être archivée (événement pas encore traité) réussissent, et l'écoute de `EntryArchived` l'écarte ensuite ; une clôture traitée après l'archivage ne crée pas de ligne ; `409 SESSION_ALREADY_INVOICED` sans `sessionEntryId` dans le problème (il est dans l'URL) ; `400 MALFORMED_REQUEST` pour un identifiant qui n'est pas un UUID.
- **Factures, brouillons et liste** (M15-08) : **Calculs** : TVA = `floor(subtotal × taux / 10000 + 0,5)` (arrondi au centime, demi supérieur), même formule exacte en Java (`InvoiceTotals`) et en SQL (`numeric`) ; le total d'un brouillon est recalculé à chaque lecture avec les réglages du moment (liste comprise), ses colonnes de montants à chaque écriture. **`POST /api/invoices`** : `201` avec la facture complète (forme `Invoice`), sans `Location` ; un brouillon vide est permis (`EMPTY_INVOICE` reste à l'émission). Ligne demandée = `{ sessionEntryId?, serviceId?, label, occurredOn?, quantity? (1 à 999, 1 si absente), unitPrice }` ; `unitPrice` entier de −10 000 000 à 10 000 000 centimes (borne ajoutée), négatif pour une remise, jamais décimal ni chaîne (`INVALID_FORMAT`) ; `id`, `position`, `amount`, `credited` refusés (`UNKNOWN_FIELD`). Une ligne avec `sessionEntryId` facture cette séance avec la désignation et le prix donnés (son `occurredOn` est toujours le jour de la séance). Lignes des `sessionEntryIds` d'abord, par date, puis les lignes données dans leur ordre. Ligne de séance : désignation « {libellé de la prestation retenue} du JJ/MM/AAAA », sinon « Séance du JJ/MM/AAAA » (`billing.line.*`, français), `serviceId` = prestation retenue, prix = tarif de Q87, quantité 1. 100 lignes libres et 200 lignes de séance au plus (`lines` `OUT_OF_RANGE`) ; `sessionEntryIds` 200 au plus, sans doublon ; doublon ou séance d'un autre patient : `sessionEntryIds[i]` ou `lines[i].sessionEntryId` `INVALID_VALUE` ; séance inconnue : création à la demande comme L5.2, sinon `404 SESSION_NOT_BILLABLE` (une séance écartée entre dans une facture, sauf si sa séance a été archivée) ; `409 SESSION_ALREADY_INVOICED` porte `sessionEntryId`, `409 PATIENT_ARCHIVED` porte `patientId`, `422 RATE_REQUIRED` (code racine, pas `VALIDATION_FAILED`) porte `sessionEntryId` ; patient d'un autre praticien `404 PATIENT_NOT_FOUND`. Valeurs par défaut : date d'émission prévue = aujourd'hui (Europe/Paris) ; `dueTerm` et `footerNote` copiés des réglages ; destinataire (`recipient_name`, `recipient_address`, renseignés dès la création) = tiers payeur du profil de facturation s'il est renseigné, sinon prénom et nom du patient (à défaut son nom affiché) avec l'adresse du profil. **`POST /api/invoices/batch`** : `sessionEntryIds` de 1 à 200 (`REQUIRED` si vide), sans doublon ; `invoices[].patient` = `{ id, displayName, archived }` ; brouillons dans l'ordre de première apparition de chaque patient ; tout ou rien. **`GET /api/invoices`** : `limit` ajouté (1 à 100, 50 par défaut) ; `from` = date d'émission (prévue pour un brouillon) ≥ `from` ; `status` limité aux cinq valeurs de L5.3 (un avoir se filtre par `kind=credit_note`) ; `q` de 2 à 100 caractères, sans tenir compte de la casse ni des accents, cherche aussi dans le nom du destinataire ; le nom affiché du patient est lu par la façade des patients (`billingIdentities` des patients facturés, filtrés en mémoire), `billing` ne lisant aucune table de `patients` ; curseur opaque (illisible : `422 cursor INVALID_FORMAT`) ; ordre : brouillons, date d'émission décroissante, identifiant croissant (ordre de PostgreSQL) ; erreurs de paramètre `422 VALIDATION_FAILED` (`status`, `kind` `INVALID_VALUE`, `q`, `limit` `OUT_OF_RANGE`, `overdue`, `from`, `patientId` `INVALID_FORMAT`). `InvoiceSummary.patient` porte aussi `archived` ; `dueOn` = échéance effective, aussi pour un brouillon ; `subject.firstOn`/`lastOn` = première et dernière date des lignes, `subject.label` = désignation de la première ligne quand aucune ligne n'est une séance ; `balanceDue` = total pour un brouillon (jamais négatif), 0 pour un avoir ; `lastPaymentMode`, `creditNoteNumber` (dernier avoir) et `cancelsNumber` sont déjà lus dans `payment` et `invoice` (vides tant que M15-10 et M15-11 n'écrivent rien). **`GET /api/invoices/{id}`** : `404 INVOICE_NOT_FOUND`, `400 MALFORMED_REQUEST` ; `payments` reste vide jusqu'à M15-10 (schéma `BillingPaymentDto` = `Payment` de L5.4) ; `creditNotes`, `cancels` et `lines[].credited` sont lus dans `invoice` et `invoice_line` ; `history` du plus ancien au plus récent, `draft_created` avec `detail = { sessionCount, batch }` ; `nextNumberPreview` dans la suite de l'année de la date d'émission prévue du brouillon (préfixe des réglages) ; `issuer` d'un document émis lu dans `issuer_snapshot`, dont les clés sont celles du bloc `issuer` plus `vatRegime` (`BillingIssuer.toSnapshot`, à écrire par M15-09) ; `issuer.vatMention` vaut `null` en régime `liable`, `issuer.bankDetailsShown` = réglage activé et IBAN enregistré ; `recipient.patientName` = prénom et nom du patient quand ils diffèrent du nom du destinataire. **`PATCH`** : `recipient: null` rend le destinataire par défaut ; `patientId`, `issueDate`, `dueTerm`, `lines` à `null` : `REQUIRED` ; `patientId` ne change que si aucune ligne de séance du patient d'avant ne reste (`422 patientId INVALID_VALUE`, pas un `409`), le destinataire suit alors le nouveau patient sauf `recipient` donné ; `dueTerm: date` exige `dueOn` (`REQUIRED`), `dueOn` avant `issueDate` : `OUT_OF_RANGE`, `dueOn` hors échéance `date` : `INVALID_VALUE` (l'échéance d'une autre nature efface `due_on`) ; `lines` suit les règles de `POST`, les lignes sont recréées (nouveaux `id`), une séance ajoutée passe à `invoiced`, une séance retirée revient à `pending` ; `409 INVOICE_ISSUED` aussi pour une facture `void` et un avoir. **`DELETE`** : `204`, puis `404` ; suppression physique du brouillon (E9 : `TableOwnershipTests.PHYSICALLY_DELETABLE` reçoit `invoice`, `invoice_line`, `invoice_event`).

## L6. Tâches, couloirs et parallélisme

### L6.1 API (`psymate-api`)

| ID | Titre | Couloir | Dépend de | Parallèle avec |
| --- | --- | --- | --- | --- |
| M15-01 | Décisions Q83 à Q95 et documentation ; copie de ce plan côté API | docs | — | M15-02 à M15-05 |
| M15-02 | `SessionClosed` et `SessionFacade.closedSession` | clinical (api, service) | — | M15-01, 03, 04, 05 |
| M15-03 | Identité d'émetteur et vérification du mot de passe | identity (api) | — | M15-01, 02, 04, 05 |
| M15-04 | Identité de facturation d'un patient | patients (api) | — | M15-01, 02, 03, 05 |
| M15-05 | Module `billing` : socle, `V18`, prestations, profil de facturation du patient | billing | — | M15-01 à M15-04 |
| M15-06 | Réglages de facturation, IBAN | billing | M15-03, M15-05 | — |
| M15-07 | Séances à facturer | billing | M15-02, M15-04, M15-06 | — |
| M15-08 | Factures : brouillons, liste | billing | M15-07 | — |
| M15-09 | Émission, numérotation, PDF | billing | M15-08 | — |
| M15-10 | Paiements, journal, export, chiffres | billing | M15-09 | — |
| M15-11 | Avoirs, envoi par e-mail | billing | M15-10 | — |
| M15-12 | Seed, tag de contrat, suivi | racine (`tools/seed`) | M15-11 | — |

**Vagues.** M15-A : 01 ∥ 02 ∥ 03 ∥ 04 ∥ 05 (cinq couloirs disjoints : docs, clinical, identity, patients, billing). M15-B : 06. M15-C : 07. M15-D : 08. M15-E : 09. M15-F : 10. M15-G : 11. M15-H : 12. Le couloir `billing` n'a jamais deux agents à la fois. Effort indicatif : 10 jours.

**Agents.** `backend-module-dev` (M15-02 à M15-11), `seed-perf` (M15-12), Claude directement pour M15-01. `reviewer` avant chaque fusion, puis `make log`.

**Voisinage.** Les jalons M14 (prise de rendez-vous) et M16 (version arabe) sont fusionnés. M15 ne touche ni `agenda`, ni `identity/preferences`, ni `directory`. **Version arabe** : l'API a trois fichiers de messages (`messages_fr`, `messages_en`, `messages_ar`) ; une clé ajoutée par M15 suit la règle du dépôt à ce sujet (voir `CLAUDE.md` et `docs/M-plan-version-arabe-api.md`) ; les factures, les avoirs, les reçus et leurs mentions légales restent en français quelle que soit la langue du praticien. Fichiers que plusieurs tâches M15 touchent en parallèle : `contract/openapi.yaml`, `docs/generated/**`, `ContractIT`, les listes des tests d'architecture, `G`, `C`, `D`, `E`, `CLAUDE.md`. En cas de conflit au rebase : pour `contract/openapi.yaml` et `docs/generated/**`, reprendre la version de `main` puis regénérer (`make contract`), jamais résoudre à la main ; pour une liste, garder les deux côtés.

### L6.3 Règles communes aux tâches API

1. **Lecture.** `CLAUDE.md`, puis ce plan : `../psymate-web/docs/L-plan-facturation.md` tant que `docs/L-plan-facturation-api.md` n'est pas sur `main`, ensuite la copie de l'API. Le chemin `../psymate-web` part de la racine du dépôt principal ; depuis un worktree, c'est `$(git rev-parse --git-common-dir)/../../psymate-web/docs/L-plan-facturation.md`. G l'emporte sur L, L sur C.
2. **Version du contrat.** `0.16.0` est taguée (`contract-v0.16.0`, M16). La première tâche M15 qui change `contract/openapi.yaml` (M15-05) passe `app.contract.version` à `0.17.0` dans sa PR ; les suivantes ajoutent leurs routes à `0.17.0` sans changer la version. Règle générale, si un autre jalon passe avant : regarder si le tag `contract-v<version courante>` existe (`git fetch --tags && git tag -l "contract-v<version>"`) ; s'il existe, passer à la mineure suivante ; sinon ne pas toucher à la version. M15-12 pose le tag de la version atteinte (`contract-v0.17.0`).
3. **Un seul `verify` à la fois sur le poste** (CLAUDE.md). Avant `./mvnw verify` ou `bash tools/verify-fast.sh`, prendre le verrou partagé des worktrees : `LOCK="$(git rev-parse --git-common-dir)/verify.lock"; until mkdir "$LOCK" 2>/dev/null; do sleep 30; done`, et le rendre à la fin, succès ou échec : `rmdir "$LOCK"`. Un verrou vieux de plus de 45 minutes est considéré comme abandonné et peut être retiré. Un test isolé (`./mvnw test -Dtest=…`) n'a pas besoin du verrou.
4. **Début et fin de tâche** (consigne de l'utilisateur du 28 novembre 2026). Au début : `make log TASK=<ID> AGENT=<agent> STATUS=in-progress SUMMARY="…"`. À la fin : `./mvnw verify` vert, sous-agent `reviewer` avec le prompt de L9, corrections des points bloquants, PR avec le template, `make log … STATUS=done … PR=<url>`, événements commités. Dès la PR ouverte, la session **active Auto-fix** sur sa PR (outils `ccd_pr` : `get_status`, `bind_pr` si besoin, puis la surveillance) et répare elle-même une CI rouge, un conflit ou une remarque de revue. **Chaque session fusionne sa propre PR**, en squash (`gh pr merge <n> --squash --delete-branch`), dès que la CI est verte et que `reviewer` n'a rien de bloquant ; si la fusion lui est refusée, elle le dit dans son dernier message et la session orchestratrice fusionne. Aucune tâche n'attend l'utilisateur : M15-01 et M15-13 se fusionnent comme les autres, et disent en tête de leur PR que les décisions Q83 à Q95 et les maquettes complétées n'ont pas été relues par lui.
5. **Jamais** : un nom de patient, un montant, un IBAN ou une adresse e-mail dans un journal ; une dépendance Maven ; un appel externe ; un fichier hors du couloir de la tâche (les listes des tests d'architecture et `tools/it-slices.txt` sont permises à la tâche qui crée le module).

## L7. Prompts API

Manœuvre : dans `psymate-api`, `git checkout main && git pull && claude --worktree <ID>`, coller le prompt (ou la phrase de lancement de L6.4). Chaque prompt se complète des règles communes de L6.3 : lecture du plan, version du contrat, verrou de `verify`, début et fin de tâche. À la fin : sous-agent `reviewer`, lecture humaine (migration, diff de `contract/openapi.yaml`, un IT), `gh pr merge <n> --squash --delete-branch`.

### M15-01 — Décisions et documentation

```text
Tâche M15-01 — Inscrire le jalon M15 « Facturation » dans la documentation de psymate-api (docs/, CLAUDE.md et .claude/agents seulement ; aucun code Java, aucune migration).

Contexte : lis ../psymate-web/docs/L-plan-facturation.md en entier (il fait foi pour cette tâche), docs/G-decisions.md (G10 et le format des lignes Q71 à Q82, la table G5), docs/K-plan-prise-de-rendez-vous-api.md (modèle d'un plan de jalon copié du web), docs/A-architecture.md (A3, A5 : modules et dépendances), docs/C-plan.md (C1 et la section C13 du jalon M14 : format), docs/D-orchestration-backend.md (D1, D3, D5), docs/E-modele-donnees.md (E1, E3.14, E4, E9), CLAUDE.md, .claude/agents/backend-module-dev.md.

Objectif :
- Nouveau fichier docs/L-plan-facturation-api.md : copie des sections L1, L2.2, L3, L4, L5, L6.1, L6.3, L7 et de la partie API de L9 et L10 du document du web, avec en tête la date, le renvoi au document du web pour la partie web, la phrase « Pour toute tâche M15, ce document fait foi (L4 DDL, L5 contrat) » et la mention que L2.2, L4 et L5 sont proposés par le plan et n'ont pas été relus par l'utilisateur, qui a demandé le 28 novembre 2026 de lancer le jalon sur cette base.
- G : nouvelle section « G11. Facturation, jalon M15, module billing (22 novembre 2026) » avec Q83 à Q95 copiées de L2.2, une ligne par question, même style que Q71 à Q82. La phrase d'introduction dit : périmètre tranché par l'utilisateur le 22 novembre 2026 (G Q67 de psymate-web) ; décisions techniques proposées par le plan L, non relues par l'utilisateur, appliquées à sa demande du 28 novembre 2026. Une ligne dans la table G5.
- A : A3 et A5 ajoutent le module billing et ses dépendances (identity, patients, clinical, events, shared ; personne ne dépend de billing).
- C : C1 ajoute le jalon M15 (objectif, démontrable, effort indicatif 10 j) ; nouvelle section C14 avec le tableau des tâches M15-01 à M15-12 au format de C13, critères repris mot pour mot de L7.
- D : D1, D3, D5 reçoivent les lignes, vagues et couloirs de L6.1, dont un nouveau couloir billing.
- E : E1 ajoute billing et ses dix tables ; nouvelle section E3.15 avec le DDL de L4, identique octet pour octet ; E4 ajoute la ligne V18__billing.sql (M15-05, vague M15-A, dépend de V01) ; E9 reçoit la ligne des brouillons de L4.
- CLAUDE.md : billing rejoint la liste des modules avec ses dépendances ; une ligne « La facturation vit dans le module billing (G Q83) : montants en centimes entiers (Q84), facture émise jamais modifiée ni supprimée, correction par avoir (Q88, Q91), rien de clinique sur une facture. Pour toute tâche M15, docs/L-plan-facturation-api.md fait foi (L4 DDL, L5 contrat). »
- .claude/agents/backend-module-dev.md : ajoute « docs/L-plan-facturation-api.md pour toute tâche M15 » à sa liste de lecture.

Périmètre : docs/**, CLAUDE.md, .claude/agents/backend-module-dev.md. Rien d'autre.

Critères d'acceptation :
- G Q83 à Q95 existent, avec la mention exacte de qui a tranché quoi ;
- chaque tâche M15-01 à M15-12 a sa ligne dans C et D avec les dépendances de L6.1 ;
- le DDL de E3.15 est identique octet pour octet au bloc SQL de L4 ;
- E4 porte V18, E9 porte la ligne des brouillons ;
- aucune source Java, aucune migration, aucun contrat modifié.

Interdits : toute modification de code, de migration ou de contrat ; réécrire docs/progress/index.html ou un événement existant ; inscrire une décision que L ne contient pas ; écrire qu'une décision de L2.2 a été « tranchée par l'utilisateur ».

Règles d'exécution : L6.3 (règles 1 et 4). Aucune source n'étant touchée, ./mvnw verify n'est pas nécessaire.

Livrable : PR « M15-01 Facturation : décisions et documentation », qui dit en tête que les décisions Q83 à Q95 sont proposées par le plan et non relues par l'utilisateur, puis make log TASK=M15-01 AGENT=claude STATUS=done SUMMARY="…" PR=<url>. Fusion : L6.3 règle 4.
```

### M15-02 — `SessionClosed` et `SessionFacade`

```text
Tâche M15-02 — Faire connaître la clôture d'une séance aux autres modules (dépôt psymate-api, module clinical : package api et SessionService seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, ../psymate-web/docs/L-plan-facturation.md (L2.2 Q85 et Q86, L6.3). Code : clinical/service/SessionService.java (close, start, contact), clinical/api (SessionView, EntryFacade, EntryCorrected : forme d'un événement et d'une façade), clinical/repository/EntryRepository (SESSION_VIEW_SELECT, openSession, currentSession), events/api (DomainEvent, DomainEventPublisher), un écouteur existant (clinical/service/IntakeSessionListener), ApiBoundaryTests, ApiUsageTests et ReservesConformanceIT (ce qu'une API encore sans appelant doit porter, voir @Reserved sur PublishedProfileFacade).

Objectif :
- clinical.api.SessionClosed : événement de domaine (tenantId, sessionId, patientId, practitionerId, kind, modality, occurredFrom, closedAt, durationMin), publié par SessionService.close dans la transaction de clôture, une fois par clôture réussie, pour tout kind ; publié aussi par la création d'une séance de contact, qui naît close. La publication de EntryCorrected ne change pas.
- clinical.api.SessionFacade : Optional<SessionView> closedSession(UUID sessionId) (vide si la séance n'existe pas, est archivée ou n'est pas close). SessionView reçoit practitionerId s'il n'y est pas. Implémentation dans clinical/service, lecture par EntryRepository.
- Aucune route, aucun changement de contrat, aucune migration.

Périmètre : src/main/java/com/psymate/clinical/api/**, clinical/service/SessionService.java et l'implémentation de la façade, clinical/repository/EntryRepository si une requête manque, leurs tests. Rien dans les autres modules.

Critères d'acceptation :
- clore une séance publie exactement un SessionClosed avec la durée et l'instant de clôture retenus ; clore une séance déjà close n'en publie pas ;
- closedSession renvoie la vue d'une séance close, vide pour une séance ouverte, archivée ou inconnue ;
- les tests d'architecture et ReservesConformanceIT sont verts (marque @Reserved « M15-07, G Q86 » si c'est la règle pour une API sans appelant) ;
- contract/openapi.yaml inchangé ; ./mvnw verify vert.

Tests : unitaire de SessionService (publication), IT de clôture (événement publié une fois), IT de la façade.

Interdits : toucher à un autre module ; changer la forme de EntryCorrected, de CloseSessionRequest ou d'une réponse HTTP ; journaliser un contenu de séance.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-02 Clôture de séance : événement et façade », puis make log TASK=M15-02 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-03 — Identité d'émetteur et mot de passe

```text
Tâche M15-03 — Exposer aux autres modules l'identité d'émetteur d'un praticien et la vérification de son mot de passe (dépôt psymate-api, module identity : package api et son implémentation seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, ../psymate-web/docs/L-plan-facturation.md (L2.2 Q85, Q95 ; L5.1 ; L6.3). Code : identity/api/PractitionerFacade.java et identity/auth/service/PractitionerFacadeImpl.java, identity/model/Practitioner.java (first_name, last_name, display_name, profession, professional_number, address_street, address_postal_code, address_city, phone, email), la façon dont le changement de mot de passe vérifie le mot de passe courant (identity/auth, PasswordEncoder), identity/api/SuspendedAccountAuthenticator (forme d'une façade d'authentification), ApiBoundaryTests, ReservesConformanceIT.

Objectif :
- PractitionerFacade.issuerOf(UUID id) → Optional<IssuerView> : record (id, firstName, lastName, displayName, profession, professionalNumber, addressStreet, addressPostalCode, addressCity, phone, email). Lecture seule.
- identity.api.PasswordCheck (ou une méthode de façade nommée dans le style du module) : boolean matches(UUID practitionerId, CharSequence password), sans effet de bord sur les compteurs de verrouillage de la connexion, sans journaliser le mot de passe ; un praticien inconnu ou sans mot de passe répond false.
- Aucune route, aucun changement de contrat, aucune migration.

Périmètre : src/main/java/com/psymate/identity/api/**, l'implémentation dans identity/auth/service, leurs tests. Ne touche pas à identity/preferences (tâche M14-02 en cours).

Critères d'acceptation :
- issuerOf renvoie les champs du praticien, vide pour un identifiant inconnu ;
- matches répond true pour le bon mot de passe, false sinon, et ne change ni l'état du compte ni les compteurs de tentative ;
- tests d'architecture et ReservesConformanceIT verts (marque @Reserved « M15-06, G Q85 » si c'est la règle) ;
- contract/openapi.yaml inchangé ; ./mvnw verify vert.

Tests : IT de la façade (issuerOf), IT ou unitaire de la vérification du mot de passe.

Interdits : exposer le hachage du mot de passe ; toucher à identity/preferences, à un contrôleur ou à un autre module ; ajouter une route.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-03 Identity : identité d'émetteur et vérification du mot de passe », puis make log TASK=M15-03 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-04 — Identité de facturation d'un patient

```text
Tâche M15-04 — Exposer aux autres modules ce que la facturation doit savoir d'un patient (dépôt psymate-api, module patients : package api et PatientService seulement). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, ../psymate-web/docs/L-plan-facturation.md (L2.2 Q85, Q87 ; L6.3), docs/G-decisions.md Q56 (fee et prescriber ne sont jamais exposés). Code : patients/api/PatientFacade.java, PatientRef, FollowUpView, patients/service/PatientService.java (il implémente la façade), patients/model/Patient.java, PatientOwnershipGuard, ApiBoundaryTests, ReservesConformanceIT.

Objectif :
- PatientFacade.billingIdentity(UUID patientId) → Optional<PatientBillingView> : record (id, ownerId, displayName, firstName, lastName, email, archived). Vide pour un patient inconnu.
- PatientFacade.billingIdentities(Collection<UUID>) → Map<UUID, PatientBillingView>, en une requête.
- Rien d'autre : ni fee, ni prescriber, ni contexte, ni téléphone.
- Aucune route, aucun changement de contrat, aucune migration.

Périmètre : src/main/java/com/psymate/patients/api/**, patients/service/PatientService.java, patients/repository si une requête manque, leurs tests.

Critères d'acceptation :
- les deux méthodes renvoient les sept champs, archived compris, pour un patient actif et un patient archivé ;
- la méthode par lot fait une seule requête pour n identifiants et ignore les inconnus ;
- tests d'architecture et ReservesConformanceIT verts (marque @Reserved « M15-07, G Q85 » si c'est la règle) ;
- contract/openapi.yaml inchangé ; ./mvnw verify vert.

Tests : IT de la façade (actif, archivé, inconnu, lot).

Interdits : exposer follow_up ou une donnée clinique ; toucher aux fichiers PatientProfile*, à un contrôleur ou à un autre module.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-04 Patients : identité de facturation », puis make log TASK=M15-04 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-05 — Module `billing` : socle, prestations, profil de facturation

```text
Tâche M15-05 — Créer le module billing, sa migration, les prestations et le profil de facturation d'un patient (dépôt psymate-api, nouveau module com.psymate.billing). Utilise l'agent backend-module-dev.

Contexte : lis CLAUDE.md, ../psymate-web/docs/L-plan-facturation.md (L2.2 Q83, Q84, Q87 ; L4 en entier ; L5.1 ; L6.3), docs/E-modele-donnees.md (E4 : règles des migrations ; E9), docs/G-decisions.md Q32 à Q38 (structure par rôle, JPA, un fichier par DTO). Code : le module agenda comme modèle récent (package-info.java, AppointmentController, AgendaExceptionHandler, AppointmentForbiddenFieldsAdvice, AppointmentMapper, AppointmentRepository, AppointmentService) ; patients/api/PatientOwnershipGuard ; identity/api/CurrentPractitioner ; les tests ModularityTests (EXPECTED_MODULES, PENDING_MODULES), ArchitectureTests (JPA_PACKAGES), RoleFolderTests (ROLE_FOLDER_MODULES), TableOwnershipTests, FlywayMigrationIT, src/test/resources/expected-schema-v01.yaml, tools/it-slices.txt et tools/verify-fast.sh --check ; la PR #103 (M14-04) pour voir ce qu'une migration fait bouger.

Objectif :
- Module billing : package-info.java avec allowedDependencies = identity :: api, patients :: api, clinical :: api, events :: api, shared (les dépendances pas encore employées vont dans PENDING_MODULES ou ce que la règle demande). Dossiers par rôle. Aucun package api (personne ne dépend de billing).
- V18__billing.sql : le DDL de L4, identique octet pour octet, les dix tables d'un coup. expected-schema-v01.yaml, TableOwnershipTests, FlywayMigrationIT et les listes d'architecture mis à jour ; les *IT de billing rejoignent la tranche core de tools/it-slices.txt.
- Entités et dépôts JPA pour billing_service et billing_patient seulement (les autres tables viennent avec leurs tâches).
- Routes de L5.1 : GET et POST /api/me/billing-services, PATCH et DELETE /api/me/billing-services/{id}, GET et PUT /api/patients/{id}/billing-profile. Règles : montants en centimes entiers ; première prestation créée = défaut ; un seul défaut ; 50 prestations au plus ; DELETE archive ; 409 SERVICE_IS_DEFAULT ; propriété du patient par PatientOwnershipGuard ; 409 PATIENT_ARCHIVED en écriture ; champs inconnus refusés (UNKNOWN_FIELD) ; 422 par champ avec les codes de L5.1.
- Contrat regénéré (make contract) ; version selon L6.3 règle 2. messages_fr.properties pour les textes d'erreur.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/db/migration/V18__billing.sql, messages_*.properties (clés billing.*), les tests de billing, les listes des tests d'architecture, expected-schema-v01.yaml, tools/it-slices.txt, contract/openapi.yaml, docs/generated/** (regénérés). Aucun autre module.

Critères d'acceptation :
- la base migrée contient les dix tables de L4 avec leurs contraintes ; FlywayMigrationIT et TableOwnershipTests verts ;
- ModularityTests reconnaît billing ; aucune dépendance interdite ; tools/verify-fast.sh --check vert ;
- prestations : création, liste (défaut d'abord), modification, changement de défaut, archivage, limite de 50, 409 SERVICE_IS_DEFAULT, isolement entre praticiens (404 pour la prestation d'un autre) ;
- profil de facturation : lecture d'un profil vide, écriture, effacement par null, 404 pour le patient d'un autre, 409 pour un patient archivé ;
- un prix à zéro, négatif ou non entier est refusé (422) ; aucun montant en décimal dans le contrat ;
- ./mvnw verify vert.

Tests : *IT Testcontainers pour chaque route (succès, 404, 409, 422), test unitaire des règles de défaut.

Interdits : toucher à un autre module ; écrire les entités des factures, des paiements ou des séances à facturer ; une dépendance Maven ; modifier une migration existante ; journaliser un montant ou un nom.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-05 Billing : socle, prestations, profil de facturation », puis make log TASK=M15-05 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-06 — Réglages de facturation, IBAN

```text
Tâche M15-06 — Réglages de facturation et IBAN (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-03 et M15-05, fusionnées.

Contexte : lis CLAUDE.md, docs/L-plan-facturation-api.md (Q85, Q89, Q92, Q95 ; L4 billing_settings ; L5.1). Code : billing (M15-05), identity/api (PractitionerFacade.issuerOf, vérification du mot de passe, EncryptedStorageFacade, CurrentPractitioner).

Objectif : GET et PUT /api/me/billing-settings, PUT et DELETE /api/me/billing-settings/iban, exactement comme L5.1. Bloc identity en lecture seule (nom, titre tiré de la profession, numéro professionnel) ; valeurs par défaut sans ligne en base ; adresse et e-mail proposés depuis identity tant qu'ils ne sont pas enregistrés ; mention de TVA tirée de messages_fr selon le régime ; numbering.next* calculés depuis billing_counter et l'année en cours, locked dès qu'un numéro existe ; missing calculé (siret, addressLines, service). SIRET : 14 chiffres et clé de Luhn. IBAN : format et clé mod 97, rangé chiffré (clé billing/iban/{practitionerId}), quatre derniers caractères en base, jamais renvoyé ; mot de passe exigé pour l'écrire et l'effacer.

Critères d'acceptation : chaque code d'erreur de L5.1 a un IT ; l'IBAN n'apparaît dans aucune réponse, aucune table, aucun journal ; un préfixe ne change plus après une émission simulée par une ligne de billing_counter ; isolement entre praticiens ; ./mvnw verify vert ; contrat regénéré.

Interdits : toucher à un autre module ; stocker l'IBAN en clair ; un champ libre pour la mention de TVA.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : *IT pour chaque route et chaque code d'erreur ; tests unitaires de la clé de Luhn du SIRET, de la clé mod 97 de l'IBAN et du calcul de missing.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-06 Billing : réglages et IBAN », puis make log TASK=M15-06 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-07 — Séances à facturer

```text
Tâche M15-07 — Séances à facturer (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-02, M15-04 et M15-06, fusionnées.

Contexte : lis docs/L-plan-facturation-api.md (Q86, Q87 ; L3.1 ; L4 billable_session ; L5.2). Code : clinical/api (SessionClosed, SessionFacade, EntryArchived), patients/api (billingIdentities), events/api (EventDeduplicator), un écouteur existant (directory/service/PractitionerStatusListener).

Objectif : écouteurs de SessionClosed (kind session seulement → pending, idempotent) et de EntryArchived (séance pending → dismissed) ; création à la demande par SessionFacade.closedSession ; les quatre routes de L5.2 ; calcul du tarif et de rateSource selon Q87 ; regroupement par patient, noms par billingIdentities.

Critères d'acceptation : une séance close apparaît dans la liste, une séance d'accueil ou de contact jamais ; rejouer l'événement ne crée pas de doublon ; dismiss puis restore ; dismiss juste après la clôture réussit même si l'événement n'est pas encore traité ; rateSource vaut session, patient, service ou none dans les quatre cas ; isolement entre praticiens ; ./mvnw verify vert ; contrat regénéré.

Interdits : lire une table de clinical ou de patients ; reprendre les séances closes avant la mise en service ; créer une ligne pour un rendez-vous.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : IT des deux écouteurs (dont le rejeu d'un événement), IT de chaque route, test unitaire du calcul du tarif (quatre sources).

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-07 Billing : séances à facturer », puis make log TASK=M15-07 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-08 — Factures : brouillons, liste

```text
Tâche M15-08 — Brouillons de facture et liste (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-07.

Contexte : lis docs/L-plan-facturation-api.md (Q84, Q87, Q88 ; L3 ; L4 invoice, invoice_line, invoice_event ; L5.3 jusqu'à DELETE).

Objectif : POST /api/invoices, POST /api/invoices/batch, GET /api/invoices (filtres, curseur, statut calculé en SQL par un fragment *NativeQueries), GET, PATCH, DELETE /api/invoices/{id}. Calculs de L3.2 ; nextNumberPreview ; bloc issuer d'un brouillon = réglages du moment ; événement d'historique draft_created ; séances passées à invoiced, rendues à pending quand une ligne part ou que le brouillon est supprimé.

Critères d'acceptation : chaque règle et chaque code de L5.3 (jusqu'à DELETE) a un IT ; le total d'un brouillon est toujours celui de L3.2, en centimes ; le lot est tout ou rien ; l'ordre de la liste est celui de L5.3 ; filtrer par chaque statut ; isolement entre praticiens ; ./mvnw verify vert ; contrat regénéré.

Interdits : émettre, numéroter, produire un PDF (M15-09) ; supprimer autre chose qu'un brouillon.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : tests unitaires des calculs de L3.2 (lignes, remise, TVA des trois régimes, arrondi), IT de chaque route et de chaque code, IT du lot tout ou rien, IT d'isolement entre praticiens.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-08 Billing : brouillons et liste », puis make log TASK=M15-08 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-09 — Émission, numérotation, PDF

```text
Tâche M15-09 — Émettre une facture, la numéroter, produire son PDF (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-08.

Contexte : lis docs/L-plan-facturation-api.md (Q88, Q89, Q92, Q93 ; L3.3 ; L5.3 de issue à duplicate), clinical/export/service/PdfRenderer et HtmlRenderer (emploi d'openhtmltopdf, polices ; à lire, pas à modifier), les maquettes de la feuille dans ../psymate-web/docs/design/facturation/ (maquette.js, fonction paper ; facturation.css, classes .paper).

Objectif : POST /api/invoices/{id}/issue (verrou du compteur, expectedNumber, instantané issuer et recipient, date, échéance, alreadyPaid qui crée le paiement dans la même transaction), GET /api/invoices/by-number/{number}, GET /api/invoices/{id}/pdf (brouillon avec filigrane, émis depuis le fichier rangé chiffré, variante receipted), POST /api/invoices/{id}/duplicate. Moteur de rendu propre à billing, mise en page de la maquette (émetteur, titre et numéro, destinataire, dates, lignes, totaux, mention de TVA, modalités, pied), en clair, A4. Aucune modification d'une facture émise, par aucune route.

Critères d'acceptation : deux émissions concurrentes ne produisent ni doublon ni trou (IT avec deux transactions) ; 409 INVOICE_NUMBER_CHANGED porte le bon numéro ; la suite repart à 0001 au changement d'année de la date d'émission ; PATCH et DELETE d'une facture émise répondent 409 ; le PDF rangé ne change pas quand les réglages changent ensuite ; le PDF contient le numéro, le total, la mention du régime, et aucune autre donnée du dossier que les dates et désignations (IT qui extrait le texte) ; ./mvnw verify vert ; contrat regénéré.

Interdits : toucher à clinical.export ; une dépendance Maven ; un PDF rangé en clair.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : IT de concurrence sur le compteur, IT de chaque route et de chaque code, IT d'immuabilité (PATCH et DELETE refusés), IT du PDF (texte extrait par PDFBox, déjà présent avec openhtmltopdf), test unitaire du changement d'année.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-09 Billing : émission, numérotation, PDF », puis make log TASK=M15-09 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-10 — Paiements, journal, export, chiffres

```text
Tâche M15-10 — Paiements, journal des encaissements, export et chiffres (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-09.

Contexte : lis docs/L-plan-facturation-api.md (Q90 ; L3.2 ; L4 payment ; L5.4).

Objectif : les sept routes de L5.4. paid_cents tenu dans la transaction ; statut et overdue recalculés ; totaux de période en SQL ; reçu en PDF à la demande ; export CSV (séparateur point-virgule, UTF-8 avec BOM, montants en euros à virgule) et PDF, sans nom par défaut ; summary et patients/{id}/billing.

Critères d'acceptation : chaque code de L5.4 a un IT ; un paiement partiel puis le solde donnent partial puis paid ; annuler un paiement rend le reste dû et garde la ligne ; les totaux d'une période de plusieurs pages sont ceux de toute la période ; l'export par défaut ne contient aucun nom ; overdue bascule le lendemain de l'échéance, au jour de Europe/Paris (horloge injectée) ; ./mvnw verify vert ; contrat regénéré.

Interdits : supprimer un paiement ; modifier un montant enregistré.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : IT de chaque route et de chaque code, IT des totaux sur plusieurs pages, IT de l'export (contenu du CSV, avec et sans nom), tests unitaires de overdue avec une horloge injectée.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-10 Billing : paiements, journal, chiffres », puis make log TASK=M15-10 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-11 — Avoirs, envoi par e-mail

```text
Tâche M15-11 — Avoirs et envoi d'une facture par e-mail (dépôt psymate-api, module billing). Utilise l'agent backend-module-dev. Dépend de M15-10.

Contexte : lis docs/L-plan-facturation-api.md (Q91, Q94 ; L5.3 credit-notes et send ; L5.4 remboursement), directory/service/ContactMailer (forme d'un mailer, Reply-To), shared/ratelimit, testsupport/SharedMailpit et MailpitClient.

Objectif : POST /api/invoices/{id}/credit-notes (portée full ou lines, numérotation de la suite des avoirs, PDF à l'émission, credited_cents, refund_due_cents, facture void si total, séances rendues à pending), remboursement par POST /api/invoices/{creditNoteId}/payments ; POST /api/invoices/{id}/send (InvoiceMailer, modèles des réglages avec les variables {numéro}, {montant}, {reste dû}, {échéance}, PDF joint, plafond de 30 par heure et par praticien, événement d'historique sent).

Critères d'acceptation : avoir partiel, avoir total, avoir supérieur au reste dû (refundDue), ligne déjà créditée (409) ; la facture d'origine n'est jamais modifiée hors de ses compteurs ; le message reçu dans Mailpit a le bon destinataire, le Reply-To du praticien, le PDF joint et aucun autre contenu que le texte envoyé ; le 31e envoi de l'heure répond 429 ; aucune adresse dans les journaux ; ./mvnw verify vert ; contrat regénéré.

Interdits : un brouillon d'avoir ; un e-mail automatique ; un appel externe autre que le SMTP configuré.

Périmètre : src/main/java/com/psymate/billing/**, src/main/resources/messages_*.properties (clés billing.*), les tests de billing, contract/openapi.yaml et docs/generated/** (regénérés par make contract). Aucun autre module, aucune migration (si une colonne manque à V18, arrête-toi et dis-le : une migration de plus se réserve dans E4).

Tests : IT des quatre cas d'avoir, IT du remboursement, IT de l'envoi lu dans Mailpit (destinataire, Reply-To, pièce jointe), IT du plafond d'envoi.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-11 Billing : avoirs et envoi par e-mail », puis make log TASK=M15-11 AGENT=backend-module-dev STATUS=done SUMMARY="…" PR=<url>.
```

### M15-12 — Seed, tag de contrat, suivi

```text
Tâche M15-12 — Seed de la facturation, tag du contrat, suivi (dépôt psymate-api, tools/seed et racine). Utilise l'agent seed-perf. Dépend de M15-11.

Contexte : lis docs/L-plan-facturation-api.md (L5, L6.3 règle 2), tools/seed/README.md, AgendaScenario et AgendaSeeder (modèle), tools/tag-contract.sh.

Objectif : BillingScenario et BillingSeeder, par l'API seulement : pour chaque praticien de démonstration, réglages complets (SIRET factice valide, régime health_exempt), trois prestations, tarif pour une partie des patients, puis, à partir des séances closes du seed : des séances laissées à facturer, deux brouillons, des factures à régler, payée en partie, payées par chaque mode, une en retard, un avoir partiel, une facture annulée par un avoir total. Idempotent. Vérification dans Verifier. Version du contrat passée à 0.17.0 si elle ne l'est pas (L6.3 règle 2), tag contract-v0.17.0 posé par tools/tag-contract.sh et poussé.

Critères d'acceptation : make seed SIZE=small puis une seconde fois ne change rien ; GET /api/billing/summary du praticien de démonstration renvoie des chiffres non nuls dans chaque case ; chaque statut de L3.2 a au moins une facture ; le tag existe et info.version vaut 0.17.0 ; ./mvnw verify vert.

Périmètre : tools/seed/**, src/main/resources/application.yml (app.contract.version seulement), contract/openapi.yaml et ContractIT (regénérés), src/test/**/perf si un scénario de performance est ajouté. Aucun code de module.

Tests : tests unitaires de BillingScenario (déterministe, chaque statut présent), Verifier étendu.

Règles d'exécution : L6.3 en entier (lecture du plan, version du contrat, verrou de verify, début et fin de tâche, fusion).

Livrable : PR « M15-12 Facturation : seed et tag de contrat », puis make log TASK=M15-12 AGENT=seed-perf STATUS=done SUMMARY="…" PR=<url>. Le tag se pose après la fusion, sur le commit de main.
```

## L9. Revue (partie API)

API (à coller après chaque tâche M15-02 à M15-12) :

```text
Lance le sous-agent reviewer sur cette branche pour la tâche <ID>. En plus de sa liste habituelle, vérifie : (1) que billing n'importe que identity.api, patients.api, clinical.api, events.api et shared, et qu'aucun module n'importe billing ; (2) que chaque critère de L7 <ID> a un test nommé ; (3) qu'aucun montant n'est un décimal, dans le code comme dans le contrat ; (4) qu'aucune route ne modifie ni ne supprime une facture émise, un avoir ou un paiement (hors cancelled_at) ; (5) qu'aucun nom de patient, montant, IBAN ou adresse e-mail n'est journalisé, et que l'IBAN n'est ni en base ni dans une réponse ; (6) qu'aucun texte clinique n'entre dans billing ; (7) que chaque lecture et écriture est filtrée par praticien, avec un test d'isolement ; (8) que la version du contrat suit L6.3 règle 2 et que contract/openapi.yaml correspond à L5. Corrige ce qu'il marque bloquant, relance-le, puis make log.
```

## L10. Ce qui reste ouvert (partie API)

Proposé par ce plan et **non relu par l'utilisateur** (le jalon a été lancé à sa demande le 28 novembre 2026, sans relecture) : les décisions Q83 à Q95, en particulier les centimes entiers (Q84), le tarif du patient rangé dans `billing` et non dans la fiche (Q87), le PDF figé à l'émission (Q93), le plafond de 30 e-mails par heure (Q94), le mot de passe exigé pour l'IBAN (Q95) ; la règle de version du contrat (L6.3).

À trancher par l'utilisateur :

- **Reprise de l'existant.** Les séances closes avant la mise en service n'entrent pas dans « À facturer ». Une reprise (par exemple les séances du mois en cours) demanderait une façade de lecture de plus dans `clinical`.
- **Ancien champ « Tarif » de la fiche patient** (`follow_up.fee`, texte libre) : M15-21 l'affiche en aide et ne le convertit pas. Le supprimer de la base est une autre décision.
- **Ligne « Facturation » de l'agenda** : hors M15 tant qu'un rendez-vous n'est pas relié à sa séance.
- **Nom d'expéditeur des e-mails** : même question que K10.

À faire valider par un expert-comptable ou un juriste (aucune tâche ne le tranche) :

- **Mentions obligatoires** d'une facture et d'un avoir pour un psychologue et pour un coach, textes des deux mentions de TVA, facture acquittée, numérotation par année.
- **Durée de conservation** des factures et des paiements, et ce que devient ce module à la suppression d'un compte.
- **E-mail au patient** : base légale, mention d'information ; hébergement de données de santé (une facture de psychologue nominative en est une).

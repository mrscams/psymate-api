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

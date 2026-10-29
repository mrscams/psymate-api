-- V01 : socle (M0-03). Copié de docs/E-modele-donnees.md E3.1, E3.2, E3.4, E3.5 (structures), E3.8, E3.11.
-- Le rôle psymate_app est créé par le script d'init du conteneur (G Q9), jamais ici.

-- E3.1 Socle
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- index GiST (patient_id, occurred_range)

-- unaccent() est STABLE, donc interdit dans un index ou une colonne générée.
-- Enveloppe IMMUTABLE classique : à utiliser partout à la place de unaccent().
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS
$$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE TABLE tenant (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- E3.2 Identité
CREATE TABLE practitioner (
  id                    uuid PRIMARY KEY,
  tenant_id             uuid NOT NULL REFERENCES tenant(id),
  email                 text NOT NULL,
  email_normalized      text NOT NULL,
  email_confirmed_at    timestamptz,
  password_hash         text NOT NULL,
  first_name            text NOT NULL,
  last_name             text NOT NULL,
  display_name          text NOT NULL,                 -- « Dr Maya Kaddour », « Claire Lambert »
  locale                text NOT NULL DEFAULT 'fr' CHECK (locale IN ('fr','en')),
  profession            text NOT NULL CHECK (profession IN ('psychologist','psychiatrist','coach')),
  role                  text NOT NULL DEFAULT 'practitioner' CHECK (role IN ('practitioner','admin')),
  -- A10.2 : coordonnées professionnelles (inscription enrichie)
  phone                 text,
  professional_number   text,                          -- ADELI / RPPS, facultatif
  address_street        text,
  address_postal_code   text,
  address_city          text,
  remote_only           boolean NOT NULL DEFAULT false,
  -- A10.3 : machine d'états de la vérification (étendue, décision E-3)
  verification_status   text NOT NULL DEFAULT 'email_pending'
                        CHECK (verification_status IN
                          ('email_pending','docs_pending','under_review','approved',
                           'needs_more','rejected','suspended','closed')),
  verified_at           timestamptz,
  verified_by           uuid REFERENCES practitioner(id),
  suspended_at          timestamptz,
  purge_after           timestamptz,                   -- compte refusé/clos : date de purge (A10, fiche 27)
  -- A10.2 : préférences (ADR-11), clés plates listées en fiche 14
  preferences           jsonb NOT NULL DEFAULT '{}'::jsonb,
  -- Sécurité (F33)
  totp_secret_enc       bytea,                         -- NULL = 2FA inactive
  totp_enabled_at       timestamptz,
  failed_logins         smallint NOT NULL DEFAULT 0,
  locked_until          timestamptz,
  last_login_at         timestamptz,
  created_at            timestamptz NOT NULL DEFAULT now(),
  updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_practitioner_admin_totp CHECK (role <> 'admin' OR totp_secret_enc IS NOT NULL OR totp_enabled_at IS NULL)
);
CREATE UNIQUE INDEX ux_practitioner_email ON practitioner(email_normalized);
CREATE INDEX ix_practitioner_tenant ON practitioner(tenant_id);
CREATE INDEX ix_practitioner_verification ON practitioner(verification_status, created_at);

-- Jetons à usage unique : confirmation d'e-mail (fiche 25), réinitialisation (fiche 26). Décision E-4.
CREATE TABLE auth_token (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  kind             text NOT NULL CHECK (kind IN ('email_confirm','password_reset','email_change')),
  token_hash       text NOT NULL,                      -- SHA-256 du jeton ; le jeton clair ne vit que dans l'e-mail
  payload          jsonb,                              -- email_change : { "newEmail": "…" }
  expires_at       timestamptz NOT NULL,               -- 24 h (e-mail), 30 min (mot de passe)
  consumed_at      timestamptz,
  created_ip       inet,
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_auth_token_hash ON auth_token(token_hash);
CREATE INDEX ix_auth_token_active ON auth_token(practitioner_id, kind) WHERE consumed_at IS NULL;

-- Codes de secours 2FA : 10 par activation, à usage unique (fiche 11, UC-19).
CREATE TABLE totp_recovery_code (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  code_hash        text NOT NULL,
  used_at          timestamptz,
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_totp_recovery_practitioner ON totp_recovery_code(practitioner_id) WHERE used_at IS NULL;

-- Sessions côté serveur (Spring Session JDBC) : indispensable pour révoquer les sessions
-- d'un compte suspendu ou après « déconnecter les autres appareils ». Décision E-5.
-- Schéma standard de Spring Session ; vérifier qu'il correspond à la version du pom.
CREATE TABLE spring_session (
  primary_id            char(36) NOT NULL,
  session_id            char(36) NOT NULL,
  creation_time         bigint NOT NULL,
  last_access_time      bigint NOT NULL,
  max_inactive_interval int NOT NULL,
  expiry_time           bigint NOT NULL,
  principal_name        varchar(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
  session_primary_id  char(36) NOT NULL,
  attribute_name      varchar(200) NOT NULL,
  attribute_bytes     bytea NOT NULL,
  CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
  CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
    REFERENCES spring_session(primary_id) ON DELETE CASCADE
);

-- Journal de sécurité et d'administration (A10.2, fiches 11, 21, 22). Append-only. Seul journal admin (G Q29).
CREATE TABLE security_event (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid REFERENCES practitioner(id),  -- compte concerné ; NULL = événement système
  actor_id         uuid REFERENCES practitioner(id),  -- qui a agi ; NULL = le compte lui-même ou le système
  kind             text NOT NULL,                      -- login_ok, login_failed, locked, 2fa_enabled, 2fa_disabled,
                                                       -- password_reset_requested, password_changed, email_confirmed,
                                                       -- session_revoked, suspended, reactivated, export_requested, purge,
                                                       -- patient.read, patient.write (M7-02), admin.* (M1-14, M1-14b)…
  ip               inet,
  user_agent       text,
  details          jsonb,
  occurred_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_security_event_practitioner ON security_event(practitioner_id, occurred_at DESC);
CREATE INDEX ix_security_event_kind ON security_event(kind, occurred_at DESC);

-- E3.4 Patients
CREATE TABLE patient (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  owner_id      uuid NOT NULL REFERENCES practitioner(id),
  display_name  text NOT NULL,                         -- seul champ obligatoire (UC-02)
  first_name    text,
  last_name     text,
  birth_date    date,
  pronoun       text,
  phone         text,
  email         text,
  context       jsonb NOT NULL DEFAULT '{}'::jsonb,    -- { situation, activity, referredBy, initialReason, history } (fiche 16)
  follow_up     jsonb NOT NULL DEFAULT '{}'::jsonb,    -- { since, rhythm, modality, durationMin, fee, prescriber }
  current_version int NOT NULL DEFAULT 1,
  clinical_rev  bigint NOT NULL DEFAULT 0,             -- incrémenté par l'application à chaque écriture clinique (G Q30) ; comparé à patient_overview.source_rev (E-8)
  archived_at   timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_patient_owner ON patient(tenant_id, owner_id) WHERE archived_at IS NULL;
CREATE INDEX ix_patient_name_trgm ON patient USING gin (f_unaccent(display_name) gin_trgm_ops);

-- Historique de la fiche patient (fiche 16 : « 3 versions »). Décision E-9, confirmée G Q7.
CREATE TABLE patient_version (
  id          uuid PRIMARY KEY,
  patient_id  uuid NOT NULL REFERENCES patient(id),
  version     int NOT NULL,
  snapshot    jsonb NOT NULL,                          -- copie des colonnes éditables au moment de la version
  reason      text,
  author_id   uuid NOT NULL REFERENCES practitioner(id),
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_patient_version ON patient_version(patient_id, version);

-- Drapeaux posés à la main, jamais déduits (fiche 16, B1 P7). Décision E-10 (confirmée G Q7) : table plutôt que text[],
-- parce que « à surveiller » exige une raison et qu'on veut savoir qui l'a posé et quand il a été levé.
CREATE TABLE patient_flag (
  id          uuid PRIMARY KEY,
  patient_id  uuid NOT NULL REFERENCES patient(id),
  kind        text NOT NULL CHECK (kind IN ('watch','risk','shared_care')),
  reason      text,                                    -- obligatoire pour watch (contrainte applicative)
  set_by      uuid NOT NULL REFERENCES practitioner(id),
  set_at      timestamptz NOT NULL DEFAULT now(),
  cleared_by  uuid REFERENCES practitioner(id),
  cleared_at  timestamptz
);
CREATE UNIQUE INDEX ux_patient_flag_active ON patient_flag(patient_id, kind) WHERE cleared_at IS NULL;

-- Partage V2 (ADR-09) : vide en V1. [Réservé]
CREATE TABLE patient_access (
  id               uuid PRIMARY KEY,
  tenant_id        uuid NOT NULL REFERENCES tenant(id),
  patient_id       uuid NOT NULL REFERENCES patient(id),
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  role             text NOT NULL CHECK (role IN ('owner','collaborator','reader')),
  granted_by       uuid NOT NULL REFERENCES practitioner(id),
  granted_at       timestamptz NOT NULL DEFAULT now(),
  revoked_at       timestamptz
);
CREATE UNIQUE INDEX ux_patient_access_active ON patient_access(patient_id, practitioner_id) WHERE revoked_at IS NULL;

-- E3.5 Vocabulaires (structures seules ; seed en V02 ; emotion_merge, tag, patient_tag en V02)
-- Six types d'entrées V1. Un nouveau type = une ligne ici + une table de détail + une migration (ADR-02).
CREATE TABLE entry_type (
  code          text PRIMARY KEY,                      -- note, event, situation, feeling, session, medication
  label_fr      text NOT NULL,
  label_en      text NOT NULL,
  detail_table  text,                                  -- entry_feeling, entry_medication, entry_session ; NULL si aucune
  has_duration  boolean NOT NULL DEFAULT false,        -- situation, medication, session
  sort_order    smallint NOT NULL,
  active        boolean NOT NULL DEFAULT true
);

-- Émotions : globales (produit) et personnalisées (praticien), A4.5.
CREATE TABLE emotion (
  code                text PRIMARY KEY,                -- 'anxiety' ; custom : 'c_' || 8 hex
  scope               text NOT NULL CHECK (scope IN ('global','custom')),
  tenant_id           uuid REFERENCES tenant(id),      -- NULL si global
  practitioner_id     uuid REFERENCES practitioner(id),-- NULL si global, obligatoire si custom
  family              text NOT NULL CHECK (family IN
                        ('joy','trust','fear','surprise','sadness','disgust','anger','anticipation')),
  intensity_level     smallint CHECK (intensity_level BETWEEN 1 AND 3),   -- Plutchik : 1 faible, 2 moyen, 3 fort ; NULL pour les ajouts cliniques et les custom
  valence             smallint CHECK (valence BETWEEN -2 AND 2),          -- NULL en V1, rempli avant la V2
  arousal             smallint CHECK (arousal BETWEEN 0 AND 4),           -- idem
  label_fr            text NOT NULL,
  label_en            text,
  label_normalized    text NOT NULL,                   -- lower(unaccent(label_fr)), pour l'unicité et le refus de tags homonymes
  vocabulary_version  int NOT NULL DEFAULT 1,
  active              boolean NOT NULL DEFAULT true,
  promoted_to         text REFERENCES emotion(code),   -- custom fusionnée dans une globale (V2)
  created_by          uuid REFERENCES practitioner(id),
  created_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_emotion_custom_owner CHECK (
    (scope = 'global' AND practitioner_id IS NULL) OR (scope = 'custom' AND practitioner_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_emotion_custom_label ON emotion(practitioner_id, label_normalized) WHERE scope = 'custom';
CREATE UNIQUE INDEX ux_emotion_global_label ON emotion(label_normalized) WHERE scope = 'global';
CREATE INDEX ix_emotion_practitioner ON emotion(practitioner_id) WHERE scope = 'custom' AND active;

-- Types de liens (A4.3). La matrice source/cible est dans les colonnes text[] ; seed en E5.3.
CREATE TABLE link_type (
  code                  text PRIMARY KEY,              -- triggers, caused_by, worsens, relieves, co_occurs, follows, part_of, treats
  directed              boolean NOT NULL DEFAULT true,
  label_fr              text NOT NULL,                 -- « déclenche »
  label_en              text NOT NULL,
  inverse_label_fr      text,                          -- « déclenché par » (affichage dans l'autre sens) ; NULL si non orienté
  inverse_label_en      text,
  allowed_source_types  text[] NOT NULL,               -- codes d'entry_type
  allowed_target_types  text[] NOT NULL,
  line_style            text NOT NULL DEFAULT 'solid', -- solid, long_dash, short_dash, dotted, thin, double (étude couleurs §4)
  sort_order            smallint NOT NULL,
  active                boolean NOT NULL DEFAULT true
);


-- E3.8 Événements : schéma Spring Modulith 2.1.2 (schemas/v2/schema-postgresql.sql), identique à E3.8
CREATE TABLE event_publication (
  id                uuid NOT NULL,
  listener_id       text NOT NULL,
  event_type        text NOT NULL,
  serialized_event  text NOT NULL,
  publication_date  timestamptz NOT NULL,
  completion_date   timestamptz,
  status            text,
  completion_attempts int,
  last_resubmission_date timestamptz,
  PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- E3.11 Réservé compagnon patient (A11), vide en V1. Les deux ALTER TABLE viennent en V05.
CREATE TABLE patient_device (
  id                  uuid PRIMARY KEY,
  tenant_id           uuid NOT NULL REFERENCES tenant(id),
  patient_id          uuid NOT NULL REFERENCES patient(id),
  platform            text CHECK (platform IN ('ios','android')),
  pairing_code_hash   text,                            -- code court à usage unique, haché ; NULL une fois consommé
  pairing_expires_at  timestamptz,
  token_hash          text,                            -- jeton d'appareil, haché
  push_token_enc      bytea,
  paired_at           timestamptz,
  last_seen_at        timestamptz,
  revoked_at          timestamptz,
  created_by          uuid NOT NULL REFERENCES practitioner(id),
  created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_patient_device_patient ON patient_device(patient_id) WHERE revoked_at IS NULL;

CREATE TABLE patient_submission (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  patient_id    uuid NOT NULL REFERENCES patient(id),
  device_id     uuid NOT NULL REFERENCES patient_device(id),
  submitted_at  timestamptz NOT NULL DEFAULT now(),
  period_from   timestamptz NOT NULL,
  period_to     timestamptz NOT NULL,
  item_count    int NOT NULL,
  status        text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','attached','dismissed')),
  session_id    uuid,                                  -- FK vers entry(id) posée dans la migration clinical
  attached_at   timestamptz,
  attached_by   uuid REFERENCES practitioner(id)
);
CREATE INDEX ix_submission_patient_pending ON patient_submission(patient_id, submitted_at DESC) WHERE status = 'pending';

-- Tables append-only (E2, E7 O11) : aucun UPDATE ni DELETE pour le rôle applicatif.
REVOKE UPDATE, DELETE ON security_event, patient_version FROM psymate_app;

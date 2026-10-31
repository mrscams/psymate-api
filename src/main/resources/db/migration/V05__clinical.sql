-- V05 clinical (M1-03) : E3.6 dossier clinique, E3.11 deux ALTER, E3.12 entry_embedding et entry_chunk.
-- export_job (E3.6) est livré par V11 (M6-05) ; ai_feedback (E3.12) par V07 (M2-01).

CREATE TABLE entry (
  id                  uuid PRIMARY KEY,
  tenant_id           uuid NOT NULL REFERENCES tenant(id),
  patient_id          uuid NOT NULL REFERENCES patient(id),
  type                text NOT NULL REFERENCES entry_type(code),
  status              text NOT NULL DEFAULT 'final' CHECK (status IN ('draft','final')),
  visibility          text NOT NULL DEFAULT 'clinical' CHECK (visibility IN ('private','clinical')),
  -- Provenance (A11, réservé compagnon) : toujours 'practitioner' et NULL en V1
  author_type         text NOT NULL DEFAULT 'practitioner' CHECK (author_type IN ('practitioner','patient')),
  submission_id       uuid,                            -- FK posée en E3.11 (patient_submission)
  -- Temps à deux axes (A4.2)
  occurred_from       timestamptz NOT NULL,
  occurred_to         timestamptz,                     -- NULL = en cours (situation, médicament)
  occurred_precision  text NOT NULL DEFAULT 'exact' CHECK (occurred_precision IN
                        ('exact','day','month','year','period','life_phase')),
  occurred_label      text,                            -- libellé d'origine : « été 2019 », « enfance »
  occurred_range      tstzrange GENERATED ALWAYS AS
                        (tstzrange(occurred_from, coalesce(occurred_to, 'infinity'::timestamptz), '[)')) STORED,
  recorded_at         timestamptz NOT NULL DEFAULT now(),
  -- Séance de rattachement (ADR-10) : obligatoire sauf pour la séance elle-même (et une entrée patient non rattachée, A11)
  session_id          uuid REFERENCES entry(id),
  current_version_id  uuid,                            -- FK posée après entry_version
  current_version     int NOT NULL DEFAULT 1,
  link_count          int NOT NULL DEFAULT 0,          -- projection maintenue par linking (A4.4 « link_neighbor_count »)
  search_vector       tsvector,                        -- maintenu par trigger (M4-02)
  archived_at         timestamptz,
  created_by          uuid REFERENCES practitioner(id),
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_entry_session_required CHECK (session_id IS NOT NULL OR type = 'session' OR author_type = 'patient'),
  CONSTRAINT ck_entry_session_not_self CHECK (session_id IS NULL OR session_id <> id),
  CONSTRAINT ck_entry_occurred_order CHECK (occurred_to IS NULL OR occurred_to >= occurred_from)
);
CREATE INDEX ix_entry_timeline      ON entry(patient_id, occurred_from DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_entry_timeline_type ON entry(patient_id, type, occurred_from DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_entry_session       ON entry(session_id) WHERE session_id IS NOT NULL;
CREATE INDEX ix_entry_range         ON entry USING gist (patient_id, occurred_range) WHERE archived_at IS NULL;
CREATE INDEX ix_entry_submission    ON entry(submission_id) WHERE submission_id IS NOT NULL;   -- A11, vide en V1
CREATE INDEX ix_entry_tenant        ON entry(tenant_id);

CREATE TABLE entry_version (
  id          uuid PRIMARY KEY,
  entry_id    uuid NOT NULL REFERENCES entry(id),
  version     int NOT NULL,
  title       text,
  body        text,                                    -- texte libre en clair, jamais dans payload
  payload     jsonb NOT NULL DEFAULT '{}'::jsonb,      -- copie du détail typé au moment de la version
  reason      text,
  author_id   uuid REFERENCES practitioner(id),        -- NULL si version écrite par le patient (A11)
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_entry_version ON entry_version(entry_id, version);
CREATE INDEX ix_entry_version_title_trgm ON entry_version USING gin (title gin_trgm_ops);
CREATE INDEX ix_entry_version_body_trgm  ON entry_version USING gin (body  gin_trgm_ops);

ALTER TABLE entry
  ADD CONSTRAINT fk_entry_current_version FOREIGN KEY (current_version_id)
  REFERENCES entry_version(id) DEFERRABLE INITIALLY DEFERRED;

-- Détail : ressenti (A4.5)
CREATE TABLE entry_feeling (
  entry_id               uuid PRIMARY KEY REFERENCES entry(id),
  emotion_code           text NOT NULL REFERENCES emotion(code),
  original_emotion_code  text REFERENCES emotion(code),-- renseigné après une fusion (fiche 17), sinon NULL
  intensity              smallint NOT NULL CHECK (intensity BETWEEN 0 AND 10),
  valence                smallint CHECK (valence BETWEEN -2 AND 2),   -- copié d'emotion à la saisie, NULL si non défini
  arousal                smallint CHECK (arousal BETWEEN 0 AND 4),
  context_note           text,
  bodily_signs           text[]
);
CREATE INDEX ix_entry_feeling_emotion ON entry_feeling(emotion_code, intensity);

-- Détail : médicament (H9)
CREATE TABLE entry_medication (
  entry_id    uuid PRIMARY KEY REFERENCES entry(id),
  molecule    text NOT NULL,
  dosage      text,                                    -- « 50 mg »
  frequency   text,                                    -- « matin », « 1/j »
  prescriber  text,
  period_from date,
  period_to   date
);

-- Détail : séance (ADR-10, UC-04)
CREATE TABLE entry_session (
  entry_id            uuid PRIMARY KEY REFERENCES entry(id),
  kind                text NOT NULL DEFAULT 'session' CHECK (kind IN ('intake','session','contact')),  -- accueil, séance, contact hors RDV
  modality            text NOT NULL DEFAULT 'in_person' CHECK (modality IN ('in_person','video','phone','email')),
  duration_min        smallint CHECK (duration_min BETWEEN 1 AND 600),   -- défaut applicatif : préférence sessions.defaultDurationMin (60)
  closed_at           timestamptz,                     -- NULL = séance ouverte (« en cours »)
  summary_version_id  uuid REFERENCES entry_version(id)
);
CREATE INDEX ix_entry_session_open ON entry_session(entry_id) WHERE closed_at IS NULL;

-- Tags sur les entrées (A4.6)
CREATE TABLE entry_tag (
  entry_id  uuid NOT NULL REFERENCES entry(id),
  tag_id    uuid NOT NULL REFERENCES tag(id),
  PRIMARY KEY (entry_id, tag_id)
);
CREATE INDEX ix_entry_tag_tag ON entry_tag(tag_id, entry_id);

-- Après la création d'entry : FK du compagnon patient (A11), colonnes vides en V1.
ALTER TABLE entry ADD CONSTRAINT fk_entry_submission FOREIGN KEY (submission_id) REFERENCES patient_submission(id);
ALTER TABLE patient_submission ADD CONSTRAINT fk_submission_session FOREIGN KEY (session_id) REFERENCES entry(id);

-- Réservé V2 (A4.9) : vides en V1.
CREATE TABLE entry_embedding (
  entry_id    uuid NOT NULL REFERENCES entry(id),
  version     int  NOT NULL,
  model       text NOT NULL,
  embedding   vector(1536) NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (entry_id, version, model)
);
-- L'index hnsw (embedding vector_cosine_ops) est créé en V2 seulement.

CREATE TABLE entry_chunk (
  id           uuid PRIMARY KEY,
  entry_id     uuid NOT NULL REFERENCES entry(id),
  version      int  NOT NULL,
  ordinal      smallint NOT NULL,
  text         text NOT NULL,
  token_count  int,
  UNIQUE (entry_id, version, ordinal)
);

-- Table append-only (E4, A4.8) : aucune modification ni suppression d'une version pour le role applicatif.
REVOKE UPDATE, DELETE ON entry_version FROM psymate_app;

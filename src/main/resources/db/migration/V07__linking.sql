-- V07 linking (M2-01) : E3.7 link, E3.12 ai_feedback (réservé V2, vide en V1).
-- Un lien n'est jamais supprimé : sa fin de vie est le statut 'superseded' (ou 'rejected' pour une
-- suggestion IA refusée, V2). La table n'est donc pas append-only : le statut, superseded_by et
-- decided_* se mettent à jour (A7 « Cycle de vie d'un lien »).

CREATE TABLE link (
  id             uuid PRIMARY KEY,
  tenant_id      uuid NOT NULL REFERENCES tenant(id),
  patient_id     uuid NOT NULL REFERENCES patient(id),
  source_id      uuid NOT NULL REFERENCES entry(id),
  target_id      uuid NOT NULL REFERENCES entry(id),
  link_type      text NOT NULL REFERENCES link_type(code),
  directed       boolean NOT NULL DEFAULT true,
  provenance     text NOT NULL CHECK (provenance IN ('manual','ai_suggested','ai_confirmed')),
  status         text NOT NULL CHECK (status IN ('proposed','accepted','rejected','superseded')),
  confidence     real CHECK (confidence BETWEEN 0 AND 1),   -- NULL en V1
  rationale      text,
  evidence       jsonb,                                -- [{entry_id, version, span}] en V2
  author_id      uuid REFERENCES practitioner(id),     -- praticien ou NULL (IA)
  decided_by     uuid REFERENCES practitioner(id),
  decided_at     timestamptz,
  superseded_by  uuid REFERENCES link(id),
  version        int NOT NULL DEFAULT 1,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_link_not_self CHECK (source_id <> target_id)
);
CREATE UNIQUE INDEX ux_link_active ON link(source_id, target_id, link_type) WHERE status IN ('proposed','accepted');
CREATE INDEX ix_link_source  ON link(source_id) WHERE status = 'accepted';
CREATE INDEX ix_link_target  ON link(target_id) WHERE status = 'accepted';
CREATE INDEX ix_link_patient ON link(patient_id, status);

-- Réservé V2 (A4.9, A8) : retours du praticien sur les suggestions de l'IA. Vide en V1.
CREATE TABLE ai_feedback (
  id                   uuid PRIMARY KEY,
  link_id              uuid NOT NULL REFERENCES link(id),
  decision             text NOT NULL CHECK (decision IN ('accepted','rejected','modified','ignored')),
  reason               text,
  pipeline_version     text,
  latency_to_decision  interval,
  decided_at           timestamptz NOT NULL DEFAULT now()
);

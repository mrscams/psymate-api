-- Profil 360 (M13) : informations durables sur la personne, hors séance (G Q62 à Q70)
CREATE TABLE patient_profile_item (
  id            uuid PRIMARY KEY,
  tenant_id     uuid NOT NULL REFERENCES tenant(id),
  patient_id    uuid NOT NULL REFERENCES patient(id),
  category      text NOT NULL CHECK (category IN ('history','family','health','values_goals')),
  title         text NOT NULL,                         -- l'information elle-même
  detail        text,                                  -- seconde ligne facultative (précision, citation)
  period_label  text,                                  -- 'history' seulement : affiché tel quel, jamais interprété (G Q66)
  period_year   smallint CHECK (period_year BETWEEN 1900 AND 2100),  -- 'history' seulement : tri du fil chronologique
  created_by    uuid NOT NULL REFERENCES practitioner(id),
  archived_at   timestamptz,                           -- retrait de la vue, jamais de DELETE SQL (G Q68)
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_patient_profile_item_period CHECK (category = 'history' OR (period_label IS NULL AND period_year IS NULL))
);
CREATE INDEX ix_patient_profile_item_patient ON patient_profile_item(patient_id) WHERE archived_at IS NULL;

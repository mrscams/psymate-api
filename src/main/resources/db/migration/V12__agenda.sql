-- V12 (M11-02) : appointment (docs/I-plan-agenda-api.md I2, G Q50 à Q55). Agenda local du
-- praticien : une ligne par rendez-vous, jamais supprimée (annulation = status 'cancelled', G Q53).
-- Aucune colonne Google ni table practitioner_integration : synchronisation en M12 (G Q51). Les
-- droits de psymate_app viennent des ALTER DEFAULT PRIVILEGES de docker/postgres/init/01-app-role.sql.

-- Agenda (fiches 15, 15b) — rendez-vous planifiés, sans synchronisation externe (G Q51)
CREATE TABLE appointment (
  id                uuid PRIMARY KEY,
  tenant_id         uuid NOT NULL REFERENCES tenant(id),
  practitioner_id   uuid NOT NULL REFERENCES practitioner(id),
  patient_id        uuid NOT NULL REFERENCES patient(id),
  series_id         uuid,                               -- commun aux occurrences d'une répétition (G Q55)
  starts_at         timestamptz NOT NULL,
  ends_at           timestamptz NOT NULL,
  modality          text NOT NULL CHECK (modality IN ('in_person','video','phone')),
  status            text NOT NULL DEFAULT 'planned' CHECK (status IN ('planned','tentative','cancelled','done')),
  planning_note     text,                               -- visible seulement ici ; jamais synchronisée (fiche 15)
  cancel_reason     text,
  cancelled_at      timestamptz,
  session_entry_id  uuid REFERENCES entry(id),          -- séance rattachée (M11-08), NULL sinon
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_appointment_order CHECK (ends_at > starts_at),
  CONSTRAINT ck_appointment_cancel CHECK ((status = 'cancelled') = (cancelled_at IS NOT NULL))
);
CREATE INDEX ix_appointment_practitioner_time ON appointment(practitioner_id, starts_at);
CREATE INDEX ix_appointment_patient ON appointment(patient_id, starts_at DESC);
CREATE INDEX ix_appointment_series ON appointment(series_id) WHERE series_id IS NOT NULL;

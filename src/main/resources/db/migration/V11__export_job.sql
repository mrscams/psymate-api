-- V11 (M6-05) : export_job (E3.6, décision E-15, G Q18). Export asynchrone des patients d'un compte
-- refusé ou suspendu (fiche 27). Le zip chiffré est stocké par le même StorageService que les
-- justificatifs ; la ligne reste après la purge du fichier (purged_at). Les droits de psymate_app
-- viennent des ALTER DEFAULT PRIVILEGES de docker/postgres/init/01-app-role.sql : aucun GRANT ici.

CREATE TABLE export_job (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  status           text NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','running','done','failed')),
  requested_at     timestamptz NOT NULL DEFAULT now(),
  finished_at      timestamptz,
  storage_key      text,                               -- zip chiffré (même StorageService que les justificatifs)
  expires_at       timestamptz,                        -- finished_at + 7 jours ; le fichier est supprimé, la ligne reste
  purged_at        timestamptz,
  error            text
);
CREATE INDEX ix_export_job_practitioner ON export_job(practitioner_id, requested_at DESC);
CREATE INDEX ix_export_job_purge ON export_job(expires_at) WHERE purged_at IS NULL AND status = 'done';

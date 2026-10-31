-- M5-03 (directory) : relais de contact (F26, fiche 09). E3.10.
-- Trace technique 30 jours pour l'anti-spam, puis purge. Jamais lié à un dossier.
CREATE TABLE contact_request (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  sender_name      text NOT NULL,
  sender_email     text NOT NULL,
  subject          text,
  message          text NOT NULL,
  consent_at       timestamptz NOT NULL,
  ip_hash          text NOT NULL,                      -- SHA-256(ip + sel du jour), pour la limite 5 / h / IP
  relayed_at       timestamptz,
  relay_error      text,
  created_at       timestamptz NOT NULL DEFAULT now(),
  purge_after      timestamptz NOT NULL                -- created_at + 30 jours ; DELETE physique par tâche planifiée
);
CREATE INDEX ix_contact_request_rate ON contact_request(ip_hash, created_at DESC);
CREATE INDEX ix_contact_request_purge ON contact_request(purge_after);

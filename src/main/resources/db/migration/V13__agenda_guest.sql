-- Rendez-vous invité (M11-10, G Q60) : une personne qui n'a pas encore de fiche patient, décrite
-- seulement par nom, prénom et téléphone dans ses lignes appointment. Purge à 6 mois : les colonnes
-- guest_* passent à NULL et guest_purged_at est renseigné, la ligne reste (E9).
ALTER TABLE appointment ALTER COLUMN patient_id DROP NOT NULL;
ALTER TABLE appointment
  ADD COLUMN guest_first_name text,
  ADD COLUMN guest_last_name  text,
  ADD COLUMN guest_phone      text,
  ADD COLUMN guest_purged_at  timestamptz,
  ADD CONSTRAINT ck_appointment_subject CHECK (
    (patient_id IS NOT NULL AND guest_first_name IS NULL AND guest_last_name IS NULL AND guest_phone IS NULL AND guest_purged_at IS NULL)
    OR (patient_id IS NULL AND (guest_last_name IS NOT NULL OR guest_purged_at IS NOT NULL))
  );
CREATE INDEX ix_appointment_guest_purge ON appointment(ends_at) WHERE patient_id IS NULL AND guest_purged_at IS NULL;

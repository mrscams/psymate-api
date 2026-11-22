-- Prise de rendez-vous en ligne (K, G Q71 à Q82) : origine et e-mail de l'invité, demandes publiques
ALTER TABLE appointment
  ADD COLUMN guest_email text,
  ADD COLUMN origin      text NOT NULL DEFAULT 'practitioner' CHECK (origin IN ('practitioner','online'));

ALTER TABLE appointment DROP CONSTRAINT ck_appointment_subject;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_subject CHECK (
  (patient_id IS NOT NULL AND guest_first_name IS NULL AND guest_last_name IS NULL AND guest_phone IS NULL AND guest_email IS NULL AND guest_purged_at IS NULL)
  OR (patient_id IS NULL AND (guest_last_name IS NOT NULL OR guest_purged_at IS NOT NULL))
);
CREATE INDEX ix_appointment_online_pending ON appointment(practitioner_id, starts_at)
  WHERE origin = 'online' AND status = 'tentative';

CREATE TABLE booking_request (
  id                 uuid PRIMARY KEY,
  tenant_id          uuid NOT NULL REFERENCES tenant(id),
  practitioner_id    uuid NOT NULL REFERENCES practitioner(id),
  starts_at          timestamptz NOT NULL,
  ends_at            timestamptz NOT NULL,
  modality           text NOT NULL CHECK (modality IN ('in_person','video','phone')),
  first_name         text,                 -- NULL dès la vérification (Q82 b)
  last_name          text,
  email              text,
  phone              text,
  locale             text NOT NULL DEFAULT 'fr' CHECK (locale IN ('fr','en')),
  email_hash         text NOT NULL,        -- HMAC de l'adresse normalisée : plafonds de Q79
  verify_token_hash  text NOT NULL UNIQUE,
  verify_expires_at  timestamptz NOT NULL,
  verified_at        timestamptz,
  manage_token_hash  text UNIQUE,
  appointment_id     uuid REFERENCES appointment(id),
  closed_by          text CHECK (closed_by IN ('visitor','practitioner','expired')),
  created_at         timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_booking_request_order CHECK (ends_at > starts_at),
  CONSTRAINT ck_booking_request_verified CHECK ((verified_at IS NULL) = (appointment_id IS NULL)),
  CONSTRAINT ck_booking_request_identity CHECK (
    verified_at IS NULL OR (first_name IS NULL AND last_name IS NULL AND email IS NULL AND phone IS NULL)
  )
);
CREATE INDEX ix_booking_request_email ON booking_request(email_hash, created_at);
CREATE INDEX ix_booking_request_created ON booking_request(created_at) WHERE verified_at IS NULL;
CREATE INDEX ix_booking_request_appointment ON booking_request(appointment_id) WHERE appointment_id IS NOT NULL;

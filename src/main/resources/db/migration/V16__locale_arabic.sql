-- Langue arabe (M16-02, G Q96, Q97) : practitioner.locale (V01) et booking_request.locale (V15)
-- acceptent 'ar'. Les deux contraintes CHECK de colonne gardent le nom que PostgreSQL leur a donné.
ALTER TABLE practitioner
  DROP CONSTRAINT practitioner_locale_check,
  ADD CONSTRAINT practitioner_locale_check CHECK (locale IN ('fr','en','ar'));

ALTER TABLE booking_request
  DROP CONSTRAINT booking_request_locale_check,
  ADD CONSTRAINT booking_request_locale_check CHECK (locale IN ('fr','en','ar'));

-- V03 (M1-10) : tables de vérification des praticiens et d'administration (E3.3).
-- Les colonnes de practitioner (A10.2) existent déjà depuis V01 : aucun ALTER TABLE ici.

CREATE TABLE verification_document (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  purpose          text NOT NULL DEFAULT 'verification' CHECK (purpose IN ('verification','appeal')),
  type             text NOT NULL CHECK (type IN
                     ('diploma','registration','certification','professional_card','identity','other')),
  title            text NOT NULL,
  issuer           text,
  year             smallint CHECK (year BETWEEN 1950 AND 2100),
  storage_key      text NOT NULL,                      -- objet chiffré (disque local en dev ; bêta : étude hébergement, G Q17), hors base
  mime             text NOT NULL CHECK (mime IN ('application/pdf','image/jpeg','image/png')),
  size_bytes       int  NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),   -- 10 Mo
  sha256           text NOT NULL,
  uploaded_at      timestamptz NOT NULL DEFAULT now(),
  reviewed_status  text NOT NULL DEFAULT 'pending' CHECK (reviewed_status IN ('pending','conform','non_conform')),
  reviewed_at      timestamptz,
  reviewed_by      uuid REFERENCES practitioner(id),
  purge_after      timestamptz,                        -- décision + 90 jours ; le fichier est supprimé, la ligne reste
  purged_at        timestamptz,
  deleted_at       timestamptz                         -- retiré par le praticien avant soumission (soft delete)
);
CREATE INDEX ix_verif_doc_practitioner ON verification_document(practitioner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_verif_doc_purge ON verification_document(purge_after) WHERE purged_at IS NULL AND purge_after IS NOT NULL;

-- Décisions d'administration : append-only, une ligne par acte (A10.2, étendu par les fiches 21, 23, 27). Décision E-6.
CREATE TABLE verification_decision (
  id                       uuid PRIMARY KEY,
  practitioner_id          uuid NOT NULL REFERENCES practitioner(id),
  admin_id                 uuid NOT NULL REFERENCES practitioner(id),
  kind                     text NOT NULL CHECK (kind IN
                             ('approved','needs_more','rejected',          -- validation (UC-17)
                              'suspended','reactivated',                   -- fiche 21 (M1-14b)
                              'appeal_accepted','appeal_rejected',         -- fiche 27 (M1-14b)
                              'report_resolved')),                         -- fiche 23 (V1.1)
  internal_note            text,                       -- jamais transmise
  message_to_practitioner  text,                       -- obligatoire si needs_more, rejected, suspended (contrainte applicative)
  checklist                jsonb NOT NULL DEFAULT '{}'::jsonb,
  related_id               uuid,                       -- recours ou signalement concerné, selon kind
  decided_at               timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_decision_message CHECK (
    kind NOT IN ('needs_more','rejected','suspended') OR message_to_practitioner IS NOT NULL)
);
CREATE INDEX ix_verif_decision_practitioner ON verification_decision(practitioner_id, decided_at DESC);
CREATE INDEX ix_verif_decision_admin ON verification_decision(admin_id, decided_at DESC);

-- Chaque ouverture d'un justificatif par un administrateur (URL signée 5 min).
CREATE TABLE document_access_log (
  id           uuid PRIMARY KEY,
  document_id  uuid NOT NULL REFERENCES verification_document(id),
  admin_id     uuid NOT NULL REFERENCES practitioner(id),
  ip           inet,
  accessed_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_doc_access_document ON document_access_log(document_id, accessed_at DESC);

-- Recours : un seul par décision, examiné par un autre administrateur (fiche 27, M1-14b, G Q28). Décision E-7.
CREATE TABLE practitioner_appeal (
  id                uuid PRIMARY KEY,
  practitioner_id   uuid NOT NULL REFERENCES practitioner(id),
  decision_id       uuid NOT NULL REFERENCES verification_decision(id),
  message           text NOT NULL,
  submitted_at      timestamptz NOT NULL DEFAULT now(),
  reviewed_by       uuid REFERENCES practitioner(id),  -- doit différer de verification_decision.admin_id (règle applicative)
  reviewed_at       timestamptz,
  outcome           text CHECK (outcome IN ('accepted','rejected')),
  response_message  text
);
CREATE UNIQUE INDEX ux_appeal_decision ON practitioner_appeal(decision_id);

-- Tables append-only (E3.3) : le rôle applicatif ne peut ni modifier ni supprimer (G Q9).
REVOKE UPDATE, DELETE ON verification_decision, document_access_log FROM psymate_app;

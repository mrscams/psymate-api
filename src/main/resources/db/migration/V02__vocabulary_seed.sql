-- V02 vocabulary (M1-02) : E3.5 tag, patient_tag, emotion_merge + E5.1, E5.2, E5.3 (seed copie sans modification, G Q11, Q12).
-- emotion_merge : DDL repris de E3.5 (la table emotion existe depuis V01).

-- Fusion de deux émotions custom (fiche 17), réversible 30 jours. Décision E-11, retenue en V1 (G Q8).
CREATE TABLE emotion_merge (
  id               uuid PRIMARY KEY,
  practitioner_id  uuid NOT NULL REFERENCES practitioner(id),
  source_code      text NOT NULL REFERENCES emotion(code),
  target_code      text NOT NULL REFERENCES emotion(code),
  affected_count   int NOT NULL,
  merged_at        timestamptz NOT NULL DEFAULT now(),
  undo_until       timestamptz NOT NULL,
  undone_at        timestamptz
);

-- Tags libres du praticien (A4.6). practitioner_id NULL = tag partagé global (V2, réservé).
CREATE TABLE tag (
  id                uuid PRIMARY KEY,
  tenant_id         uuid REFERENCES tenant(id),
  practitioner_id   uuid REFERENCES practitioner(id),
  label             text NOT NULL,
  normalized_label  text NOT NULL,                     -- lower(unaccent(label)), sans '#'
  usage_count       int NOT NULL DEFAULT 0,            -- maintenu par l'application (autocomplétion)
  merged_into       uuid REFERENCES tag(id),           -- fusion de tags (UC-11)
  created_at        timestamptz NOT NULL DEFAULT now(),
  archived_at       timestamptz
);
CREATE UNIQUE INDEX ux_tag_practitioner_label ON tag(practitioner_id, normalized_label) WHERE archived_at IS NULL;

-- Tags au niveau patient (fiche 16). Décision E-12, confirmée G Q7.
CREATE TABLE patient_tag (
  patient_id  uuid NOT NULL REFERENCES patient(id),
  tag_id      uuid NOT NULL REFERENCES tag(id),
  added_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (patient_id, tag_id)
);
CREATE INDEX ix_patient_tag_tag ON patient_tag(tag_id);

INSERT INTO entry_type (code, label_fr, label_en, detail_table, has_duration, sort_order) VALUES
  ('note',       'Note',       'Note',       NULL,               false, 1),
  ('event',      'Événement',  'Event',      NULL,               false, 2),
  ('situation',  'Situation',  'Situation',  NULL,               true,  3),
  ('feeling',    'Ressenti',   'Feeling',    'entry_feeling',    false, 4),
  ('session',    'Séance',     'Session',    'entry_session',    true,  5),
  ('medication', 'Médicament', 'Medication', 'entry_medication', true,  6);

INSERT INTO emotion (code, scope, family, intensity_level, label_fr, label_en, label_normalized, vocabulary_version) VALUES
  -- Joie
  ('serenity',      'global', 'joy',          1, 'Sérénité',        'Serenity',      'serenite',        1),
  ('joy',           'global', 'joy',          2, 'Joie',            'Joy',           'joie',            1),
  ('ecstasy',       'global', 'joy',          3, 'Extase',          'Ecstasy',       'extase',          1),
  -- Confiance
  ('acceptance',    'global', 'trust',        1, 'Acceptation',     'Acceptance',    'acceptation',     1),
  ('trust',         'global', 'trust',        2, 'Confiance',       'Trust',         'confiance',       1),
  ('admiration',    'global', 'trust',        3, 'Admiration',      'Admiration',    'admiration',      1),
  -- Peur
  ('apprehension',  'global', 'fear',         1, 'Appréhension',    'Apprehension',  'apprehension',    1),
  ('fear',          'global', 'fear',         2, 'Peur',            'Fear',          'peur',            1),
  ('terror',        'global', 'fear',         3, 'Terreur',         'Terror',        'terreur',         1),
  -- Surprise
  ('distraction',   'global', 'surprise',     1, 'Distraction',     'Distraction',   'distraction',     1),
  ('surprise',      'global', 'surprise',     2, 'Surprise',        'Surprise',      'surprise',        1),
  ('amazement',     'global', 'surprise',     3, 'Stupéfaction',    'Amazement',     'stupefaction',    1),
  -- Tristesse
  ('pensiveness',   'global', 'sadness',      1, 'Mélancolie',      'Pensiveness',   'melancolie',      1),
  ('sadness',       'global', 'sadness',      2, 'Tristesse',       'Sadness',       'tristesse',       1),
  ('grief',         'global', 'sadness',      3, 'Chagrin',         'Grief',         'chagrin',         1),
  -- Dégoût
  ('boredom',       'global', 'disgust',      1, 'Ennui',           'Boredom',       'ennui',           1),
  ('disgust',       'global', 'disgust',      2, 'Dégoût',          'Disgust',       'degout',          1),
  ('loathing',      'global', 'disgust',      3, 'Répulsion',       'Loathing',      'repulsion',       1),
  -- Colère
  ('annoyance',     'global', 'anger',        1, 'Agacement',       'Annoyance',     'agacement',       1),
  ('anger',         'global', 'anger',        2, 'Colère',          'Anger',         'colere',          1),
  ('rage',          'global', 'anger',        3, 'Rage',            'Rage',          'rage',            1),
  -- Anticipation
  ('interest',      'global', 'anticipation', 1, 'Intérêt',         'Interest',      'interet',         1),
  ('anticipation',  'global', 'anticipation', 2, 'Anticipation',    'Anticipation',  'anticipation',    1),
  ('vigilance',     'global', 'anticipation', 3, 'Vigilance',       'Vigilance',     'vigilance',       1),
  -- Ajouts cliniques (familles tranchées, G Q11)
  ('anxiety',       'global', 'fear',         NULL, 'Anxiété',      'Anxiety',       'anxiete',         1),
  ('guilt',         'global', 'sadness',      NULL, 'Culpabilité',  'Guilt',         'culpabilite',     1),
  ('shame',         'global', 'disgust',      NULL, 'Honte',        'Shame',         'honte',           1),
  ('emptiness',     'global', 'sadness',      NULL, 'Vide',         'Emptiness',     'vide',            1),
  ('calm',          'global', 'trust',        NULL, 'Calme',        'Calm',          'calme',           1);

INSERT INTO link_type (code, directed, label_fr, label_en, inverse_label_fr, inverse_label_en,
                       allowed_source_types, allowed_target_types, line_style, sort_order) VALUES
  ('triggers',  true,  'déclenche',       'triggers',       'déclenché par', 'triggered by',
     ARRAY['event','situation','session'],                 ARRAY['feeling','situation','event'],            'solid',      1),
  ('caused_by', true,  'est causé par',   'is caused by',   'cause',         'causes',
     ARRAY['feeling','situation','event'],                 ARRAY['event','situation'],                      'solid',      2),
  ('worsens',   true,  'aggrave',         'worsens',        'aggravé par',   'worsened by',
     ARRAY['situation','event','medication'],              ARRAY['feeling','situation'],                    'long_dash',  3),
  ('relieves',  true,  'soulage',         'relieves',       'soulagé par',   'relieved by',
     ARRAY['event','situation','medication','session'],    ARRAY['feeling','situation'],                    'short_dash', 4),
  ('co_occurs', false, 'survient avec',   'co-occurs with', NULL,            NULL,
     ARRAY['note','event','situation','feeling','medication'], ARRAY['note','event','situation','feeling','medication'], 'dotted', 5),
  ('follows',   true,  'fait suite à',    'follows',        'suivi de',      'followed by',
     ARRAY['note','event','situation','feeling'],          ARRAY['note','event','situation','feeling'],     'thin',       6),
  ('part_of',   true,  'fait partie de',  'is part of',     'comprend',      'includes',
     ARRAY['event','feeling','note'],                      ARRAY['situation','session'],                    'thin',       7),
  ('treats',    true,  'traite',          'treats',         'traité par',    'treated by',
     ARRAY['medication'],                                  ARRAY['situation','feeling'],                    'double',     8);


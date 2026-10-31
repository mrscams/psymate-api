-- V04 : annuaire public (M5-01). Copié de docs/E-modele-donnees.md E3.10 (sans contact_request, V06) + seed initial.

CREATE TABLE directory_vocabulary (
  kind        text NOT NULL CHECK (kind IN ('specialty','audience')),
  code        text NOT NULL,                           -- 'anxiety', 'couples_families'…
  label_fr    text NOT NULL,
  label_en    text,
  sort_order  smallint NOT NULL DEFAULT 0,
  active      boolean NOT NULL DEFAULT true,           -- désactivation, jamais suppression
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (kind, code)
);

CREATE TABLE practitioner_profile (
  practitioner_id   uuid PRIMARY KEY REFERENCES practitioner(id),
  slug              text NOT NULL,                     -- URL publique /praticiens/{slug} (fiches 09, 10). Décision E-14
  published         boolean NOT NULL DEFAULT false,
  display_name      text NOT NULL,
  title_label       text,                              -- « Psychologue clinicienne », « Coach mental »
  show_professional_number boolean NOT NULL DEFAULT false,
  photo_asset_key   text,                              -- objet redimensionné, sans EXIF
  bio               text,
  specialties       text[] NOT NULL DEFAULT '{}',      -- codes actifs de directory_vocabulary (kind = specialty), validés par l'application
  audiences         text[] NOT NULL DEFAULT '{}',      -- codes actifs de directory_vocabulary (kind = audience) : adults, young_adults, adolescents, children, couples_families
  modalities        text[] NOT NULL DEFAULT '{}',      -- in_person, video, home
  languages         text[] NOT NULL DEFAULT '{}',      -- codes ISO 639-1
  city              text,
  postal_code       text,
  country           text NOT NULL DEFAULT 'FR',
  geo               point,                             -- tri par distance
  contact_phone     text,
  contact_email     text,
  website_url       text,
  address           text,
  show_phone        boolean NOT NULL DEFAULT false,
  show_email        boolean NOT NULL DEFAULT false,
  show_address      boolean NOT NULL DEFAULT false,
  response_delay    text,                              -- « sous 48 h », NULL = ne pas afficher
  fees              text,                              -- texte libre, facultatif
  availability      text NOT NULL DEFAULT 'unknown' CHECK (availability IN ('accepting','waitlist','closed','unknown')),
  next_slot_at      date,
  search_vector     tsvector,                          -- bio + spécialités + ville ; trigger ci-dessous
  published_at      timestamptz,
  updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_profile_slug  ON practitioner_profile(slug);
CREATE INDEX ix_profile_public ON practitioner_profile(published, updated_at DESC) WHERE published;
CREATE INDEX ix_profile_search ON practitioner_profile USING gin (search_vector);
CREATE INDEX ix_profile_geo    ON practitioner_profile USING gist (geo) WHERE published;

CREATE OR REPLACE FUNCTION trg_profile_search_vector() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', f_unaccent(coalesce(NEW.display_name, ''))), 'A') ||
    setweight(to_tsvector('simple', f_unaccent(array_to_string(NEW.specialties, ' '))), 'B') ||
    setweight(to_tsvector('simple', f_unaccent(coalesce(NEW.city, ''))), 'B') ||
    setweight(to_tsvector('french', f_unaccent(coalesce(NEW.bio, ''))), 'C');
  RETURN NEW;
END $$;

CREATE TRIGGER profile_search_vector_before_write
  BEFORE INSERT OR UPDATE ON practitioner_profile
  FOR EACH ROW EXECUTE FUNCTION trg_profile_search_vector();

-- Seed initial de la liste fermée (G Q15) ; l'administration de la liste est M1-14b.
INSERT INTO directory_vocabulary (kind, code, label_fr, label_en, sort_order) VALUES
  ('specialty', 'anxiety',            'Anxiété',                       'Anxiety',                      1),
  ('specialty', 'depression',         'Dépression',                    'Depression',                   2),
  ('specialty', 'cbt',                'Thérapie cognitivo-comportementale (TCC)', 'Cognitive behavioural therapy (CBT)', 3),
  ('specialty', 'emdr',               'EMDR',                          'EMDR',                         4),
  ('specialty', 'couple_therapy',     'Thérapie de couple',            'Couple therapy',               5),
  ('specialty', 'burnout',            'Burn-out',                      'Burnout',                      6),
  ('specialty', 'adhd',               'TDAH',                          'ADHD',                         7),
  ('specialty', 'addictions',         'Addictions',                    'Addictions',                   8),
  ('specialty', 'trauma_ptsd',        'Traumatismes et stress post-traumatique', 'Trauma and PTSD',    9),
  ('specialty', 'phobias',            'Phobies',                       'Phobias',                     10),
  ('specialty', 'ocd',                'TOC',                           'OCD',                         11),
  ('specialty', 'eating_disorders',   'Troubles du comportement alimentaire', 'Eating disorders',     12),
  ('specialty', 'bipolar',            'Troubles bipolaires',           'Bipolar disorders',           13),
  ('specialty', 'grief',              'Deuil',                         'Grief',                       14),
  ('specialty', 'self_esteem',        'Estime de soi',                 'Self-esteem',                 15),
  ('specialty', 'stress_management',  'Gestion du stress',             'Stress management',           16),
  ('specialty', 'sleep_disorders',    'Troubles du sommeil',           'Sleep disorders',             17),
  ('specialty', 'autism_spectrum',    'Troubles du spectre autistique','Autism spectrum',             18),
  ('specialty', 'parenting',          'Parentalité',                   'Parenting',                   19),
  ('specialty', 'career_coaching',    'Accompagnement professionnel',  'Career coaching',             20),
  ('specialty', 'mindfulness',        'Pleine conscience',             'Mindfulness',                 21),
  ('specialty', 'psychodynamic',      'Approche psychodynamique',      'Psychodynamic approach',      22),
  ('audience',  'adults',             'Adultes',                       'Adults',                       1),
  ('audience',  'young_adults',       'Jeunes adultes',                'Young adults',                 2),
  ('audience',  'adolescents',        'Adolescents',                   'Adolescents',                  3),
  ('audience',  'children',           'Enfants',                       'Children',                     4),
  ('audience',  'couples_families',   'Couples et familles',           'Couples and families',         5);

-- Libellés arabes des catalogues (M16-03, G Q98). Colonnes nulles par défaut, jamais obligatoires :
-- une ligne sans libellé arabe est rendue en français quand l'arabe est demandé. Les lignes globales
-- reçoivent le libellé proposé par le glossaire du web (psymate-web/docs/design/arabe-glossaire.md,
-- sections 4, 5.2, 6, 9.1 et 9.2), tel quel, par code. Les émotions custom (scope = 'custom') et
-- les entrées ajoutées par l'administration hors du seed de V04 restent sans libellé arabe.
ALTER TABLE entry_type           ADD COLUMN label_ar text;
ALTER TABLE emotion              ADD COLUMN label_ar text;
ALTER TABLE link_type            ADD COLUMN label_ar text,
                                 ADD COLUMN inverse_label_ar text;
ALTER TABLE directory_vocabulary ADD COLUMN label_ar text;

-- entry_type (glossaire, section 4) : 6 lignes.
UPDATE entry_type AS t SET label_ar = v.label_ar
FROM (VALUES
  ('note',       'ملاحظة'),
  ('event',      'حدث'),
  ('situation',  'وضعية'),
  ('feeling',    'شعور'),
  ('session',    'جلسة'),
  ('medication', 'دواء')
) AS v(code, label_ar)
WHERE t.code = v.code;

-- emotion globale (glossaire, section 5.2) : 29 lignes ; une émotion custom n'est jamais touchée.
UPDATE emotion AS t SET label_ar = v.label_ar
FROM (VALUES
  ('serenity',     'صفاء'),
  ('joy',          'فرح'),
  ('ecstasy',      'نشوة'),
  ('acceptance',   'تقبّل'),
  ('trust',        'ثقة'),
  ('admiration',   'إعجاب'),
  ('apprehension', 'توجّس'),
  ('fear',         'خوف'),
  ('terror',       'رعب'),
  ('distraction',  'شرود'),
  ('surprise',     'مفاجأة'),
  ('amazement',    'ذهول'),
  ('pensiveness',  'شجن'),
  ('sadness',      'حزن'),
  ('grief',        'أسى'),
  ('boredom',      'ملل'),
  ('disgust',      'اشمئزاز'),
  ('loathing',     'نفور'),
  ('annoyance',    'انزعاج'),
  ('anger',        'غضب'),
  ('rage',         'هياج'),
  ('interest',     'اهتمام'),
  ('anticipation', 'ترقّب'),
  ('vigilance',    'يقظة'),
  ('anxiety',      'قلق'),
  ('guilt',        'شعور بالذنب'),
  ('shame',        'خزي'),
  ('emptiness',    'شعور بالفراغ'),
  ('calm',         'هدوء')
) AS v(code, label_ar)
WHERE t.code = v.code AND t.scope = 'global';

-- link_type (glossaire, section 6) : 8 lignes ; co_occurs, non orienté, n'a pas d'inverse.
UPDATE link_type AS t SET label_ar = v.label_ar, inverse_label_ar = v.inverse_label_ar
FROM (VALUES
  ('triggers',  'يُثير',      'يُثيره'),
  ('caused_by', 'ناتج عن',    'يُسبِّب'),
  ('worsens',   'يُفاقِم',    'يتفاقم بسبب'),
  ('relieves',  'يُخفِّف',    'يخفّ بفضل'),
  ('co_occurs', 'يتزامن مع',  NULL::text),
  ('follows',   'يلي',        'يليه'),
  ('part_of',   'جزء من',     'يتضمّن'),
  ('treats',    'يُعالِج',    'يُعالَج بـ')
) AS v(code, label_ar, inverse_label_ar)
WHERE t.code = v.code;

-- directory_vocabulary (glossaire, sections 9.1 et 9.2) : 22 spécialités, 5 publics.
UPDATE directory_vocabulary AS t SET label_ar = v.label_ar
FROM (VALUES
  ('specialty', 'anxiety',            'القلق'),
  ('specialty', 'depression',         'الاكتئاب'),
  ('specialty', 'cbt',                'العلاج المعرفي السلوكي'),
  ('specialty', 'emdr',               'EMDR'),
  ('specialty', 'couple_therapy',     'العلاج الزوجي'),
  ('specialty', 'burnout',            'الاحتراق النفسي'),
  ('specialty', 'adhd',               'اضطراب فرط الحركة وتشتّت الانتباه'),
  ('specialty', 'addictions',         'الإدمان'),
  ('specialty', 'trauma_ptsd',        'الصدمات النفسية واضطراب ما بعد الصدمة'),
  ('specialty', 'phobias',            'الرهاب'),
  ('specialty', 'ocd',                'الوسواس القهري'),
  ('specialty', 'eating_disorders',   'اضطرابات الأكل'),
  ('specialty', 'bipolar',            'الاضطراب ثنائي القطب'),
  ('specialty', 'grief',              'الحِداد'),
  ('specialty', 'self_esteem',        'تقدير الذات'),
  ('specialty', 'stress_management',  'إدارة الضغط النفسي'),
  ('specialty', 'sleep_disorders',    'اضطرابات النوم'),
  ('specialty', 'autism_spectrum',    'اضطراب طيف التوحّد'),
  ('specialty', 'parenting',          'الوالدية'),
  ('specialty', 'career_coaching',    'التوجيه المهني'),
  ('specialty', 'mindfulness',        'اليقظة الذهنية'),
  ('specialty', 'psychodynamic',      'المقاربة الدينامية النفسية'),
  ('audience',  'adults',             'البالغون'),
  ('audience',  'young_adults',       'الشباب'),
  ('audience',  'adolescents',        'المراهقون'),
  ('audience',  'children',           'الأطفال'),
  ('audience',  'couples_families',   'الأزواج والعائلات')
) AS v(kind, code, label_ar)
WHERE t.kind = v.kind AND t.code = v.code;

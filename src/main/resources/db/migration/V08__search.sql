-- V08 search (M4-02) : E3.9 « Recherche ». Projection search_vector de entry, maintenue par trigger.
-- Ne suppose rien de la table link (V07 est livrée par une autre branche).
-- Poids : titre A, émotion et tags B, corps C, nom de séance / médicament D (A6).

CREATE OR REPLACE FUNCTION entry_search_vector_refresh(p_entry_id uuid) RETURNS void
LANGUAGE sql AS $$
  UPDATE entry e
  SET search_vector =
      setweight(to_tsvector('french', f_unaccent(coalesce(v.title, ''))), 'A') ||
      setweight(to_tsvector('simple', f_unaccent(coalesce(
        (SELECT string_agg(em.label_fr, ' ') FROM entry_feeling f JOIN emotion em ON em.code = f.emotion_code WHERE f.entry_id = e.id), ''))), 'B') ||
      setweight(to_tsvector('simple', f_unaccent(coalesce(
        (SELECT string_agg(t.label, ' ') FROM entry_tag et JOIN tag t ON t.id = et.tag_id WHERE et.entry_id = e.id), ''))), 'B') ||
      setweight(to_tsvector('french', f_unaccent(coalesce(v.body, ''))), 'C') ||
      setweight(to_tsvector('simple', f_unaccent(coalesce(
        (SELECT m.molecule FROM entry_medication m WHERE m.entry_id = e.id), ''))), 'D')
  FROM entry_version v
  WHERE e.id = p_entry_id AND v.id = e.current_version_id;
$$;

CREATE OR REPLACE FUNCTION trg_entry_search_vector() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  PERFORM entry_search_vector_refresh(NEW.id);
  RETURN NULL;
END $$;

CREATE TRIGGER entry_search_vector_after_version
  AFTER UPDATE OF current_version_id ON entry
  FOR EACH ROW EXECUTE FUNCTION trg_entry_search_vector();

-- Écart documenté E3.9 (1/2) : la règle de grâce de clinical (G Q31, 1 h 15) réécrit title/body de la
-- version courante EN PLACE, sans changer entry.current_version_id : le trigger ci-dessus ne
-- se déclenche pas. Ce second trigger rafraîchit le vecteur dans ce cas.
-- Écart documenté E3.9 (2/2) : à la création, clinical pose current_version_id dès l'INSERT de
-- l'entrée, puis insère la version 1 : aucun UPDATE de current_version_id n'a lieu non plus. Le
-- même trigger écoute donc l'INSERT de la version (EXISTS : seulement si elle est la version
-- courante, ce qui exclut l'INSERT d'une version de correction, couverte par le premier trigger).
-- Les tags et l'émotion, insérés après la version, sont indexés par les abonnés EntryCreated /
-- EntryCorrected (SearchIndexListener) qui rappellent entry_search_vector_refresh.
CREATE OR REPLACE FUNCTION trg_entry_version_search_vector() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM entry WHERE id = NEW.entry_id AND current_version_id = NEW.id) THEN
    PERFORM entry_search_vector_refresh(NEW.entry_id);
  END IF;
  RETURN NULL;
END $$;

CREATE TRIGGER entry_search_vector_after_grace_edit
  AFTER INSERT OR UPDATE OF title, body ON entry_version
  FOR EACH ROW EXECUTE FUNCTION trg_entry_version_search_vector();

CREATE INDEX ix_entry_search ON entry USING gin (search_vector);

-- Rafraîchissement initial des entrées antérieures à cette migration (search_vector encore NULL).
-- Idempotent ; sans effet sur une base vierge.
SELECT entry_search_vector_refresh(id) FROM entry WHERE search_vector IS NULL AND current_version_id IS NOT NULL;

-- Écart documenté E3.9 : repli « résultats approchés » (A6, pg_trgm). Les index trigrammes de V05
-- portent sur le texte brut ; la requête compare le texte sans accents (f_unaccent est IMMUTABLE).
-- Cet index sur la même expression permet à l'opérateur <% (word_similarity) de s'en servir au lieu
-- de balayer et « désaccentuer » toutes les versions du dossier.
CREATE INDEX ix_entry_version_text_trgm ON entry_version
  USING gin ((f_unaccent(coalesce(title, '') || ' ' || coalesce(body, ''))) gin_trgm_ops);

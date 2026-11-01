-- V09 overview (M4-03) : E3.9 « Vue d'ensemble ». Projection patient_overview, une ligne JSONB par patient.
-- Recalculée par OverviewListener, ou à la volée par GET /api/patients/{id}/overview quand source_rev
-- est en retard sur patient.clinical_rev (G Q30). Dépend de V05 (entry) et V07 (link).
-- Les droits de psymate_app viennent des ALTER DEFAULT PRIVILEGES de docker/postgres/init/01-app-role.sql.

CREATE TABLE patient_overview (
  patient_id   uuid PRIMARY KEY REFERENCES patient(id),
  payload      jsonb NOT NULL,                         -- { facts, activeSituations, trend, emotions, narrative, sinceLastSession, recentSessions }
  source_rev   bigint NOT NULL,                        -- patient.clinical_rev au moment du calcul ; si < courant → recalcul à la volée
  computed_at  timestamptz NOT NULL DEFAULT now(),
  compute_ms   int
);

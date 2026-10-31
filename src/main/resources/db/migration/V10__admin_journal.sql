-- V10 (M1-14b) : vue admin_journal_v (E3.13, G Q29). Lecture seule.
-- Les droits de psymate_app viennent des ALTER DEFAULT PRIVILEGES de docker/postgres/init/01-app-role.sql :
-- aucun GRANT ici. La vue ne porte ni ip ni user_agent.

CREATE VIEW admin_journal_v AS
  SELECT e.id, e.occurred_at, e.kind, e.actor_id, e.practitioner_id,
         coalesce(e.details->>'summary', e.kind) AS summary, e.details, 'security_event' AS source
  FROM security_event e
  UNION ALL
  SELECT d.id, d.decided_at, 'admin.' || d.kind, d.admin_id, d.practitioner_id,
         coalesce(d.message_to_practitioner, d.kind), d.checklist, 'verification_decision'
  FROM verification_decision d;

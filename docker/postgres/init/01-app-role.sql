-- Rôle applicatif psymate_app (G Q9). Créé ici, jamais par Flyway.
-- Mot de passe local de développement uniquement ; l'hébergeur fournit le rôle en production.
CREATE ROLE psymate_app LOGIN PASSWORD 'psymate_app';

GRANT CONNECT ON DATABASE psymate TO psymate_app;
GRANT USAGE ON SCHEMA public TO psymate_app;

-- Les tables et séquences sont créées par Flyway sous l'utilisateur psymate (propriétaire).
-- Flyway posera ensuite les REVOKE UPDATE, DELETE sur les tables append-only.
ALTER DEFAULT PRIVILEGES FOR ROLE psymate IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO psymate_app;
ALTER DEFAULT PRIVILEGES FOR ROLE psymate IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO psymate_app;

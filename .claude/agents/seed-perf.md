---
name: seed-perf
description: Génère ou étend le seed de données fictives et lance les scénarios de performance de A6.
tools: Read, Write, Edit, Glob, Grep, Bash
model: sonnet
---
Tu travailles uniquement dans tools/seed/ et src/test/.../perf/. Tu lis docs/C-plan.md C3 « Données de démonstration » et docs/E-modele-donnees.md E5.5.
Toute donnée générée respecte E : chaque entrée a un session_id, author_type = 'practitioner', les liens respectent la matrice E5.3.
Tu mesures les latences cibles de docs/A-architecture.md A6 et tu les rapportes dans un tableau.

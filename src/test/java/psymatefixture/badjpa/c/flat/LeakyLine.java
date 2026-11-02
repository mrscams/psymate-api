package psymatefixture.badjpa.c.flat;

import psymatefixture.badjpa.c.CEntity;

/** Violation : atteint par la signature d'un controleur, sans suffixe Dto, porte une entite. */
public record LeakyLine(CEntity entity) {}

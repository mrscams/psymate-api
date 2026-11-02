package psymatefixture.badjpa.c.dto;

import psymatefixture.badjpa.c.CEntity;

/** Violation : une requete de dto/, sans suffixe Dto, qui porte une entite. */
public record LeakyRequest(CEntity entity) {}

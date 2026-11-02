package psymatefixture.badjpa.c.dto;

import psymatefixture.badjpa.c.CEntity;
import psymatefixture.badjpa.c.GoodDto;

/** Correct : un mapper de dto/ est le seul a toucher une entite (G Q33, revue §4.1 regle 3). */
public class CThingMapper {
  public GoodDto toDto(CEntity entity) {
    return new GoodDto(entity.hashCode() == 0 ? null : java.util.UUID.randomUUID());
  }
}

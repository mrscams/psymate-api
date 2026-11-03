package psymatefixture.badmapping.dto;

/** Interdit : un mapping écrit à la main s'appelle *Assembler, pas *Mapper. */
public class ManualMapper {
  static String map(Object o) {
    return String.valueOf(o);
  }
}

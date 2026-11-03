package psymatefixture.badmapping.dto;

/** Correct : mapping écrit à la main, nommé *Assembler. */
public final class ThingAssembler {
  private ThingAssembler() {}

  static String assemble(Object o) {
    return String.valueOf(o);
  }
}

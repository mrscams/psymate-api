package psymatefixture.shareddb;

/** Interdit : un bloc de texte est lu comme une chaîne. */
public class TextBlockCount {
  static final String SQL =
      """
      select count(*)
        from contact_request
      """;
}

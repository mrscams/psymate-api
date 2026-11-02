package psymatefixture.badtables.search;

/** Violation : une projection n'ecrit jamais la table d'un autre module (bloc de texte). */
public class SearchWritesBeta {
  static final String SQL =
      """
      insert into beta_item (id, name)
      values (?, ?)
      """;
}

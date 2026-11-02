package psymatefixture.badtables.beta;

/**
 * Correct : le SQL des commentaires est ignore, par exemple delete from beta_item ou insert into
 * alpha_item.
 */
public class BetaComments {
  // update alpha_item set x = 1
  static final char QUOTE = '"';
  static final String ESCAPED = "a \" quote";

  /* insert into alpha_item */
  static final String TEXT = "Unable to update the item";

  /** Texte d'annotation (prose OpenAPI) : jamais lu comme du SQL. */
  @Deprecated(since = "insert into alpha_item")
  static final String DOCUMENTED = "ok";
}

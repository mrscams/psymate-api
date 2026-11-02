package psymatefixture.badshared.shared;

/** Correct : un utilitaire technique sans module ni SQL. */
public final class GoodShared {
  private GoodShared() {}

  public static String trim(String s) {
    return s == null ? null : s.trim();
  }
}

package psymatefixture.shareddb;

/** Interdit hors base dédiée : rejouerait les publications inachevées des autres tests. */
public class FastRepublish {
  static final String PROPERTIES = "app.events.republish-older-than=30m";
}

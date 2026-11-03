package psymatefixture.shareddb;

/** Correct : pas sous le seuil du profil de test. */
public class SlowRepublish {
  static final String PROPERTIES = "app.events.republish-older-than=2h";
  static final String ISO = "app.events.republish-older-than=PT1H";
}

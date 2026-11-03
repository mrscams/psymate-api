package psymatefixture.shareddb;

/** Correct : le where, sur la ligne suivante, filtre sur les données du test. */
public class CountsOwnRows {
  static final String SQL =
      "select count(*) from entry e join entry_session s on s.entry_id = e.id"
          + " where e.patient_id = ?";
  static final String CATALOG = "select count(*) from pg_stat_activity";
}

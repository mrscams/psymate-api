package psymatefixture.apiusage.alpha.api;

import com.psymate.shared.Reserved;

/** Incorrect : annoté @Reserved alors que beta l'utilise. */
@Reserved(consumer = "V2", reason = "Point d'ancrage")
public record StaleReservation(String id) {}

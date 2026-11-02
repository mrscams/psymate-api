package psymatefixture.apiusage.alpha.api;

import com.psymate.shared.Reserved;

/** Correct : gardé exprès sans consommateur. */
@Reserved(consumer = "V2", reason = "Point d'ancrage")
public record ReservedEvent(String id) {}

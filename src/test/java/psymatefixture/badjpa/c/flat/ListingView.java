package psymatefixture.badjpa.c.flat;

import java.util.List;

/** Correct en soi : seul un type de ses champs porte une entite (LeakyLine). */
public record ListingView(List<LeakyLine> lines) {}

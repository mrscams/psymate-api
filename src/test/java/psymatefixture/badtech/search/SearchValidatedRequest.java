package psymatefixture.badtech.search;

import org.hibernate.validator.constraints.Length;

/** Correct : Hibernate Validator n'est pas une API JPA. */
public record SearchValidatedRequest(@Length(max = 10) String q) {}

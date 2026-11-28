package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clé composite de {@code billing_counter} : (praticien, nature, année civile). */
@Embeddable
public class BillingCounterId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "practitioner_id")
  private UUID practitionerId;

  @Column(name = "kind")
  private String kind;

  @Column(name = "year")
  private int year;

  protected BillingCounterId() {}

  public BillingCounterId(UUID practitionerId, String kind, int year) {
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.kind = Objects.requireNonNull(kind);
    this.year = year;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public String getKind() {
    return kind;
  }

  public int getYear() {
    return year;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof BillingCounterId other
            && year == other.year
            && Objects.equals(practitionerId, other.practitionerId)
            && Objects.equals(kind, other.kind));
  }

  @Override
  public int hashCode() {
    return Objects.hash(practitionerId, kind, year);
  }
}

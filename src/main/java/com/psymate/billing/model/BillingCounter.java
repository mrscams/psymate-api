package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Compteur de numérotation (table {@code billing_counter}, V18, G Q89) : dernier numéro attribué
 * par praticien, par nature ({@code invoice}, {@code credit_note}) et par année civile. M15-06 le
 * lit seulement (prochain numéro, verrou des préfixes) ; l'émission (M15-09) l'incrémente sous
 * {@code select … for update}. Aucune association JPA.
 */
@Entity
@Table(name = "billing_counter")
public class BillingCounter {

  @EmbeddedId private BillingCounterId id;

  /** Dernier numéro attribué, 0 ou plus. */
  @Column(nullable = false)
  private int lastNumber;

  protected BillingCounter() {}

  public BillingCounterId getId() {
    return id;
  }

  public int getLastNumber() {
    return lastNumber;
  }
}

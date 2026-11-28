package com.psymate.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Compteur de numérotation (table {@code billing_counter}, V18, G Q89) : dernier numéro attribué
 * par praticien, par nature ({@code invoice}, {@code credit_note}) et par année civile de la date
 * d'émission. M15-06 le lit (prochain numéro, verrou des préfixes) ; l'émission (M15-09) crée la
 * ligne de l'année au besoin ({@code insert … on conflict do nothing}), la verrouille ({@code
 * select … for update}) et l'incrémente par {@link #assignNext}. Aucune association JPA.
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

  /**
   * Attribue le numéro suivant de la suite (dernier plus un) et le renvoie. Réservé à une ligne
   * verrouillée dans la transaction d'émission : la suite reste continue, sans trou ni doublon.
   */
  public int assignNext() {
    lastNumber = Math.addExact(lastNumber, 1);
    return lastNumber;
  }
}

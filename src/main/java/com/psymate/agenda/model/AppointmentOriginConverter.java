package com.psymate.agenda.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link AppointmentOrigin} ↔ texte de {@code appointment.origin} : les constantes Java sont en
 * majuscules, la colonne porte la valeur en minuscules de la contrainte CHECK (V15).
 */
@Converter
public class AppointmentOriginConverter implements AttributeConverter<AppointmentOrigin, String> {

  @Override
  public String convertToDatabaseColumn(AppointmentOrigin origin) {
    return origin == null ? null : origin.value();
  }

  @Override
  public AppointmentOrigin convertToEntityAttribute(String value) {
    return value == null ? null : AppointmentOrigin.of(value);
  }
}

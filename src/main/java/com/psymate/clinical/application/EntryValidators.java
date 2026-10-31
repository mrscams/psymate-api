package com.psymate.clinical.application;

import com.psymate.clinical.domain.EntryChecks;
import com.psymate.clinical.domain.EntryContent;
import com.psymate.clinical.domain.EntryType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validateur Jakarta par type d'entrée : chaque type a son groupe ({@link EntryChecks}) avec ses
 * champs minimaux.
 */
@Component
public class EntryValidators {

  private final Validator validator;

  EntryValidators(Validator validator) {
    this.validator = validator;
  }

  /**
   * @throws ConstraintViolationException si le contenu ne respecte pas le groupe du type
   */
  public void validate(EntryType type, EntryContent content) {
    Set<ConstraintViolation<EntryContent>> violations =
        validator.validate(content, EntryChecks.groupOf(type));
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}

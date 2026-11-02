package com.psymate.vocabulary.repository;

import com.psymate.vocabulary.model.EntryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lecture seule du catalogue {@code entry_type}. Interne au module. */
public interface EntryTypeRepository extends JpaRepository<EntryType, String> {

  public List<EntryType> findByActiveTrueOrderBySortOrder();
}

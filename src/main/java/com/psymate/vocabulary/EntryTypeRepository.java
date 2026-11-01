package com.psymate.vocabulary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lecture seule du catalogue {@code entry_type}. Interne au module. */
interface EntryTypeRepository extends JpaRepository<EntryType, String> {

  List<EntryType> findByActiveTrueOrderBySortOrder();
}

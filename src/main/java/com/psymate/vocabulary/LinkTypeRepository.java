package com.psymate.vocabulary;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Lecture seule du catalogue {@code link_type}. Interne au module. */
interface LinkTypeRepository extends JpaRepository<LinkType, String> {

  List<LinkType> findByActiveTrueOrderBySortOrder();

  Optional<LinkType> findByCodeAndActiveTrue(String code);
}

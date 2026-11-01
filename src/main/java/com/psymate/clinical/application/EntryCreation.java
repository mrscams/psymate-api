package com.psymate.clinical.application;

import com.psymate.clinical.api.CreatedLink;
import java.util.List;
import java.util.UUID;

/** Entrée créée avec les liens posés dans la même transaction (vide si aucun lien demandé). */
public record EntryCreation(UUID id, List<CreatedLink> links) {}

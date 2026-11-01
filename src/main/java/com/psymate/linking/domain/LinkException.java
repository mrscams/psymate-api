package com.psymate.linking.domain;

import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.UUID;

/**
 * Erreur métier du module linking. Selon le code, elle porte le lien existant (LINK_EXISTS) ou les
 * types de liens autorisés pour la paire d'entrées (LINK_TYPE_NOT_ALLOWED).
 */
public class LinkException extends RuntimeException {

  private final LinkError error;
  private final UUID existingLinkId;
  private final transient List<LinkTypeView> allowedTypes;
  private final String field;

  public LinkException(LinkError error, String message) {
    this(error, message, null, List.of(), null);
  }

  private LinkException(
      LinkError error,
      String message,
      UUID existingLinkId,
      List<LinkTypeView> allowedTypes,
      String field) {
    super(message);
    this.error = error;
    this.existingLinkId = existingLinkId;
    this.allowedTypes = List.copyOf(allowedTypes);
    this.field = field;
  }

  /** Doublon actif : l'identifiant du lien déjà posé est rendu au client. */
  public static LinkException exists(UUID existingLinkId) {
    return new LinkException(
        LinkError.LINK_EXISTS, "Ce lien existe déjà", existingLinkId, List.of(), null);
  }

  /** Type interdit pour ce couple de types d'entrées ; allowed liste ceux qui conviennent. */
  public static LinkException notAllowed(
      String linkType, String sourceType, String targetType, List<LinkTypeView> allowed) {
    return new LinkException(
        LinkError.LINK_TYPE_NOT_ALLOWED,
        "Le type de lien "
            + linkType
            + " n'est pas autorisé entre une entrée "
            + sourceType
            + " et une entrée "
            + targetType,
        null,
        allowed,
        null);
  }

  public LinkError error() {
    return error;
  }

  /** Lien déjà existant (LINK_EXISTS), nul sinon. */
  public UUID existingLinkId() {
    return existingLinkId;
  }

  /** Types autorisés pour la paire (LINK_TYPE_NOT_ALLOWED), vide sinon. */
  public List<LinkTypeView> allowedTypes() {
    return allowedTypes;
  }

  /**
   * Une extrémité du lien est archivée (A7 : une entrée archivée n'a que des liens superseded).
   *
   * @param field sourceId ou targetId, selon l'extrémité du lien
   */
  public static LinkException archived(String field) {
    return new LinkException(LinkError.ENTRY_ARCHIVED, "Entrée archivée", null, List.of(), field);
  }

  /**
   * Erreur portée par un champ de la requête (errors[] d'un 422, ex. DIFFERENT_PATIENT sur {@code
   * root}).
   */
  public static LinkException atField(LinkError error, String field, String message) {
    return new LinkException(error, message, null, List.of(), field);
  }

  /** Champ fautif pour errors[] (ENTRY_ARCHIVED, ou erreur portée par un champ), nul sinon. */
  public String field() {
    return field;
  }
}

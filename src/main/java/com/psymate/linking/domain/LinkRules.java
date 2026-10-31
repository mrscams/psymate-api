package com.psymate.linking.domain;

import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.UUID;

/**
 * Règles pures de validation d'un lien (M2-01, E3.7) : pas d'auto-lien, type connu, couple (type de
 * la source, type de la cible) autorisé par la matrice {@code link_type.allowed_*} (G Q12). Sans
 * accès base ni contexte de sécurité : la propriété et le patient sont vérifiés par LinkService.
 */
public final class LinkRules {

  private LinkRules() {}

  /** Une entrée ne se lie pas à elle-même (contrainte ck_link_not_self). */
  public static void requireDistinct(UUID sourceId, UUID targetId) {
    if (sourceId.equals(targetId)) {
      throw new LinkException(LinkError.SELF_LINK, "Une entrée ne peut pas être liée à elle-même");
    }
  }

  /** La matrice autorise ce type pour cette source et cette cible. */
  public static boolean allows(LinkTypeView type, String sourceType, String targetType) {
    return type.allowedSourceTypes().contains(sourceType)
        && type.allowedTargetTypes().contains(targetType);
  }

  /** Les types de liens actifs autorisés pour ce couple, dans l'ordre du catalogue. */
  public static List<LinkTypeView> allowedTypes(
      List<LinkTypeView> catalog, String sourceType, String targetType) {
    return catalog.stream().filter(t -> allows(t, sourceType, targetType)).toList();
  }

  /**
   * Valide un lien source vers cible du type donné.
   *
   * @param catalog types de liens actifs, pour proposer les types possibles en cas de refus
   * @return le type de lien du catalogue
   * @throws LinkException SELF_LINK, UNKNOWN_LINK_TYPE ou LINK_TYPE_NOT_ALLOWED
   */
  public static LinkTypeView validate(
      UUID sourceId,
      UUID targetId,
      String sourceType,
      String targetType,
      String linkType,
      List<LinkTypeView> catalog) {
    requireDistinct(sourceId, targetId);
    LinkTypeView type =
        catalog.stream()
            .filter(t -> t.code().equals(linkType))
            .findFirst()
            .orElseThrow(
                () ->
                    new LinkException(
                        LinkError.UNKNOWN_LINK_TYPE,
                        "Type de lien inconnu ou inactif : " + linkType));
    if (!allows(type, sourceType, targetType)) {
      throw LinkException.notAllowed(
          linkType, sourceType, targetType, allowedTypes(catalog, sourceType, targetType));
    }
    return type;
  }
}

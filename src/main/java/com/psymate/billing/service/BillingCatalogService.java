package com.psymate.billing.service;

import com.psymate.billing.dto.BillingServiceDto;
import com.psymate.billing.dto.BillingServiceMapper;
import com.psymate.billing.dto.BillingServicesDto;
import com.psymate.billing.dto.CreateBillingServiceRequest;
import com.psymate.billing.dto.UpdateBillingServiceRequest;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingService;
import com.psymate.billing.repository.BillingServiceRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.problem.ProblemException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prestations du praticien (M15-05, L5.1) : lister, créer, modifier, archiver. Le praticien et le
 * cabinet viennent de la session, jamais du corps ; la prestation d'un autre praticien est
 * introuvable (404).
 *
 * <p>Règles du défaut : la première prestation créée devient la prestation par défaut ; il y en a
 * au plus une ({@code billing_service_one_default}) et, tant qu'il reste une prestation non
 * archivée, exactement une ; en désigner une autre retire le défaut de la précédente ; la
 * prestation par défaut ne s'archive pas tant qu'il en reste d'autres (409 SERVICE_IS_DEFAULT) ; 50
 * prestations non archivées au plus (422 SERVICE_LIMIT_REACHED). Les écritures d'un même praticien
 * passent par son verrou consultatif ({@link BillingServiceRepository#lockServices}) : limite et
 * défaut unique tiennent en parallèle. Rien n'est supprimé : une prestation s'archive. Aucun
 * libellé ni montant n'est journalisé.
 */
@Service
public class BillingCatalogService {

  private final BillingServiceRepository services;
  private final BillingServiceMapper mapper;
  private final Clock clock;

  public BillingCatalogService(
      BillingServiceRepository services, BillingServiceMapper mapper, Clock clock) {
    this.services = services;
    this.mapper = mapper;
    this.clock = clock;
  }

  // ---- règles pures (testées unitairement)

  /** Une nouvelle prestation est par défaut si on le demande, ou si c'est la seule. */
  static boolean createdAsDefault(Boolean requested, int activeCount) {
    return Boolean.TRUE.equals(requested) || activeCount == 0;
  }

  /** Une prestation de plus est permise tant que le praticien en a moins de 50 actives. */
  static boolean belowLimit(int activeCount) {
    return activeCount < BillingException.MAX_SERVICES;
  }

  /**
   * La prestation par défaut ne s'archive que si elle est la dernière active ; toute autre
   * s'archive librement.
   */
  static boolean archivable(boolean isDefault, int activeCount) {
    return !isDefault || activeCount <= 1;
  }

  /**
   * {@code isDefault: false} n'est admis que sur une prestation qui n'est pas par défaut (sans
   * effet) : la prestation par défaut ne perd son rôle qu'au profit d'une autre.
   */
  static boolean defaultFlagAccepted(boolean currentlyDefault, Boolean requested) {
    return requested == null || requested || !currentlyDefault;
  }

  // ---- lecture

  @Transactional(readOnly = true)
  public BillingServicesDto list() {
    return new BillingServicesDto(mapper.toDtos(services.findActive(me().id())));
  }

  // ---- écriture

  @Transactional
  public BillingServiceDto create(CreateBillingServiceRequest request) {
    CurrentPractitioner me = me();
    services.lockServices(me.id());
    List<BillingService> active = services.findActive(me.id());
    if (!belowLimit(active.size())) {
      throw BillingException.serviceLimitReached();
    }
    OffsetDateTime now = now();
    boolean isDefault = createdAsDefault(request.isDefault(), active.size());
    if (isDefault) {
      clearDefault(active, null, now);
    }
    BillingService created =
        new BillingService(
            UUID.randomUUID(),
            me.tenantId(),
            me.id(),
            request.label().strip(),
            request.durationMin(),
            request.price(),
            isDefault,
            now);
    return mapper.toDto(services.save(created));
  }

  @Transactional
  public BillingServiceDto update(UUID id, UpdateBillingServiceRequest request) {
    CurrentPractitioner me = me();
    services.lockServices(me.id());
    BillingService service = active(id, me);
    if (!defaultFlagAccepted(service.isDefault(), request.getIsDefault())) {
      throw BillingException.invalidField("isDefault", "INVALID_VALUE");
    }
    OffsetDateTime now = now();
    if (request.getLabel() != null) {
      service.rename(request.getLabel().strip(), now);
    }
    if (request.durationMinProvided()) {
      service.changeDuration(request.getDurationMin(), now);
    }
    if (request.getPrice() != null) {
      service.changePrice(request.getPrice(), now);
    }
    if (Boolean.TRUE.equals(request.getIsDefault()) && !service.isDefault()) {
      clearDefault(services.findActive(me.id()), service.getId(), now);
      service.markDefault(true, now);
    }
    return mapper.toDto(services.saveAndFlush(service));
  }

  /**
   * Archive la prestation (204). Idempotent : une prestation déjà archivée du praticien répond 204
   * sans rien changer.
   */
  @Transactional
  public void archive(UUID id) {
    CurrentPractitioner me = me();
    services.lockServices(me.id());
    BillingService service =
        services
            .findByIdAndPractitionerId(id, me.id())
            .orElseThrow(BillingException::serviceNotFound);
    if (service.isArchived()) {
      return;
    }
    if (!archivable(service.isDefault(), services.findActive(me.id()).size())) {
      throw BillingException.serviceIsDefault();
    }
    service.archive(now());
  }

  // ---- outils

  /**
   * Retire le défaut de la prestation qui l'a (sauf {@code keep}), puis écrit tout de suite :
   * l'index {@code billing_service_one_default} n'admet jamais deux défauts, et Hibernate écrirait
   * sinon l'insertion ou la mise à jour du nouveau défaut avant celle-ci.
   */
  private void clearDefault(List<BillingService> active, UUID keep, OffsetDateTime now) {
    boolean changed = false;
    for (BillingService s : active) {
      if (s.isDefault() && !s.getId().equals(keep)) {
        s.markDefault(false, now);
        changed = true;
      }
    }
    if (changed) {
      services.flush();
    }
  }

  private BillingService active(UUID id, CurrentPractitioner me) {
    return services
        .findByIdAndPractitionerId(id, me.id())
        .filter(s -> !s.isArchived())
        .orElseThrow(BillingException::serviceNotFound);
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}

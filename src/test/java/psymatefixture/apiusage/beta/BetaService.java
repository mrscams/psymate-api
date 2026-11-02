package psymatefixture.apiusage.beta;

import psymatefixture.apiusage.alpha.api.StaleReservation;
import psymatefixture.apiusage.alpha.api.UsedFacade;

/** Consommateur : appelle UsedFacade sans nommer UsedView, et utilise StaleReservation. */
public class BetaService {
  private final UsedFacade facade;

  BetaService(UsedFacade facade) {
    this.facade = facade;
  }

  boolean known(String id) {
    return facade.find(id) != null && new StaleReservation(id).id() != null;
  }
}

package psymatefixture.apiusage.alpha;

import psymatefixture.apiusage.alpha.api.InternalOnly;
import psymatefixture.apiusage.alpha.api.UsedFacade;
import psymatefixture.apiusage.alpha.api.UsedView;

/** Implémentation interne : ses dépendances vers api ne comptent pas. */
public class AlphaService implements UsedFacade {
  @Override
  public UsedView find(String id) {
    return new UsedView(new InternalOnly(id).id());
  }
}

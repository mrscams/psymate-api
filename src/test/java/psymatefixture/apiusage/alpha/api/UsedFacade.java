package psymatefixture.apiusage.alpha.api;

/** Correct : lue par le module beta. */
public interface UsedFacade {
  UsedView find(String id);
}

package psymatefixture.badroles.shop.dto;

/** Violation (G Q35) : conteneur de DTO, record public imbrique. */
public final class ShopDtos {
  private ShopDtos() {}

  public record NestedRequest(String name) {}
}

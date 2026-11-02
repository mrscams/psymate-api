package psymatefixture.badroles.shop.api;

/** Correct : un type d'api (parametre d'un port) garde son nom, comme clinical.api.LinkRequest. */
public record ShopOrderRequest(String item) {}

package dev.onepieceapi.contentservice.web.dto.response;

/**
 * An alias of a Character.
 *
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record AliasResponse(String alias, FirstAppearanceResponse discovered, FirstAppearanceResponse ended) {

}

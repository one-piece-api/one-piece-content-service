package dev.onepieceapi.contentservice.web.dto.response;

/**
 * A Devil Fruit of a Character: former once its window has an end, derived.
 *
 * @param devilFruit the fruit as of today
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns the power is lost; null for none
 */
public record FruitUseResponse(DevilFruitReferenceResponse devilFruit, FirstAppearanceResponse discovered,
		FirstAppearanceResponse ended) {

}

package dev.onepieceapi.contentservice.web.dto.response;

/**
 * A Devil Fruit of a Character.
 *
 * @param devilFruit the fruit as of today
 */
public record FruitUseResponse(DevilFruitReferenceResponse devilFruit, boolean former) {

}

package dev.onepieceapi.contentservice.domain.character;

import java.util.UUID;

/**
 * A Devil Fruit a character ate, as of today (implementation plan of the Character, D12).
 * Part of the character's version, in the order it is shown.
 *
 * @param devilFruitContentId the content id of the fruit
 * @param former whether the character no longer has its power
 */
public record FruitUse(UUID devilFruitContentId, boolean former) {

}

package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.UUID;

/**
 * A Devil Fruit Type and one of its subcategories, as a fruit or one of its knowledge
 * phases says them together (ADR-0010).
 *
 * @param typeContentId the content of the type; null while not chosen
 * @param subcategoryId one of its subcategories; null for none
 * @param phase the position of the phase saying them; null for the fruit itself
 */
public record DevilFruitClassification(UUID typeContentId, UUID subcategoryId, Integer phase) {

}

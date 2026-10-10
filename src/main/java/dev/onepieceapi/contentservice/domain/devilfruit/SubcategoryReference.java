package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Map;
import java.util.UUID;

/**
 * A subcategory of a Devil Fruit Type as a fruit points to it (implementation plan of the
 * subcategories, S5): its id, and what to call it as the type was last approved.
 *
 * @param names the name per language code, for the languages that have one
 */
public record SubcategoryReference(UUID id, Map<String, String> names) {

}

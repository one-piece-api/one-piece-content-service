package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;
import java.util.UUID;

/**
 * A subcategory of a Devil Fruit Type as a fruit points to it: as the type was last
 * approved.
 *
 * @param names the name per language code, for the languages that have one; empty when
 * the type, as last approved, no longer has it
 */
public record SubcategoryReferenceResponse(UUID id, Map<String, String> names) {

}

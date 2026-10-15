package dev.onepieceapi.contentservice.domain.devilfruit;

import java.util.Map;
import java.util.UUID;

/**
 * A Devil Fruit as another content points to it - a participant of an event
 * (implementation plan of the timeline, T5): its content id, romaji and names as it was
 * last approved, never a copy kept in the pointing version.
 *
 * @param names the name per language code, for the languages that have one
 */
public record DevilFruitReference(UUID id, String romaji, Map<String, String> names) {

}

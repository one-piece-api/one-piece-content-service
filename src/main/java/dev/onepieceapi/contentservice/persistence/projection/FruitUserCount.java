package dev.onepieceapi.contentservice.persistence.projection;

import java.util.UUID;

/** How many characters online currently use a Devil Fruit. */
public record FruitUserCount(UUID devilFruitContentId, long count) {

}

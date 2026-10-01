package dev.onepieceapi.contentservice.domain.devilfruittype;

import java.util.Map;

/**
 * What one version of a Devil Fruit Type says
 * (docs/user-flows/content-editorial-workflow.md 3.1): one romaji shared by every
 * language, and a name and description per language code. Plugged into the generic
 * {@code Content} and {@code Version}, which carry the workflow around it.
 */
public record DevilFruitType(String romaji, Map<String, DevilFruitTypeTranslation> translations) {

}

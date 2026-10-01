package dev.onepieceapi.contentservice.domain.devilfruittype;

/**
 * The localized fields of a Devil Fruit Type in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1). Either may still be missing while
 * the version is a draft.
 */
public record DevilFruitTypeTranslation(String name, String description) {

}

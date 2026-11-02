package dev.onepieceapi.contentservice.domain.devilfruit;

import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The fields of a Devil Fruit a reveal may name
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): its name, its romaji, its type
 * with its subcategory - revealed together.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DevilFruitField implements RevealableField {

	NAME("name", true, "translations"), ROMAJI("romaji", true, "romaji"), TYPE("type", false, "type");

	private final String fieldName;

	private final boolean alwaysKnown;

	private final String keyField;

}

package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The fields of a Character a reveal may name
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): its identity - name, romaji and
 * Japanese name, revealed together - and its status.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CharacterField implements RevealableField {

	IDENTITY("identity", true, "romaji"), STATUS("status", false, "status");

	private final String fieldName;

	private final boolean alwaysKnown;

	private final String keyField;

}

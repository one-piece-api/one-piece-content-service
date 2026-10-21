package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The localized fields of a Species in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.10). Either may still be missing
 * while the version is a draft.
 */
public record SpeciesTranslation(String name, String description) {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final SpeciesTranslation NONE = new SpeciesTranslation(null, null);

	SpeciesTranslation normalized() {
		return new SpeciesTranslation(Text.stripToNull(this.name), Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Stream.of(this.name, this.description).allMatch(Objects::isNull);
	}

}

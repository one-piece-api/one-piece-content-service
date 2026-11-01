package dev.onepieceapi.contentservice.domain.species;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a Species in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.10): its name, which may still be
 * missing while the version is a draft. The description is a list of paragraphs (3.7.3).
 */
public record SpeciesTranslation(String name) {

	public static final int NAME_MAX_LENGTH = 100;

	/** A language nothing was written in. */
	static final SpeciesTranslation NONE = new SpeciesTranslation(null);

	SpeciesTranslation normalized() {
		return new SpeciesTranslation(Text.stripToNull(this.name));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null;
	}

}

package dev.onepieceapi.contentservice.domain.affiliation;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The localized fields of an Affiliation in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.9). Either may still be missing
 * while the version is a draft.
 */
public record AffiliationTranslation(String name, String description) {

	public static final int NAME_MAX_LENGTH = 100;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final AffiliationTranslation NONE = new AffiliationTranslation(null, null);

	AffiliationTranslation normalized() {
		return new AffiliationTranslation(Text.stripToNull(this.name), Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Stream.of(this.name, this.description).allMatch(Objects::isNull);
	}

}

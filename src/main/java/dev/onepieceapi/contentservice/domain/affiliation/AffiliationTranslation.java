package dev.onepieceapi.contentservice.domain.affiliation;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of an Affiliation in one language
 * (docs/user-flows/content-editorial-workflow.md 3.1.9): its name, which may still be
 * missing while the version is a draft. The description is a list of paragraphs (3.7.3).
 */
public record AffiliationTranslation(String name) {

	public static final int NAME_MAX_LENGTH = 100;

	/** A language nothing was written in. */
	static final AffiliationTranslation NONE = new AffiliationTranslation(null);

	AffiliationTranslation normalized() {
		return new AffiliationTranslation(Text.stripToNull(this.name));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return this.name == null;
	}

}

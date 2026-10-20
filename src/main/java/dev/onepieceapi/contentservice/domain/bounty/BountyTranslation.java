package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;

/**
 * The localized fields of a bounty in one language (implementation plan of the Bounty,
 * D5): why it was issued, raised or made inactive. It may still be missing while the
 * version is a draft.
 */
public record BountyTranslation(String description) {

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final BountyTranslation NONE = new BountyTranslation(null);

	BountyTranslation normalized() {
		return new BountyTranslation(Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Objects.isNull(this.description);
	}

}

package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a volume in one language: its title. A volume has no description
 * (implementation plan of the story structure, section 3). May still be missing while the
 * version is a draft.
 */
public record VolumeTranslation(String title) {

	VolumeTranslation normalized() {
		return new VolumeTranslation(Text.stripToNull(this.title));
	}

	boolean isEmpty() {
		return this.title == null;
	}

}

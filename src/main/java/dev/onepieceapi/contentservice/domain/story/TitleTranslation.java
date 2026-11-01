package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.Text;

/**
 * The localized field of a volume, a saga or an arc in one language: its title. A volume
 * has no description; those of a saga and an arc are paragraphs (workflow 3.7.3). May
 * still be missing while the version is a draft.
 */
public record TitleTranslation(String title) {

	TitleTranslation normalized() {
		return new TitleTranslation(Text.stripToNull(this.title));
	}

	boolean isEmpty() {
		return this.title == null;
	}

}

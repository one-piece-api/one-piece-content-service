package dev.onepieceapi.contentservice.domain.story;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The localized fields of a story entity in one language - a saga, an arc (implementation
 * plan of the story structure, section 3). Either may still be missing while the version
 * is a draft.
 */
public record StoryTranslation(String title, String description) {

	public static final int TITLE_MAX_LENGTH = 200;

	public static final int DESCRIPTION_MAX_LENGTH = 2000;

	/** A language nothing was written in. */
	static final StoryTranslation NONE = new StoryTranslation(null, null);

	StoryTranslation normalized() {
		return new StoryTranslation(Text.stripToNull(this.title), Text.stripToNull(this.description));
	}

	/** Nothing written in this language. */
	boolean isEmpty() {
		return Stream.of(this.title, this.description).allMatch(Objects::isNull);
	}

}

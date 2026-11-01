package dev.onepieceapi.contentservice.domain.readertime;

/**
 * Anything shown to the reader inside a {@link StoryWindow}
 * (docs/user-flows/content-editorial-workflow.md 3.7.2): an entry of a character's list,
 * a period of a membership.
 */
public interface Windowed {

	StoryWindow window();

}

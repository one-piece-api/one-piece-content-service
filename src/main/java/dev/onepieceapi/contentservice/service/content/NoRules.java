package dev.onepieceapi.contentservice.service.content;

/** The rules of an entity that has none: every default of {@link ContentRules}. */
public final class NoRules implements ContentRules<Object> {

	static final NoRules INSTANCE = new NoRules();

	private NoRules() {
	}

}

package dev.onepieceapi.contentservice.service;

/** The rules of an entity that has none: every default of {@link ContentRules}. */
final class NoRules implements ContentRules<Object> {

	static final NoRules INSTANCE = new NoRules();

	private NoRules() {
	}

}

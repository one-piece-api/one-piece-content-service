package dev.onepieceapi.contentservice.domain.readertime;

/**
 * A field, or fields revealed together, that a reveal may name
 * (docs/user-flows/content-editorial-workflow.md 3.7.1): the name of a Devil Fruit, the
 * identity of a Character. Implemented by an enum per entity.
 */
public interface RevealableField {

	/** As a request names it in {@code fields}, e.g. {@code identity}. */
	String fieldName();

	/**
	 * Whether the initial knowledge must hold it as soon as a reveal names it: a name or
	 * a romaji, so the item is never nameless nor without a slug.
	 */
	boolean alwaysKnown();

	/**
	 * The value the field cannot do without when it is given, as a request names it, e.g.
	 * {@code romaji} for the identity, {@code type} for the type and its subcategory.
	 */
	String keyField();

}

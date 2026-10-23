package dev.onepieceapi.contentservice.domain.character;

/**
 * What a relative is to a character, read as "the relative is the <kind> of the
 * character" (implementation plan of the Character, D13). A link is stored once, by one
 * of the two characters; the other side reads it through {@link #inverse()}.
 */
public enum RelativeKind {

	PARENT, CHILD, GRANDPARENT, GRANDCHILD, UNCLE_AUNT, NEPHEW_NIECE, ADOPTIVE_PARENT, ADOPTIVE_CHILD,

	/** The same both ways. */
	SIBLING, COUSIN, SPOUSE, SWORN_SIBLING;

	/** What the character is to its relative: the kind read from the other side. */
	public RelativeKind inverse() {
		return switch (this) {
			case PARENT -> CHILD;
			case CHILD -> PARENT;
			case GRANDPARENT -> GRANDCHILD;
			case GRANDCHILD -> GRANDPARENT;
			case UNCLE_AUNT -> NEPHEW_NIECE;
			case NEPHEW_NIECE -> UNCLE_AUNT;
			case ADOPTIVE_PARENT -> ADOPTIVE_CHILD;
			case ADOPTIVE_CHILD -> ADOPTIVE_PARENT;
			case SIBLING, COUSIN, SPOUSE, SWORN_SIBLING -> this;
		};
	}

}

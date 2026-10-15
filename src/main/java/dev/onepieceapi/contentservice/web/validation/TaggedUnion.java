package dev.onepieceapi.contentservice.web.validation;

import java.util.List;

/**
 * A request object that is a tagged union (see {@link OnePart}): one property per
 * variant, at most one of them written.
 */
public interface TaggedUnion {

	/** The name of each variant, as a request names it. */
	List<String> partNames();

	/** How many variants are written. */
	long writtenParts();

}

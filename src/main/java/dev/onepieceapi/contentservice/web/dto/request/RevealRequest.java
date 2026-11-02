package dev.onepieceapi.contentservice.web.dto.request;

import java.util.Map;
import java.util.Set;

/**
 * What every reveal says, whatever its entity (workflow 3.7.1): where the reader learns,
 * the fields learned there and a short title per language. A field named with a value is
 * learned there; named without one, the reveal reveals the current value.
 *
 * @param <F> the fields of the entity a reveal may name
 */
public interface RevealRequest<F> {

	/** Where the reader learns; left out while not chosen - it is required to save. */
	FirstAppearanceRequest revealedAt();

	/** The title per language code; a language left out, or blank, is not written yet. */
	Map<String, String> titles();

	/** The fields learned there; at least one. */
	Set<F> fields();

}

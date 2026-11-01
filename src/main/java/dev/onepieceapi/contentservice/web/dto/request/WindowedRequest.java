package dev.onepieceapi.contentservice.web.dto.request;

/**
 * An entry saved with a window of the reader axis
 * (docs/user-flows/content-editorial-workflow.md 3.7.2): its points sit on the entry
 * itself, as the fields of an error name them ({@code species[1].discovered.chapter}).
 */
public interface WindowedRequest {

	/**
	 * Where the reader learns it; left out while not chosen - it is required to submit.
	 */
	FirstAppearanceRequest discovered();

	/** Where the reader learns it is over; left out for none. */
	FirstAppearanceRequest ended();

}

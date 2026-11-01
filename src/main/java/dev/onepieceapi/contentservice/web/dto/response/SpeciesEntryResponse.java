package dev.onepieceapi.contentservice.web.dto.response;

/**
 * A species of a Character.
 *
 * @param species the species as of today
 * @param discovered where the reader learns it; null while not chosen
 * @param ended where the reader learns it is over; null for none
 */
public record SpeciesEntryResponse(SpeciesReferenceResponse species, FirstAppearanceResponse discovered,
		FirstAppearanceResponse ended) {

}

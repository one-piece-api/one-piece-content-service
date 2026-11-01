package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * A species of a Character, as a draft is saved.
 *
 * @param species the content id of the species
 * @param discovered as {@link WindowedRequest#discovered()}
 * @param ended as {@link WindowedRequest#ended()}
 */
public record SpeciesEntryRequest(@NotNull @Schema(description = "The content id of a Species") UUID species,
		FirstAppearanceRequest discovered, FirstAppearanceRequest ended) implements WindowedRequest {

}

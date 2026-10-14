package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The day something came out in one country, as a draft is saved - a release of a volume
 * or a chapter, a broadcast of an episode. At most one per country; whether the country
 * is a known one is for the service to say.
 *
 * @param country ISO 3166-1 alpha-2 code, e.g. {@code JP}
 * @param date the calendar day in that country
 */
public record ReleaseRequest(@NotBlank @Schema(example = "JP") String country,
		@NotNull @Schema(example = "1997-12-24") LocalDate date) {

}

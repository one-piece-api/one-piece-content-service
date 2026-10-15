package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.web.validation.YearSpan;
import dev.onepieceapi.contentservice.web.validation.YearsInOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * A year, or a span of years, of the One Piece calendar, before the story starts
 * (implementation plan of the timeline, T4). Whether it ends by the year the story starts
 * is for the service to say.
 *
 * @param yearFrom the first year, required
 * @param yearTo the last year, not before the first; left out for one year
 * @param approximate whether the years are not known for sure; false when left out
 */
@YearsInOrder
public record WorldDateRequest(@NotNull Integer yearFrom, Integer yearTo,
		@Schema(description = "Whether the years are not known for sure; false when left out") Boolean approximate)
		implements
			YearSpan {

}

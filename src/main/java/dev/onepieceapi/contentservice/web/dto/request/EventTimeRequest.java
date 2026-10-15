package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.web.validation.OnePart;
import dev.onepieceapi.contentservice.web.validation.TaggedUnion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * When an event happens or ends (implementation plan of the timeline, T3): exactly one of
 * a chapter - in the story - or a world date - before it.
 *
 * @param chapter the id of the content of the chapter; whether it can be chosen is
 * answered by {@code GET /chapters/linkable}
 * @param worldDate the year, or span of years, of the world calendar
 */
@OnePart
@Schema(description = "Exactly one of chapter or worldDate")
public record EventTimeRequest(@Schema(description = "The content id of a chapter") UUID chapter,
		@Valid WorldDateRequest worldDate) implements TaggedUnion {

	@Override
	public List<String> partNames() {
		return List.of("chapter", "worldDate");
	}

	@Override
	public long writtenParts() {
		return Stream.of(this.chapter, this.worldDate).filter(Objects::nonNull).count();
	}

}

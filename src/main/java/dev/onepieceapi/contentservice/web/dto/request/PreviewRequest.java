package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.web.validation.OneReaderAxis;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;

/**
 * Where the reader of a preview is (reveals plan R6, D1): at chapter N or at episode N,
 * exactly one of the two.
 */
@OneReaderAxis
public record PreviewRequest(@Parameter(description = PreviewRequest.CHAPTER) @Positive Integer asOfChapter,
		@Parameter(description = PreviewRequest.EPISODE) @Positive Integer asOfEpisode) {

	static final String CHAPTER = "The chapter the reader is at, 1 or more; not with asOfEpisode";

	static final String EPISODE = "The episode the reader is at, 1 or more; not with asOfChapter";

}

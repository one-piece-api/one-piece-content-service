package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * The query parameters of the list of contents keyed by a number that may be linked to,
 * e.g. the sagas an arc may belong to. All optional; the limits are those of
 * {@link LinkableTypesRequest}.
 *
 * @param page 0-based, 0 when missing
 * @param size the page size, 20 when missing
 * @param q what the number or a title contains
 */
public record LinkableNumberedRequest(@Parameter(description = "0-based page, 0 when missing") @Min(0) Integer page,
		@Parameter(
				description = "Page size: 20 when missing, at most 50") @Min(1) @Max(LinkableTypesRequest.MAX_SIZE) Integer size,
		@Parameter(description = "Text the number or a title contains, case-insensitive; 100 characters at most") @Size(
				max = LinkableTypesRequest.MAX_QUERY_LENGTH) String q) {

	public int pageOrDefault() {
		return this.page == null ? 0 : this.page;
	}

	public int sizeOrDefault() {
		return this.size == null ? LinkableTypesRequest.DEFAULT_SIZE : this.size;
	}

}

package dev.onepieceapi.contentservice.web.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * The query parameters of the list of Devil Fruit Types a fruit may be linked to. All
 * optional.
 *
 * @param page 0-based, 0 when missing
 * @param size the page size, {@value #DEFAULT_SIZE} when missing
 * @param q what the romaji or a name contains
 */
public record LinkableTypesRequest(@Parameter(description = "0-based page, 0 when missing") @Min(0) Integer page,
		@Parameter(description = "Page size: 20 when missing, at most 50") @Min(1) @Max(MAX_SIZE) Integer size,
		@Parameter(description = "Text the romaji or a name contains, case-insensitive; 100 characters at most") @Size(
				max = LinkableTypesRequest.MAX_QUERY_LENGTH) String q) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 50;

	public static final int MAX_QUERY_LENGTH = 100;

	public int pageOrDefault() {
		return this.page == null ? 0 : this.page;
	}

	public int sizeOrDefault() {
		return this.size == null ? DEFAULT_SIZE : this.size;
	}

}

package dev.onepieceapi.contentservice.web.dto.response;

import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Minimal page envelope, the same one {@code one-piece-user-service} returns -
 * deliberately not the {@link Page} of Spring Data itself, whose default JSON shape is
 * not a stable contract.
 */
@Builder
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return PageResponse.<T>builder()
			.content(page.getContent())
			.page(page.getNumber())
			.size(page.getSize())
			.totalElements(page.getTotalElements())
			.totalPages(page.getTotalPages())
			.build();
	}

}

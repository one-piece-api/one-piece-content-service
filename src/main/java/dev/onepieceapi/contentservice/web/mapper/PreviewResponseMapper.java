package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.web.dto.response.HiddenEntryResponse;
import dev.onepieceapi.contentservice.web.dto.response.PreviewResponse;
import dev.onepieceapi.contentservice.web.dto.response.ReaderPositionResponse;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.experimental.UtilityClass;

/** A preview at N into its answer (UF-CNT-23). */
@UtilityClass
public class PreviewResponseMapper {

	/**
	 * @param body the resolved body in the section's shape; null when not visible
	 */
	public <T, R> PreviewResponse<R> toResponse(Preview<T> preview, R body, ImageResponseMapper images) {
		ReaderPosition position = preview.position();
		String portrait = preview.mainPortraitImageId();
		return new PreviewResponse<>(new ReaderPositionResponse(position.axis(), position.number()), preview.visible(),
				body, (portrait == null) ? null : images.toResponse(portrait),
				preview.hiddenEntries().stream().map(PreviewResponseMapper::toResponse).toList());
	}

	private HiddenEntryResponse toResponse(HiddenEntry entry) {
		return new HiddenEntryResponse(entry.field(), entry.reason(), entry.visibleFrom());
	}

}

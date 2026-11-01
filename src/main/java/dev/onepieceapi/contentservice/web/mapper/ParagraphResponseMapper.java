package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.web.dto.response.ParagraphResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The paragraphs of a section as a response shows them (workflow 3.7.3). */
@UtilityClass
public class ParagraphResponseMapper {

	/**
	 * In their order, each text sorted by language code.
	 * @param storyItems the chapters and the episodes by content id
	 */
	public List<ParagraphResponse> toResponse(List<Paragraph> paragraphs, Map<UUID, NumberedReference> storyItems) {
		return paragraphs.stream().map(paragraph -> toResponse(paragraph, storyItems)).toList();
	}

	private static ParagraphResponse toResponse(Paragraph paragraph, Map<UUID, NumberedReference> storyItems) {
		return new ParagraphResponse(new TreeMap<>(paragraph.text()),
				FirstAppearanceMapper.toResponse(paragraph.window().discovered(), storyItems),
				FirstAppearanceMapper.toResponse(paragraph.window().ended(), storyItems));
	}

}

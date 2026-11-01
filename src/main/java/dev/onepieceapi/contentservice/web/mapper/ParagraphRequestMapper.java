package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.web.dto.request.ParagraphRequest;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The paragraphs of a section as a request sends them (workflow 3.7.3). */
@UtilityClass
public class ParagraphRequestMapper {

	/** In the order sent; a section left out has none. */
	public List<Paragraph> toDomain(List<ParagraphRequest> paragraphs) {
		return Optional.ofNullable(paragraphs)
			.orElseGet(List::of)
			.stream()
			.map(ParagraphRequestMapper::toDomain)
			.toList();
	}

	private static Paragraph toDomain(ParagraphRequest paragraph) {
		return new Paragraph(written(paragraph.text()), FirstAppearanceMapper.toWindow(paragraph));
	}

	/** A language sent as null is a language with nothing written. */
	private static Map<String, String> written(Map<String, String> texts) {
		Map<String, String> written = new HashMap<>();
		Optional.ofNullable(texts).orElseGet(Map::of).forEach((language, text) -> {
			if (text != null) {
				written.put(language, text);
			}
		});
		return written;
	}

}

package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.persistence.entity.ParagraphEntity;
import dev.onepieceapi.contentservice.persistence.entity.ParagraphFields;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The paragraphs of a version between the domain, a list per section, and its rows, one
 * list of every section (workflow 3.7.3): the sections follow one another in their
 * declared order, each in its own.
 */
@UtilityClass
public class ParagraphMapper {

	/**
	 * Every section in its declared order, each paragraph in the order of its section.
	 */
	public List<ParagraphFields> toFields(Map<ParagraphSection, List<Paragraph>> paragraphs) {
		return Arrays.stream(ParagraphSection.values())
			.flatMap(section -> fieldsOf(section, paragraphs.getOrDefault(section, List.of())))
			.toList();
	}

	/** Grouped by section in the order of the rows; a section with no row is empty. */
	public Map<ParagraphSection, List<Paragraph>> toDomain(List<? extends ParagraphEntity> rows) {
		Map<ParagraphSection, List<Paragraph>> bySection = new EnumMap<>(ParagraphSection.class);
		Arrays.stream(ParagraphSection.values()).forEach(section -> bySection.put(section, new ArrayList<>()));
		rows.forEach(row -> bySection.get(row.getSection()).add(toDomain(row)));
		return bySection;
	}

	/** The description of an entity that has no other section. */
	public List<Paragraph> descriptionOf(List<? extends ParagraphEntity> rows) {
		return toDomain(rows).get(ParagraphSection.DESCRIPTION);
	}

	private static Stream<ParagraphFields> fieldsOf(ParagraphSection section, List<Paragraph> paragraphs) {
		return paragraphs.stream()
			.map(paragraph -> new ParagraphFields(section, paragraph.text(),
					StoryWindowMapper.toEmbeddable(paragraph.window())));
	}

	private static Paragraph toDomain(ParagraphEntity row) {
		return new Paragraph(row.getText(), StoryWindowMapper.toDomain(row.getWindow()));
	}

}

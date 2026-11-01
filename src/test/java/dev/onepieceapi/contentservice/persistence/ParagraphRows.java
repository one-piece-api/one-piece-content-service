package dev.onepieceapi.contentservice.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Paragraph rows seeded straight into an entity's paragraph tables (V39). */
final class ParagraphRows {

	private ParagraphRows() {
	}

	/**
	 * One paragraph of a section, at a position in the version's list, with its text in
	 * each given language.
	 * @param entity the table prefix, such as {@code species} or {@code devil_fruit}
	 */
	static void paragraph(JdbcClient jdbc, String entity, UUID versionId, int position, String section,
			Map<String, String> texts) {
		UUID id = UUID.randomUUID();
		jdbc.sql("insert into " + entity + "_version_paragraph (id, version_id, position, section) values (?, ?, ?, ?)")
			.params(id, versionId, position, section)
			.update();
		texts.forEach((language, text) -> jdbc
			.sql("insert into " + entity
					+ "_version_paragraph_translation (paragraph_id, language_code, text) values (?, ?, ?)")
			.params(id, language, text)
			.update());
	}

	/** A single description paragraph saying "Description LANGUAGE" in each language. */
	static void description(JdbcClient jdbc, String entity, UUID versionId, String... languages) {
		Map<String, String> texts = new TreeMap<>();
		for (String language : languages) {
			texts.put(language, "Description " + language.toUpperCase());
		}
		paragraph(jdbc, entity, versionId, 0, "DESCRIPTION", texts);
	}

}

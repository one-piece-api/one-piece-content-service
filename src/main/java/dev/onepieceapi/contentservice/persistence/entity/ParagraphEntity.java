package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * A paragraph of one version (workflow 3.7.3; V39): a child of its version, saved and
 * removed only through it, one row per paragraph of every section in a single ordered
 * list - the order of a section is the order of its rows. Its row has an id of its own,
 * the parent of its texts, and its window on the reader axis. Each paragraphed entity has
 * its own tables: a subclass names them, its texts' table on a collection of its own -
 * JPA has no standard override for the table of an element collection inherited from a
 * mapped superclass.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ParagraphEntity {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	private ParagraphSection section;

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

	protected ParagraphEntity(UUID id) {
		this.id = id;
	}

	/** The text per language code; a language nothing was saved for is absent. */
	public abstract Map<String, String> getText();

	/** Replaces what the paragraph says, its section included. */
	public void rewrite(ParagraphFields written) {
		this.section = written.section();
		getText().clear();
		getText().putAll(written.text());
		this.window = written.window();
	}

}

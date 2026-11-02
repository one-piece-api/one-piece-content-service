package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A reveal of one version (workflow 3.7.1; V40): a child of its version, saved and
 * removed only through it, one row per reveal in list order - the position is the index
 * in the list. Its row has an id of its own, the parent of its fields and translations,
 * and its point on the reader axis. Each revealing entity has its own tables: a subclass
 * names them and holds the entity's values, as {@link ParagraphEntity} does.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class RevealEntity {

	@Id
	private UUID id;

	/** The content of the chapter of its point; null for none. */
	private UUID revealedChapterId;

	/** The content of the episode of its point; null for none. */
	private UUID revealedEpisodeId;

	protected RevealEntity(UUID id) {
		this.id = id;
	}

	protected void rewritePoint(FirstAppearanceIds point) {
		this.revealedChapterId = point.chapterId();
		this.revealedEpisodeId = point.episodeId();
	}

}

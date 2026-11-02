package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.readertime.ImageKind;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An image of the gallery of one version of a Character (workflow 3.5.1; V41): a child of
 * {@link CharacterVersionEntity}, saved and removed only through it. Its row has an id of
 * its own, the parent of its captions, the stored image it shows, its kind and its window
 * on the reader axis.
 */
@Entity
@Table(name = "character_version_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterImageEntity {

	@Id
	private UUID id;

	private String imageId;

	@Enumerated(EnumType.STRING)
	private ImageKind kind;

	/** The caption per language code; a language nothing was saved for is absent. */
	@ElementCollection
	@CollectionTable(name = "character_version_image_translation", joinColumns = @JoinColumn(name = "gallery_image_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "caption")
	private Map<String, String> caption = new HashMap<>();

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

	public CharacterImageEntity(GalleryImageFields written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the image row says. */
	public void rewrite(GalleryImageFields written) {
		this.imageId = written.imageId();
		this.kind = written.kind();
		this.caption.clear();
		this.caption.putAll(written.caption());
		this.window = written.window();
	}

}

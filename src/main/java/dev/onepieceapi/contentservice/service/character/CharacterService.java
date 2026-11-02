package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.image.GalleryUploads;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.content.ContentService;
import dev.onepieceapi.contentservice.service.image.GalleryImages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Characters and their versions: the whole workflow of {@link ContentService}, run with
 * the {@link CharacterDefinition}, plus the gallery a draft is saved with (workflow
 * 3.5.1): uploads stored with the version, kept images checked to be the draft's own. A
 * new version takes the gallery of its base with the rest of what the base says, and an
 * image no version shows any more - replaced, removed, or its draft discarded - is
 * deleted in the same transaction.
 */
@Service
public class CharacterService extends ContentService<Character, CharacterVersionEntity> {

	private final GalleryImages gallery;

	@Autowired
	public CharacterService(CharacterDefinition definition, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, AuditLogService auditLogService, Clock clock, GalleryImages gallery) {
		super(definition, contentVersionRepository, contentRepository, auditLogService, clock);
		this.gallery = gallery;
	}

	/**
	 * A new content whose first draft shows the uploaded images (UF-CNT-01): a new
	 * content has no image to keep.
	 */
	@Transactional
	public Content<Character> create(Set<Permission> permissions, User caller, Character written,
			GalleryUploads uploads) {
		var images = this.gallery.apply(written.gallery(), uploads, Set.of(), EntityType.CHARACTER);
		return create(permissions, caller, written.withGallery(images));
	}

	/**
	 * Rewrites a draft with no upload: every image it names must be one the draft already
	 * shows.
	 */
	@Override
	@Transactional
	public VersionAccess<Character> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Character written) {
		return edit(permissions, caller, contentId, versionNumber, written, GalleryUploads.NONE);
	}

	/**
	 * Rewrites a draft (UF-CNT-02), its gallery as written: images kept, reordered,
	 * removed or uploaded.
	 */
	@Transactional
	public VersionAccess<Character> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Character written, GalleryUploads uploads) {
		Set<String> previous = imagesOf(permissions, caller, contentId, versionNumber);
		var images = this.gallery.apply(written.gallery(), uploads, previous, EntityType.CHARACTER);
		var edited = super.edit(permissions, caller, contentId, versionNumber, written.withGallery(images));
		this.gallery.release(previous, edited.body().galleryImageIds());
		return edited;
	}

	/** Discards a draft (UF-CNT-11), and its images no other version shows. */
	@Override
	@Transactional
	public void delete(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		Set<String> previous = imagesOf(permissions, caller, contentId, versionNumber);
		super.delete(permissions, caller, contentId, versionNumber);
		this.gallery.release(previous, Set.of());
	}

	private Set<String> imagesOf(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		return getVersion(permissions, caller, contentId, versionNumber).body().galleryImageIds();
	}

}

package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
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
import dev.onepieceapi.contentservice.service.image.ContentImages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Characters and their versions: the whole workflow of {@link ContentService}, run with
 * the {@link CharacterDefinition}, plus the portrait a draft is saved with
 * (implementation plan of the Character, D7), by the same mechanism as the image of a
 * Devil Fruit: a new version takes the portrait of its base with the rest of what the
 * base says, and a portrait no version uses any more - replaced, removed, or its draft
 * discarded - is deleted in the same transaction.
 */
@Service
public class CharacterService extends ContentService<Character, CharacterVersionEntity> {

	private final ContentImages images;

	@Autowired
	public CharacterService(CharacterDefinition definition, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, AuditLogService auditLogService, Clock clock, ContentImages images) {
		super(definition, contentVersionRepository, contentRepository, auditLogService, clock);
		this.images = images;
	}

	/** A new content whose first draft has the uploaded portrait, if any (UF-CNT-01). */
	@Transactional
	public Content<Character> create(Set<Permission> permissions, User caller, Character written, ImageChange image) {
		String imageId = this.images.apply(image, null, EntityType.CHARACTER);
		return create(permissions, caller, written.withImage(imageId));
	}

	/**
	 * Rewrites a draft keeping its portrait: what is written never says one by itself.
	 */
	@Override
	@Transactional
	public VersionAccess<Character> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Character written) {
		return edit(permissions, caller, contentId, versionNumber, written, ImageChange.KEEP);
	}

	/** Rewrites a draft, its portrait kept, removed or replaced (UF-CNT-02). */
	@Transactional
	public VersionAccess<Character> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Character written, ImageChange image) {
		String previous = imageOf(permissions, caller, contentId, versionNumber);
		String next = this.images.apply(image, previous, EntityType.CHARACTER);
		var edited = super.edit(permissions, caller, contentId, versionNumber, written.withImage(next));
		this.images.release(previous, next);
		return edited;
	}

	/** Discards a draft (UF-CNT-11), and its portrait if no other version uses it. */
	@Override
	@Transactional
	public void delete(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		String previous = imageOf(permissions, caller, contentId, versionNumber);
		super.delete(permissions, caller, contentId, versionNumber);
		this.images.release(previous, null);
	}

	private String imageOf(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		return getVersion(permissions, caller, contentId, versionNumber).body().imageId();
	}

}

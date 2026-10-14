package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Volume;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.persistence.entity.VolumeVersionEntity;
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
 * Volumes and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link VolumeDefinition}, plus the cover a draft is saved with (implementation plan of
 * the story structure, S6), by the same mechanism as a Devil Fruit's image: a new version
 * takes its base's cover with the rest of what the base says, and a cover no version uses
 * any more - replaced, removed, or its draft discarded - is deleted in the same
 * transaction.
 */
@Service
public class VolumeService extends ContentService<Volume, VolumeVersionEntity> {

	private final ContentImages images;

	@Autowired
	public VolumeService(ContentVersionRepository contentVersionRepository, ContentRepository contentRepository,
			AuditLogService auditLogService, Clock clock, ContentImages images, VolumeDefinition definition) {
		super(definition, contentVersionRepository, contentRepository, auditLogService, clock);
		this.images = images;
	}

	/** A new content whose first draft has the uploaded cover, if any (UF-CNT-01). */
	@Transactional
	public Content<Volume> create(Set<Permission> permissions, User caller, Volume written, ImageChange cover) {
		String coverImageId = this.images.apply(cover, null, EntityType.VOLUME);
		return create(permissions, caller, written.withCover(coverImageId));
	}

	/** Rewrites a draft keeping its cover: what is written never says one by itself. */
	@Override
	@Transactional
	public VersionAccess<Volume> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Volume written) {
		return edit(permissions, caller, contentId, versionNumber, written, ImageChange.KEEP);
	}

	/** Rewrites a draft, its cover kept, removed or replaced (UF-CNT-02). */
	@Transactional
	public VersionAccess<Volume> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Volume written, ImageChange cover) {
		String previous = coverOf(permissions, caller, contentId, versionNumber);
		String next = this.images.apply(cover, previous, EntityType.VOLUME);
		var edited = super.edit(permissions, caller, contentId, versionNumber, written.withCover(next));
		this.images.release(previous, next);
		return edited;
	}

	/** Discards a draft (UF-CNT-11), and its cover if no other version uses it. */
	@Override
	@Transactional
	public void delete(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		String previous = coverOf(permissions, caller, contentId, versionNumber);
		super.delete(permissions, caller, contentId, versionNumber);
		this.images.release(previous, null);
	}

	private String coverOf(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		return getVersion(permissions, caller, contentId, versionNumber).body().coverImageId();
	}

}

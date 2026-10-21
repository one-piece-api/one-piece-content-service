package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
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
 * Species and their versions: the whole workflow of {@link ContentService}, run with the
 * {@link SpeciesDefinition}, plus the picture a draft is saved with (implementation plan
 * of the Species, D5), by the same mechanism as the image of a Devil Fruit: a new version
 * takes the picture of its base with the rest of what the base says, and a picture no
 * version uses any more - replaced, removed, or its draft discarded - is deleted in the
 * same transaction.
 */
@Service
public class SpeciesService extends ContentService<Species, SpeciesVersionEntity> {

	private final ContentImages images;

	@Autowired
	public SpeciesService(SpeciesDefinition definition, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, AuditLogService auditLogService, Clock clock, ContentImages images) {
		super(definition, contentVersionRepository, contentRepository, auditLogService, clock);
		this.images = images;
	}

	/** A new content whose first draft has the uploaded picture, if any (UF-CNT-01). */
	@Transactional
	public Content<Species> create(Set<Permission> permissions, User caller, Species written, ImageChange image) {
		String imageId = this.images.apply(image, null, EntityType.SPECIES);
		return create(permissions, caller, written.withImage(imageId));
	}

	/** Rewrites a draft keeping its picture: what is written never says one by itself. */
	@Override
	@Transactional
	public VersionAccess<Species> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Species written) {
		return edit(permissions, caller, contentId, versionNumber, written, ImageChange.KEEP);
	}

	/** Rewrites a draft, its picture kept, removed or replaced (UF-CNT-02). */
	@Transactional
	public VersionAccess<Species> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Species written, ImageChange image) {
		String previous = imageOf(permissions, caller, contentId, versionNumber);
		String next = this.images.apply(image, previous, EntityType.SPECIES);
		var edited = super.edit(permissions, caller, contentId, versionNumber, written.withImage(next));
		this.images.release(previous, next);
		return edited;
	}

	/** Discards a draft (UF-CNT-11), and its picture if no other version uses it. */
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

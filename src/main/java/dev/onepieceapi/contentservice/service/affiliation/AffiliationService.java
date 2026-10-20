package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
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
 * Affiliations and their versions: the whole workflow of {@link ContentService}, run with
 * the {@link AffiliationDefinition}, plus the emblem a draft is saved with
 * (implementation plan of the Affiliation, D5), by the same mechanism as the image of a
 * Devil Fruit: a new version takes the emblem of its base with the rest of what the base
 * says, and an emblem no version uses any more - replaced, removed, or its draft
 * discarded - is deleted in the same transaction.
 */
@Service
public class AffiliationService extends ContentService<Affiliation, AffiliationVersionEntity> {

	private final ContentImages images;

	@Autowired
	public AffiliationService(AffiliationDefinition definition, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, AuditLogService auditLogService, Clock clock, ContentImages images) {
		super(definition, contentVersionRepository, contentRepository, auditLogService, clock);
		this.images = images;
	}

	/** A new content whose first draft has the uploaded emblem, if any (UF-CNT-01). */
	@Transactional
	public Content<Affiliation> create(Set<Permission> permissions, User caller, Affiliation written,
			ImageChange image) {
		String imageId = this.images.apply(image, null, EntityType.AFFILIATION);
		return create(permissions, caller, written.withImage(imageId));
	}

	/** Rewrites a draft keeping its emblem: what is written never says one by itself. */
	@Override
	@Transactional
	public VersionAccess<Affiliation> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Affiliation written) {
		return edit(permissions, caller, contentId, versionNumber, written, ImageChange.KEEP);
	}

	/** Rewrites a draft, its emblem kept, removed or replaced (UF-CNT-02). */
	@Transactional
	public VersionAccess<Affiliation> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			Affiliation written, ImageChange image) {
		String previous = imageOf(permissions, caller, contentId, versionNumber);
		String next = this.images.apply(image, previous, EntityType.AFFILIATION);
		var edited = super.edit(permissions, caller, contentId, versionNumber, written.withImage(next));
		this.images.release(previous, next);
		return edited;
	}

	/** Discards a draft (UF-CNT-11), and its emblem if no other version uses it. */
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

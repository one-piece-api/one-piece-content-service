package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentListSummary;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.TransitionContext;
import dev.onepieceapi.contentservice.domain.workflow.TransitionDecision;
import dev.onepieceapi.contentservice.domain.workflow.TransitionPolicy;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.DevilFruitTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Devil Fruit Types and their versions: reading them
 * (docs/user-flows/content-editorial-workflow.md UF-CNT-12, UF-CNT-18), creating one,
 * editing its draft and discarding it (UF-CNT-01, UF-CNT-02, UF-CNT-11), sending it to
 * review and taking it back (UF-CNT-03, UF-CNT-04). Every method starts from the caller:
 * {@link VisibilityPolicy} turns their permissions into the statuses they may see, and
 * nothing outside those statuses is ever loaded; {@link TransitionPolicy} says what they
 * may do with a version they see, and the same answer guards each change. Building the
 * queries, converting rows and validating what is saved are done elsewhere: this class
 * only decides what to read or change, and for whom.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeService {

	private static final String AUDIT_ACTION_CREATED = "VERSION_CREATED";

	private static final String AUDIT_ACTION_EDITED = "VERSION_EDITED";

	private static final String AUDIT_ACTION_DELETED = "VERSION_DELETED";

	private static final String AUDIT_ACTION_SUBMITTED = "VERSION_SUBMITTED";

	private static final String AUDIT_ACTION_PULLED_BACK = "VERSION_PULLED_BACK";

	private final DevilFruitTypeVersionRepository versionRepository;

	private final ContentVersionRepository contentVersionRepository;

	private final ContentRepository contentRepository;

	private final DevilFruitTypeValidator validator;

	private final AuditLogService auditLogService;

	private final Clock clock;

	/**
	 * One row per content: its most recent visible version or, when filtering by status,
	 * its visible version in that status. The other filters apply to that row.
	 */
	public Page<ContentSummary<DevilFruitType>> list(Set<Permission> permissions, User caller, ContentFilter filter,
			Pageable pageable) {
		Set<VersionStatus> statuses = filter.statusesAmong(VisibilityPolicy.visibleStatuses(permissions));
		if (statuses.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<DevilFruitTypeVersionEntity> page = this.versionRepository.search(statuses, filter, this.clock, pageable);
		List<UUID> contentIds = contentIdsOf(page);
		Map<UUID, Integer> onlineNumbers = this.contentVersionRepository.onlineVersionNumbers(contentIds);
		Set<UUID> withOpenVersion = this.contentVersionRepository.withOpenVersion(contentIds);
		return page.map(entity -> {
			UUID contentId = entity.getContentId();
			Version<DevilFruitType> version = DevilFruitTypeVersionMapper.toDomain(entity);
			var context = new TransitionContext(version, withOpenVersion.contains(contentId), caller, permissions);
			Set<VersionAction> allowedActions = TransitionPolicy.allowedActions(context);
			return new ContentSummary<>(contentId, version, onlineNumbers.get(contentId), allowedActions);
		});
	}

	/**
	 * The distinct authors of the versions the caller sees - the list's author filter.
	 */
	public List<User> authors(Set<Permission> permissions) {
		return this.versionRepository.findAuthors(VisibilityPolicy.visibleStatuses(permissions));
	}

	/**
	 * What surrounds the list, whatever filter is on: how many contents the caller sees,
	 * how many of those are shown by a version of their own, and the statuses they may
	 * filter by.
	 */
	public ContentListSummary summary(Set<Permission> permissions, User caller) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		if (statuses.isEmpty()) {
			return new ContentListSummary(0, 0, statuses);
		}
		long total = this.versionRepository.count(statuses, ContentFilter.none(), this.clock);
		long mine = this.versionRepository.count(statuses, ContentFilter.authoredBy(caller.username()), this.clock);
		return new ContentListSummary(total, mine, statuses);
	}

	/** A content with its visible versions, and what the caller may do with each. */
	public Content<DevilFruitType> get(Set<Permission> permissions, User caller, UUID contentId) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		List<DevilFruitTypeVersionEntity> versions = this.versionRepository.findVisible(contentId, statuses);
		if (versions.isEmpty()) {
			throw new DevilFruitTypeNotFoundException(contentId);
		}
		boolean hasOpenVersion = this.contentVersionRepository.hasOpenVersion(contentId);
		List<VersionAccess<DevilFruitType>> chain = versions.stream()
			.map(DevilFruitTypeVersionMapper::toDomain)
			.map(version -> accessTo(version, new TransitionContext(version, hasOpenVersion, caller, permissions)))
			.toList();
		return new Content<>(contentId, chain);
	}

	/** One visible version with what it says, and what the caller may do with it. */
	public VersionAccess<DevilFruitType> getVersion(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> version = DevilFruitTypeVersionMapper.toDomain(entity);
		return accessTo(version, contextOf(version, contentId, caller, permissions));
	}

	/** The history of a version the caller sees, oldest first. */
	public List<VersionEvent> events(Set<Permission> permissions, UUID contentId, int versionNumber) {
		DevilFruitTypeVersionEntity version = visibleVersion(permissions, contentId, versionNumber);
		return this.auditLogService.versionEvents(version.getVersionId());
	}

	/**
	 * A new content, born with its first version: a draft of the caller saying what was
	 * given, which may be incomplete (UF-CNT-01).
	 */
	@Transactional
	public Content<DevilFruitType> create(Set<Permission> permissions, User caller, DevilFruitType written) {
		UUID contentId = UUID.randomUUID();
		DevilFruitType body = written.normalized();
		this.validator.validateDraft(contentId, body);
		Instant now = this.clock.instant();
		this.contentRepository.save(new ContentEntity(contentId, EntityType.DEVIL_FRUIT_TYPE, now));
		var draft = DevilFruitTypeVersionMapper.toFirstDraft(contentId, caller, body, now);
		DevilFruitTypeVersionEntity saved = this.versionRepository.save(draft);
		UUID versionId = saved.getVersionId();
		this.auditLogService.recordOnVersion(AUDIT_ACTION_CREATED, caller, contentId, versionId, body.romaji());
		Version<DevilFruitType> version = DevilFruitTypeVersionMapper.toDomain(saved);
		var context = new TransitionContext(version, true, caller, permissions);
		return new Content<>(contentId, List.of(accessTo(version, context)));
	}

	/**
	 * Replaces what a draft says with what was given (UF-CNT-02). Only for whoever
	 * {@link TransitionPolicy} lets edit it, and only while it is a draft.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> edit(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber, DevilFruitType written) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.EDIT, context);
		DevilFruitType body = written.normalized();
		this.validator.validateDraft(contentId, body);
		DevilFruitTypeVersionMapper.rewrite(entity, body, this.clock.instant());
		UUID versionId = entity.getVersionId();
		this.auditLogService.recordOnVersion(AUDIT_ACTION_EDITED, caller, contentId, versionId, body.romaji());
		return accessTo(DevilFruitTypeVersionMapper.toDomain(entity), context);
	}

	/**
	 * Removes a draft for good (UF-CNT-11): the content goes back to its previous version
	 * and the number is free again. A first version takes its content with it - there is
	 * nothing left to go back to. Only the audit log remembers it.
	 */
	@Transactional
	public void delete(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> version = DevilFruitTypeVersionMapper.toDomain(entity);
		require(VersionAction.DELETE, contextOf(version, contentId, caller, permissions));
		this.versionRepository.delete(entity);
		if (version.isFirst()) {
			this.contentRepository.deleteById(contentId);
		}
		UUID versionId = entity.getVersionId();
		String label = version.body().romaji();
		this.auditLogService.recordOnVersion(AUDIT_ACTION_DELETED, caller, contentId, versionId, label);
	}

	/**
	 * Sends a draft to review (UF-CNT-03): from then on reviewers see it, unclaimed. Only
	 * a version complete, unique and different from the others of its content goes.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> submit(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.SUBMIT, context);
		this.validator.validateSubmission(contentId, versionNumber, current.body());
		return moveTo(VersionStatus.IN_REVIEW, entity, context, AUDIT_ACTION_SUBMITTED);
	}

	/**
	 * Takes a version back from review while no reviewer holds it (UF-CNT-04): a draft of
	 * its author again, editable.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> pullBack(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.PULL_BACK, context);
		return moveTo(VersionStatus.DRAFT, entity, context, AUDIT_ACTION_PULLED_BACK);
	}

	/**
	 * Takes a version the rules already let move to another status, records it, and
	 * answers with the version as it now is. Whether the content has an open version is
	 * unchanged: both submitting and pulling back go from one open status to another.
	 */
	private VersionAccess<DevilFruitType> moveTo(VersionStatus status, DevilFruitTypeVersionEntity entity,
			TransitionContext before, String auditAction) {
		entity.moveTo(status, this.clock.instant());
		Version<DevilFruitType> moved = DevilFruitTypeVersionMapper.toDomain(entity);
		User caller = before.caller();
		this.auditLogService.recordOnVersion(auditAction, caller, entity.getContentId(), entity.getVersionId(),
				moved.body().romaji());
		var after = new TransitionContext(moved, before.contentHasOpenVersion(), caller, before.permissions());
		return accessTo(moved, after);
	}

	private DevilFruitTypeVersionEntity visibleVersion(Set<Permission> permissions, UUID contentId, int versionNumber) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		return this.versionRepository.findVisible(contentId, versionNumber, statuses)
			.orElseThrow(() -> new VersionNotFoundException(contentId, versionNumber));
	}

	private TransitionContext contextOf(Version<?> version, UUID contentId, User caller, Set<Permission> permissions) {
		boolean hasOpenVersion = this.contentVersionRepository.hasOpenVersion(contentId);
		return new TransitionContext(version, hasOpenVersion, caller, permissions);
	}

	/** The version with what the transition rules let the caller do with it. */
	private static <T> VersionAccess<T> accessTo(Version<T> version, TransitionContext context) {
		return new VersionAccess<>(version, TransitionPolicy.allowedActions(context));
	}

	/** Stops an action the transition rules refuse, telling the two refusals apart. */
	private static void require(VersionAction action, TransitionContext context) {
		TransitionDecision decision = TransitionPolicy.decide(action, context);
		if (decision == TransitionDecision.FORBIDDEN) {
			throw new VersionActionForbiddenException(action);
		}
		if (decision == TransitionDecision.CONFLICT) {
			throw new VersionActionConflictException(action);
		}
	}

	private static List<UUID> contentIdsOf(Page<DevilFruitTypeVersionEntity> page) {
		return page.getContent().stream().map(DevilFruitTypeVersionEntity::getContentId).toList();
	}

}

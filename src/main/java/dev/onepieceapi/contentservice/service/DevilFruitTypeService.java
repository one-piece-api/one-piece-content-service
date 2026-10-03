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
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
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
import org.springframework.dao.DataIntegrityViolationException;
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
 * review and taking it back (UF-CNT-03, UF-CNT-04), a reviewer claiming and releasing it
 * (UF-CNT-13, UF-CNT-14), approving or rejecting it, and its author taking a rejected one
 * back to draft (UF-CNT-05, UF-CNT-06, UF-CNT-15), putting it online (UF-CNT-07), opening
 * the next version from a closed one (UF-CNT-08). Every method starts from the caller:
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

	private static final String AUDIT_ACTION_CLAIMED = "VERSION_CLAIMED";

	private static final String AUDIT_ACTION_RELEASED = "VERSION_RELEASED";

	private static final String AUDIT_ACTION_APPROVED = "VERSION_APPROVED";

	private static final String AUDIT_ACTION_REJECTED = "VERSION_REJECTED";

	private static final String AUDIT_ACTION_RETURNED_TO_DRAFT = "VERSION_RETURNED_TO_DRAFT";

	private static final String AUDIT_ACTION_PUBLISHED = "VERSION_PUBLISHED";

	private static final String AUDIT_ACTION_SUPERSEDED = "VERSION_SUPERSEDED";

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
		entity.submit(this.clock.instant());
		return recorded(entity, context, AUDIT_ACTION_SUBMITTED);
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
		entity.moveTo(VersionStatus.DRAFT, this.clock.instant());
		return recorded(entity, context, AUDIT_ACTION_PULLED_BACK);
	}

	/**
	 * A reviewer takes a version in review (UF-CNT-13): from then on only they can decide
	 * on it, and everyone who sees it sees who holds it. Never the version's own author.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> claim(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.CLAIM, context);
		entity.claimBy(UserMapper.toEmbeddable(caller));
		return recorded(entity, context, AUDIT_ACTION_CLAIMED);
	}

	/**
	 * The reviewer holding a version lets it go (UF-CNT-14): unclaimed again, available
	 * to any reviewer.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> release(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RELEASE, context);
		entity.release();
		return recorded(entity, context, AUDIT_ACTION_RELEASED);
	}

	/**
	 * The claimant passes the review (UF-CNT-05): the version is ready to publish, and
	 * nobody holds it any more.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> approve(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.APPROVE, context);
		entity.approve(this.clock.instant());
		return recorded(entity, context, AUDIT_ACTION_APPROVED);
	}

	/**
	 * The claimant fails the review, saying why (UF-CNT-06): the version is frozen until
	 * its author takes it back to draft, and the reason stays on it and in its history.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> reject(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber, String reason) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.REJECT, context);
		entity.reject(reason, this.clock.instant());
		return recorded(entity, context, AUDIT_ACTION_REJECTED, reason);
	}

	/**
	 * The author takes a rejected version back (UF-CNT-15): a draft again, editable,
	 * still showing why it was rejected until it is submitted again.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> returnToDraft(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RETURN_TO_DRAFT, context);
		entity.moveTo(VersionStatus.DRAFT, this.clock.instant());
		return recorded(entity, context, AUDIT_ACTION_RETURNED_TO_DRAFT);
	}

	/**
	 * Puts a version ready to publish online (UF-CNT-07). The version online until then,
	 * if any, is superseded in the same transaction, and its own history says by which.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> publish(Set<Permission> permissions, User caller, UUID contentId,
			int versionNumber) {
		DevilFruitTypeVersionEntity entity = visibleVersion(permissions, contentId, versionNumber);
		Version<DevilFruitType> current = DevilFruitTypeVersionMapper.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.PUBLISH, context);
		Instant now = this.clock.instant();
		this.versionRepository.findOnline(contentId).ifPresent(online -> supersede(online, versionNumber, caller, now));
		entity.moveTo(VersionStatus.PUBLISHED, now);
		return recorded(entity, context, AUDIT_ACTION_PUBLISHED);
	}

	/**
	 * Opens the next version of a content from one of its closed versions (UF-CNT-08): a
	 * draft of the caller, saying what the base says and recording it as its base.
	 * Nothing else moves - the online version stays online. The rules allow it only while
	 * the content has no open version; two editors opening one at the same instant both
	 * pass that check, and the database lets only one of them in (one open version, one
	 * row per number - see {@code V2}): the other is refused as if it had come second.
	 */
	@Transactional
	public VersionAccess<DevilFruitType> openNewVersion(Set<Permission> permissions, User caller, UUID contentId,
			int basedOn) {
		DevilFruitTypeVersionEntity baseEntity = visibleVersion(permissions, contentId, basedOn);
		Version<DevilFruitType> base = DevilFruitTypeVersionMapper.toDomain(baseEntity);
		require(VersionAction.OPEN_NEW_VERSION, contextOf(base, contentId, caller, permissions));
		int number = this.contentVersionRepository.findLatestNumber(contentId) + 1;
		var draft = DevilFruitTypeVersionMapper.toDraftFrom(contentId, number, base, caller, this.clock.instant());
		DevilFruitTypeVersionEntity saved;
		try {
			saved = this.versionRepository.saveAndFlush(draft);
		}
		catch (DataIntegrityViolationException ex) {
			throw new VersionActionConflictException(VersionAction.OPEN_NEW_VERSION);
		}
		this.auditLogService.recordOnVersion(AUDIT_ACTION_CREATED, caller, contentId, saved.getVersionId(),
				saved.getRomaji(), String.valueOf(basedOn));
		Version<DevilFruitType> opened = DevilFruitTypeVersionMapper.toDomain(saved);
		return accessTo(opened, new TransitionContext(opened, true, caller, permissions));
	}

	/**
	 * Takes the online version offline in favour of another, and writes it at once: the
	 * database allows one online version per content and checks it statement by
	 * statement, so this one must be gone before the other one arrives.
	 * @param replacedBy the number of the version going online in its place
	 */
	private void supersede(DevilFruitTypeVersionEntity online, int replacedBy, User caller, Instant now) {
		online.moveTo(VersionStatus.SUPERSEDED, now);
		this.versionRepository.flush();
		this.auditLogService.recordOnVersion(AUDIT_ACTION_SUPERSEDED, caller, online.getContentId(),
				online.getVersionId(), online.getRomaji(), String.valueOf(replacedBy));
	}

	private VersionAccess<DevilFruitType> recorded(DevilFruitTypeVersionEntity entity, TransitionContext before,
			String auditAction) {
		return recorded(entity, before, auditAction, null);
	}

	/**
	 * Records a change the rules already allowed and answers with the version as it now
	 * is, with what the caller may do with it next.
	 * @param detail what the action carries with it, e.g. a rejection reason
	 */
	private VersionAccess<DevilFruitType> recorded(DevilFruitTypeVersionEntity entity, TransitionContext before,
			String auditAction, String detail) {
		Version<DevilFruitType> moved = DevilFruitTypeVersionMapper.toDomain(entity);
		this.auditLogService.recordOnVersion(auditAction, before.caller(), entity.getContentId(), entity.getVersionId(),
				moved.body().romaji(), detail);
		return accessTo(moved, before.after(moved));
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

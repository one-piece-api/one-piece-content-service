package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.service.audit.AuditLogService;
import dev.onepieceapi.contentservice.service.audit.VersionAuditRecord;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentListSummary;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
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
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.mapper.ContentVersionMapper;
import dev.onepieceapi.contentservice.persistence.mapper.UserMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The contents of one entity and their versions - the whole editorial workflow, written
 * once for every entity (implementation plan of the Devil Fruit, D8): reading them
 * (docs/user-flows/content-editorial-workflow.md UF-CNT-12, UF-CNT-18), creating one,
 * editing its draft and discarding it (UF-CNT-01, UF-CNT-02, UF-CNT-11), sending it to
 * review and taking it back (UF-CNT-03, UF-CNT-04), a reviewer claiming and releasing it
 * (UF-CNT-13, UF-CNT-14), approving or rejecting it, and its author taking a rejected one
 * back to draft (UF-CNT-05, UF-CNT-06, UF-CNT-15), putting it online (UF-CNT-07), setting
 * it aside and back (UF-CNT-16, UF-CNT-17), taking it offline and back online (UF-CNT-10,
 * UF-CNT-09), opening the next version from a closed one (UF-CNT-08) - and an
 * administrator doing so in someone else's place (UF-CNT-20), recorded as such. Every
 * method starts from the caller: {@link VisibilityPolicy} turns their permissions into
 * the statuses they may see, and nothing outside those statuses is ever loaded;
 * {@link TransitionPolicy} says what they may do with a version they see, and the same
 * answer guards each change. What differs per entity - where its versions are stored, how
 * they read, how they are validated - comes from its {@link ContentDefinition}; each
 * entity has its own service extending this one, e.g. {@code DevilFruitTypeService}.
 * Building the queries, converting rows and validating what is saved are done elsewhere:
 * this class only decides what to read or change, and for whom.
 *
 * @param <T> what a version of the entity says
 * @param <E> the entity's version table
 */
@Transactional(readOnly = true)
public class ContentService<T extends ContentBody<T>, E extends VersionBodyEntity> {

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

	private static final String AUDIT_ACTION_ARCHIVED = "VERSION_ARCHIVED";

	private static final String AUDIT_ACTION_RECOVERED = "VERSION_RECOVERED";

	private static final String AUDIT_ACTION_RETIRED = "VERSION_RETIRED";

	private static final String AUDIT_ACTION_RESTORED = "VERSION_RESTORED";

	private final ContentDefinition<T, E> definition;

	private final VersionBodyRepository<E> versionRepository;

	private final ContentValidator<T, E> validator;

	private final ContentRules<T> rules;

	private final ContentVersionRepository contentVersionRepository;

	private final ContentRepository contentRepository;

	private final AuditLogService auditLogService;

	private final Clock clock;

	protected ContentService(ContentDefinition<T, E> definition, ContentVersionRepository contentVersionRepository,
			ContentRepository contentRepository, AuditLogService auditLogService, Clock clock) {
		this.definition = definition;
		this.versionRepository = definition.repository();
		this.validator = definition.validator();
		this.rules = definition.rules();
		this.contentVersionRepository = contentVersionRepository;
		this.contentRepository = contentRepository;
		this.auditLogService = auditLogService;
		this.clock = clock;
	}

	/**
	 * One row per content: its most recent visible version or, when filtering by status,
	 * its visible version in that status. The other filters apply to that row. A sort by
	 * name reads it in {@code language}, the one the caller is reading in.
	 */
	public Page<ContentSummary<T>> list(Set<Permission> permissions, User caller, ContentFilter filter,
			Pageable pageable, String language) {
		Set<VersionStatus> statuses = filter.statusesAmong(VisibilityPolicy.visibleStatuses(permissions));
		if (statuses.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<E> page = this.versionRepository.search(statuses, filter, this.clock, pageable, language);
		List<UUID> contentIds = contentIdsOf(page);
		Map<UUID, Integer> onlineNumbers = this.contentVersionRepository.onlineVersionNumbers(contentIds);
		Set<UUID> withOpenVersion = this.contentVersionRepository.withOpenVersion(contentIds);
		return page.map(entity -> {
			UUID contentId = entity.getContentId();
			Version<T> version = this.definition.toDomain(entity);
			var context = new TransitionContext(version, withOpenVersion.contains(contentId), caller, permissions);
			Set<VersionAction> allowedActions = TransitionPolicy.allowedActions(context);
			Set<VersionAction> overrideActions = TransitionPolicy.overrideActions(context);
			Integer onlineNumber = onlineNumbers.get(contentId);
			return new ContentSummary<>(contentId, version, onlineNumber, allowedActions, overrideActions);
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
	public Content<T> get(Set<Permission> permissions, User caller, UUID contentId) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		List<E> versions = this.versionRepository.findVisible(contentId, statuses);
		if (versions.isEmpty()) {
			throw this.definition.notFound(contentId);
		}
		boolean hasOpenVersion = this.contentVersionRepository.hasOpenVersion(contentId);
		List<VersionAccess<T>> chain = versions.stream()
			.map(this.definition::toDomain)
			.map(version -> accessTo(contentId, version,
					new TransitionContext(version, hasOpenVersion, caller, permissions)))
			.toList();
		return new Content<>(contentId, chain);
	}

	/** One visible version with what it says, and what the caller may do with it. */
	public VersionAccess<T> getVersion(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> version = this.definition.toDomain(entity);
		return accessTo(contentId, version, contextOf(version, contentId, caller, permissions));
	}

	/** The history of a version the caller sees, oldest first. */
	public List<VersionEvent> events(Set<Permission> permissions, UUID contentId, int versionNumber) {
		E version = visibleVersion(permissions, contentId, versionNumber);
		return this.auditLogService.versionEvents(version.getVersionId());
	}

	/**
	 * A new content, born with its first version: a draft of the caller saying what was
	 * given, which may be incomplete (UF-CNT-01).
	 */
	@Transactional
	public Content<T> create(Set<Permission> permissions, User caller, T written) {
		UUID contentId = UUID.randomUUID();
		T body = written.normalized();
		this.validator.validateDraft(contentId, body);
		Instant now = this.clock.instant();
		this.contentRepository.save(new ContentEntity(contentId, this.definition.entityType(), now));
		E draft = newDraft(contentId, 1, null, caller, body, now);
		E saved = this.versionRepository.save(draft);
		this.auditLogService.recordOnVersion(auditRecord(AUDIT_ACTION_CREATED, caller, saved).build());
		Version<T> version = this.definition.toDomain(saved);
		var context = new TransitionContext(version, true, caller, permissions);
		return new Content<>(contentId, List.of(accessTo(contentId, version, context)));
	}

	/**
	 * Replaces what a draft says with what was given (UF-CNT-02). Only for whoever
	 * {@link TransitionPolicy} lets edit it, and only while it is a draft.
	 */
	@Transactional
	public VersionAccess<T> edit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			T written) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.EDIT, context);
		T body = written.normalized();
		this.validator.validateDraft(contentId, body);
		this.definition.rewrite(entity, body, this.clock.instant());
		this.auditLogService.recordOnVersion(auditRecord(AUDIT_ACTION_EDITED, caller, entity)
			.override(TransitionPolicy.overrides(VersionAction.EDIT, context))
			.build());
		return accessTo(contentId, this.definition.toDomain(entity), context);
	}

	/**
	 * Removes a draft for good (UF-CNT-11): the content goes back to its previous version
	 * and the number is free again. A first version takes its content with it - there is
	 * nothing left to go back to. Only the audit log remembers it.
	 */
	@Transactional
	public void delete(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> version = this.definition.toDomain(entity);
		TransitionContext context = contextOf(version, contentId, caller, permissions);
		require(VersionAction.DELETE, context);
		this.versionRepository.delete(entity);
		if (version.isFirst()) {
			this.contentRepository.deleteById(contentId);
		}
		this.auditLogService.recordOnVersion(auditRecord(AUDIT_ACTION_DELETED, caller, entity)
			.override(TransitionPolicy.overrides(VersionAction.DELETE, context))
			.build());
	}

	/**
	 * Sends a draft to review (UF-CNT-03): from then on reviewers see it, unclaimed. Only
	 * a version complete, unique and different from the others of its content goes.
	 */
	@Transactional
	public VersionAccess<T> submit(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.SUBMIT, contentId, current, context);
		this.validator.validateSubmission(contentId, versionNumber, current.body());
		entity.submit(this.clock.instant());
		return recorded(entity, context, VersionAction.SUBMIT, AUDIT_ACTION_SUBMITTED);
	}

	/**
	 * Takes a version back from review while no reviewer holds it (UF-CNT-04): a draft of
	 * its author again, editable.
	 */
	@Transactional
	public VersionAccess<T> pullBack(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.PULL_BACK, contentId, current, context);
		entity.moveTo(VersionStatus.DRAFT, this.clock.instant());
		return recorded(entity, context, VersionAction.PULL_BACK, AUDIT_ACTION_PULLED_BACK);
	}

	/**
	 * A reviewer takes a version in review (UF-CNT-13): from then on only they can decide
	 * on it, and everyone who sees it sees who holds it. Never the version's own author.
	 */
	@Transactional
	public VersionAccess<T> claim(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.CLAIM, contentId, current, context);
		entity.claimBy(UserMapper.toEmbeddable(caller));
		return recorded(entity, context, VersionAction.CLAIM, AUDIT_ACTION_CLAIMED);
	}

	/**
	 * The reviewer holding a version lets it go (UF-CNT-14): unclaimed again, available
	 * to any reviewer. An administrator may release someone else's claim (2.3); its
	 * record then says whose claim it was.
	 */
	@Transactional
	public VersionAccess<T> release(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RELEASE, contentId, current, context);
		boolean forced = TransitionPolicy.overrides(VersionAction.RELEASE, context);
		String heldBy = forced ? current.claimantUsername() : null;
		entity.release();
		return recorded(entity, context, VersionAction.RELEASE, AUDIT_ACTION_RELEASED, heldBy);
	}

	/**
	 * The claimant passes the review (UF-CNT-05): the version is ready to publish, and
	 * nobody holds it any more.
	 */
	@Transactional
	public VersionAccess<T> approve(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.APPROVE, contentId, current, context);
		entity.approve(this.clock.instant());
		return recorded(entity, context, VersionAction.APPROVE, AUDIT_ACTION_APPROVED);
	}

	/**
	 * The claimant fails the review, saying why (UF-CNT-06): the version is frozen until
	 * its author takes it back to draft, and the reason stays on it and in its history.
	 */
	@Transactional
	public VersionAccess<T> reject(Set<Permission> permissions, User caller, UUID contentId, int versionNumber,
			String reason) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.REJECT, contentId, current, context);
		entity.reject(reason, this.clock.instant());
		return recorded(entity, context, VersionAction.REJECT, AUDIT_ACTION_REJECTED, reason);
	}

	/**
	 * The author takes a rejected version back (UF-CNT-15): a draft again, editable,
	 * still showing why it was rejected until it is submitted again.
	 */
	@Transactional
	public VersionAccess<T> returnToDraft(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RETURN_TO_DRAFT, contentId, current, context);
		entity.moveTo(VersionStatus.DRAFT, this.clock.instant());
		return recorded(entity, context, VersionAction.RETURN_TO_DRAFT, AUDIT_ACTION_RETURNED_TO_DRAFT);
	}

	/**
	 * Puts a version ready to publish online (UF-CNT-07). The version online until then,
	 * if any, is superseded in the same transaction, and its own history says by which.
	 */
	@Transactional
	public VersionAccess<T> publish(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.PUBLISH, contentId, current, context);
		putOnline(entity, versionNumber, VersionAction.PUBLISH, caller);
		return recorded(entity, context, VersionAction.PUBLISH, AUDIT_ACTION_PUBLISHED);
	}

	/**
	 * Sets a version ready to publish aside without putting it online (UF-CNT-16): it is
	 * closed from then on, so its content may open a new draft.
	 */
	@Transactional
	public VersionAccess<T> archive(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.ARCHIVE, contentId, current, context);
		entity.moveTo(VersionStatus.ARCHIVED, this.clock.instant());
		return recorded(entity, context, VersionAction.ARCHIVE, AUDIT_ACTION_ARCHIVED);
	}

	/**
	 * Brings an archived version back among those ready to publish (UF-CNT-17) - open
	 * again, so only while its content has no other open version. A new draft opened at
	 * the same instant passes that check too; the database lets only one of them in (one
	 * open version per content - see {@code V2}), and the recovery is written at once so
	 * that, if it is the one left out, it is refused as if it had come second.
	 */
	@Transactional
	public VersionAccess<T> recover(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RECOVER, contentId, current, context);
		entity.moveTo(VersionStatus.READY_TO_PUBLISH, this.clock.instant());
		try {
			this.versionRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new VersionActionConflictException(VersionAction.RECOVER);
		}
		return recorded(entity, context, VersionAction.RECOVER, AUDIT_ACTION_RECOVERED);
	}

	/**
	 * Takes the online version offline (UF-CNT-10): its content has nothing online until
	 * a version is restored or published. Nothing is deleted.
	 */
	@Transactional
	public VersionAccess<T> retire(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RETIRE, contentId, current, context);
		entity.moveTo(VersionStatus.RETIRED, this.clock.instant());
		return recorded(entity, context, VersionAction.RETIRE, AUDIT_ACTION_RETIRED);
	}

	/**
	 * Puts a version that was online back online as it was (UF-CNT-09): no new version,
	 * no review, whatever version of its content is open meanwhile. The version online
	 * until then, if any, is superseded in the same transaction, as by a publication.
	 */
	@Transactional
	public VersionAccess<T> restore(Set<Permission> permissions, User caller, UUID contentId, int versionNumber) {
		E entity = visibleVersion(permissions, contentId, versionNumber);
		Version<T> current = this.definition.toDomain(entity);
		TransitionContext context = contextOf(current, contentId, caller, permissions);
		require(VersionAction.RESTORE, contentId, current, context);
		putOnline(entity, versionNumber, VersionAction.RESTORE, caller);
		return recorded(entity, context, VersionAction.RESTORE, AUDIT_ACTION_RESTORED);
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
	public VersionAccess<T> openNewVersion(Set<Permission> permissions, User caller, UUID contentId, int basedOn) {
		E baseEntity = visibleVersion(permissions, contentId, basedOn);
		Version<T> base = this.definition.toDomain(baseEntity);
		require(VersionAction.OPEN_NEW_VERSION, contextOf(base, contentId, caller, permissions));
		int number = this.contentVersionRepository.findLatestNumber(contentId) + 1;
		E draft = newDraft(contentId, number, basedOn, caller, base.body(), this.clock.instant());
		E saved;
		try {
			saved = this.versionRepository.saveAndFlush(draft);
		}
		catch (DataIntegrityViolationException ex) {
			throw new VersionActionConflictException(VersionAction.OPEN_NEW_VERSION);
		}
		this.auditLogService
			.recordOnVersion(auditRecord(AUDIT_ACTION_CREATED, caller, saved).detail(String.valueOf(basedOn)).build());
		Version<T> opened = this.definition.toDomain(saved);
		return accessTo(contentId, opened, new TransitionContext(opened, true, caller, permissions));
	}

	/**
	 * A new version: a draft of its author, saying what was given.
	 * @param basedOn the version it was opened from; null for the first one
	 */
	private E newDraft(UUID contentId, int number, Integer basedOn, User author, T body, Instant now) {
		var workflow = ContentVersionMapper.toDraft(contentId, number, basedOn, author, now);
		E draft = this.definition.newVersion(workflow);
		this.definition.rewrite(draft, body, now);
		return draft;
	}

	/**
	 * Puts a version online in place of the one online until then, if any, and writes it
	 * at once. Another version going online at the same instant - published or restored -
	 * is stopped by the lock on the version both supersede; with nothing online to
	 * supersede, only by the database (one online version per content - see {@code V2}):
	 * the one left out is refused as if it had come second. The content takes the slug of
	 * its romaji (flows document 3.3), refused the same way in the one case the checks on
	 * save cannot stop: another content saving the same slug at the same instant.
	 */
	private void putOnline(E entity, int versionNumber, VersionAction action, User caller) {
		Instant now = this.clock.instant();
		this.versionRepository.findOnline(entity.getContentId())
			.ifPresent(online -> supersede(online, versionNumber, caller, now));
		entity.moveTo(VersionStatus.PUBLISHED, now);
		try {
			this.versionRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new VersionActionConflictException(action);
		}
		if (this.versionRepository.assignSlug(entity.getVersionId(), now) == 0) {
			throw new VersionActionConflictException(action);
		}
	}

	/**
	 * Takes the online version offline in favour of another, and writes it at once: the
	 * database allows one online version per content and checks it statement by
	 * statement, so this one must be gone before the other one arrives.
	 * @param replacedBy the number of the version going online in its place
	 */
	private void supersede(E online, int replacedBy, User caller, Instant now) {
		online.moveTo(VersionStatus.SUPERSEDED, now);
		this.versionRepository.flush();
		String detail = String.valueOf(replacedBy);
		this.auditLogService
			.recordOnVersion(auditRecord(AUDIT_ACTION_SUPERSEDED, caller, online).detail(detail).build());
	}

	private VersionAccess<T> recorded(E entity, TransitionContext before, VersionAction action, String auditAction) {
		return recorded(entity, before, action, auditAction, null);
	}

	/**
	 * Records a change the rules already allowed - saying whether only
	 * {@code content:admin} allowed it - and answers with the version as it now is, with
	 * what the caller may do with it next.
	 * @param detail what the action carries with it, e.g. a rejection reason
	 */
	private VersionAccess<T> recorded(E entity, TransitionContext before, VersionAction action, String auditAction,
			String detail) {
		this.auditLogService.recordOnVersion(auditRecord(auditAction, before.caller(), entity).detail(detail)
			.override(TransitionPolicy.overrides(action, before))
			.build());
		Version<T> moved = this.definition.toDomain(entity);
		return accessTo(entity.getContentId(), moved, before.after(moved));
	}

	/** A record of an action on the version, named as the version is now named. */
	private static VersionAuditRecord.VersionAuditRecordBuilder auditRecord(String auditAction, User actor,
			VersionBodyEntity entity) {
		return VersionAuditRecord.builder()
			.action(auditAction)
			.actor(actor)
			.contentId(entity.getContentId())
			.versionId(entity.getVersionId())
			.label(entity.getRomaji());
	}

	private E visibleVersion(Set<Permission> permissions, UUID contentId, int versionNumber) {
		Set<VersionStatus> statuses = VisibilityPolicy.visibleStatuses(permissions);
		return this.versionRepository.findVisible(contentId, versionNumber, statuses)
			.orElseThrow(() -> new VersionNotFoundException(contentId, versionNumber));
	}

	private TransitionContext contextOf(Version<?> version, UUID contentId, User caller, Set<Permission> permissions) {
		boolean hasOpenVersion = this.contentVersionRepository.hasOpenVersion(contentId);
		return new TransitionContext(version, hasOpenVersion, caller, permissions);
	}

	/**
	 * The version with what the transition rules let the caller do with it, and which of
	 * those the entity's own rules refuse.
	 */
	private VersionAccess<T> accessTo(UUID contentId, Version<T> version, TransitionContext context) {
		Set<VersionAction> allowed = TransitionPolicy.allowedActions(context);
		List<BlockedAction> blocked = allowed.stream()
			.sorted()
			.flatMap(action -> this.rules.blockOf(action, contentId, version)
				.map(block -> BlockedAction.of(action, block))
				.stream())
			.toList();
		return new VersionAccess<>(version, allowed, TransitionPolicy.overrideActions(context), blocked);
	}

	/**
	 * Stops an action the transition rules or the entity's own rules refuse. The lock the
	 * entity wants is taken first, so what its rule reads cannot change before the action
	 * is written.
	 */
	private void require(VersionAction action, UUID contentId, Version<T> version, TransitionContext context) {
		require(action, context);
		this.rules.lockBefore(action, contentId, version);
		this.rules.blockOf(action, contentId, version).ifPresent(block -> {
			throw new VersionActionBlockedException(action, block);
		});
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

	private static List<UUID> contentIdsOf(Page<? extends VersionBodyEntity> page) {
		return page.getContent().stream().map(VersionBodyEntity::getContentId).toList();
	}

}

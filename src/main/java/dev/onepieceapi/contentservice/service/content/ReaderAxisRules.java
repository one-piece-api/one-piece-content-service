package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.readertime.FirstAppearing;
import dev.onepieceapi.contentservice.domain.readertime.NamedWindow;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.WindowSequence;
import dev.onepieceapi.contentservice.domain.readertime.WindowedItem;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.exception.web.FieldViolation;
import dev.onepieceapi.readertime.Axis;
import dev.onepieceapi.readertime.PeriodRule;
import dev.onepieceapi.readertime.PeriodRules;
import dev.onepieceapi.readertime.PeriodViolation;
import dev.onepieceapi.readertime.Point;
import dev.onepieceapi.readertime.PointPart;
import dev.onepieceapi.readertime.PointRule;
import dev.onepieceapi.readertime.PointRules;
import dev.onepieceapi.readertime.PointViolation;
import dev.onepieceapi.readertime.RevealWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The rules every point of the reader axis follows
 * (docs/user-flows/content-editorial-workflow.md 3.7.4, 3.4.3, 3.4.7), written once for
 * whatever is a {@link WindowedItem}:
 * <ul>
 * <li>on save, each chapter and episode of a window is linkable, and - on the numbers
 * last approved - each window keeps to the axes of the first appearance, is not
 * discovered before it, does not end before it is discovered, and the windows of a
 * sequence follow one another without overlapping;</li>
 * <li>going online, each chapter and episode is online, and the same rules hold on the
 * numbers online: a chapter may have been renumbered since the save
 * ({@link BlockReason#STORY_POINT_OUT_OF_RANGE});</li>
 * <li>a chapter or episode is not retired while an online item uses it as a point: the
 * entity's own query, read by {@code ChapterRules} and {@code EpisodeRules}.</li>
 * </ul>
 * The story structure (Saga, Arc) has no first appearance: its points are bounded only by
 * the rules on {@code ended}. On save a point need only be a chapter or an episode, in
 * any status; going online it must be linkable, and the order is read on the numbers last
 * approved. A chapter goes online only with its arc online, so an arc could never go
 * online pointing at its own chapter; and a chapter is approved only with its arc
 * approved, so asking an approved chapter on save would leave the first Saga of an empty
 * catalog nothing to point to (3.7.4). A window without {@code discovered} is a draft's
 * business: submitting requires it ({@link WindowedItem#missingDiscovered()}). The first
 * appearance itself is a relation of the item ({@link FirstAppearanceRules}). The library
 * {@code one-piece-reader-time} compares the numbers. Not even {@code content:admin}
 * lifts them.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ReaderAxisRules {

	/**
	 * The chapter of a point, named in a block as {@code chapterId},
	 * {@code chapterNumber}.
	 */
	static final RelationDescriptor<UUID> CHAPTER = pointPart("chapter", "must be a Chapter with an approved version");

	/**
	 * The episode of a point, named in a block as {@code episodeId},
	 * {@code episodeNumber}.
	 */
	static final RelationDescriptor<UUID> EPISODE = pointPart("episode", "must be an Episode with an approved version");

	/**
	 * Where a block names the point at fault, e.g. {@code species[1].discovered.chapter}.
	 */
	static final String FIELD = "field";

	/** Why a block refuses the point, in the words of a save. */
	static final String MESSAGE = "message";

	private static final Map<PointRule, String> POINT_MESSAGES = new EnumMap<>(
			Map.of(PointRule.AXIS_NOT_MET, "must be on an axis of the first appearance",
					PointRule.BEFORE_FIRST_APPEARANCE, "must not be before the first appearance",
					PointRule.ENDED_NOT_DISCOVERED, "must be on an axis where discovered has a part",
					PointRule.ENDED_BEFORE_DISCOVERED, "must not be before discovered"));

	private static final Map<PeriodRule, String> PERIOD_MESSAGES = new EnumMap<>(Map.of(PeriodRule.OUT_OF_ORDER,
			"must not start before a previous period", PeriodRule.OVERLAPPING, "must not overlap a previous period",
			PeriodRule.OPEN_BEFORE_LAST, "must have an end: only the last period may have none"));

	private static final String ON_AXIS = "%s on the %s axis";

	private static final String NOT_A_CHAPTER = "must be a Chapter";

	private static final String NOT_AN_EPISODE = "must be an Episode";

	private static final Set<VersionStatus> ANY_STATUS = EnumSet.allOf(VersionStatus.class);

	/** Read on save only: what the items are called does not matter there. */
	private static final RelationDescriptor<FirstAppearing> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation("items");

	private static final RelationDescriptor<FirstAppearing> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation("items");

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	/**
	 * The chapter and the episode of the first appearance, the point every other one is
	 * measured from, when one is not linkable.
	 */
	public List<FieldViolation> firstAppearanceViolations(FirstAppearing item) {
		return Stream
			.of(RelationRules.notLinkable(FIRST_APPEARANCE_CHAPTER, item, "", this.chapterRepository),
					RelationRules.notLinkable(FIRST_APPEARANCE_EPISODE, item, "", this.episodeRepository))
			.flatMap(Optional::stream)
			.toList();
	}

	/**
	 * Every point at fault on save, each part named: not linkable, or - for the story
	 * structure - not a chapter or an episode at all. The order is checked only once
	 * every chapter and episode of the item is known: one that is not is already
	 * reported, as a point or as the first appearance, or is a structure's point not
	 * approved yet.
	 */
	public List<FieldViolation> violationsOnSave(WindowedItem item) {
		boolean structure = item.origin().isEmpty();
		List<FieldViolation> violations = new ArrayList<>();
		parts(item).map(part -> structure ? unknown(part) : notLinkable(part))
			.flatMap(Optional::stream)
			.forEach(violations::add);
		violations.addAll(orderViolations(item, VersionStatus.approved()));
		return violations;
	}

	private Optional<FieldViolation> notLinkable(PointTarget part) {
		return RelationRules.notLinkable(part.relation(), part.target(), part.prefix(), repositoryOf(part.relation()));
	}

	private Optional<FieldViolation> unknown(PointTarget part) {
		if (repositoryOf(part.relation()).existsWithStatus(part.target(), ANY_STATUS)) {
			return Optional.empty();
		}
		String message = part.relation() == CHAPTER ? NOT_A_CHAPTER : NOT_AN_EPISODE;
		return Optional.of(new FieldViolation(part.field(), message));
	}

	/**
	 * Before the item goes online: holds every chapter and episode of its windows shared.
	 */
	public void lockBefore(VersionAction action, WindowedItem item) {
		parts(item).forEach(
				part -> RelationRules.lockTargetBefore(action, part.relation(), part.target(), this.contentRepository));
	}

	/**
	 * Going online, the first chapter or episode of a window that is not online, then the
	 * first point the numbers online put out of order. For the story structure, the first
	 * chapter or episode not approved, then the first point the numbers approved put out
	 * of order.
	 */
	public Optional<ActionBlock> blockOf(VersionAction action, WindowedItem item) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		if (item.origin().isEmpty()) {
			return parts(item).map(this::notApproved)
				.flatMap(Optional::stream)
				.findFirst()
				.or(() -> firstOutOfRange(item, VersionStatus.approved()));
		}
		return parts(item)
			.map(part -> RelationRules.targetNotOnline(action, part.relation(), part.target(),
					Map.of(FIELD, part.field()), repositoryOf(part.relation())))
			.flatMap(Optional::stream)
			.findFirst()
			.or(() -> firstOutOfRange(item, Set.of(VersionStatus.PUBLISHED)));
	}

	/**
	 * A point of the story structure whose chapter or episode has passed no review yet,
	 * named as its latest version numbers it, e.g. {@code {field, chapterId,
	 * chapterNumber}}.
	 */
	private Optional<ActionBlock> notApproved(PointTarget part) {
		VersionBodyRepository<?> targets = repositoryOf(part.relation());
		if (targets.existsWithStatus(part.target(), VersionStatus.approved())) {
			return Optional.empty();
		}
		String name = part.relation().name();
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put(FIELD, part.field());
		detail.put(name + "Id", part.target());
		targets.findMostRecent(List.of(part.target()), ANY_STATUS)
			.stream()
			.findFirst()
			.map(VersionBodyEntity::naturalKey)
			.filter(key -> key.value() instanceof Integer)
			.ifPresent(key -> detail.put(name + "Number", key.value()));
		return Optional.of(new ActionBlock(BlockReason.STORY_POINT_NOT_APPROVED, detail));
	}

	private Optional<ActionBlock> firstOutOfRange(WindowedItem item, Collection<VersionStatus> statuses) {
		return orderViolations(item, statuses).stream().findFirst().map(ReaderAxisRules::outOfRange);
	}

	private static ActionBlock outOfRange(FieldViolation violation) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put(FIELD, violation.field());
		detail.put(MESSAGE, violation.message());
		return new ActionBlock(BlockReason.STORY_POINT_OUT_OF_RANGE, detail);
	}

	/** The rules on the order of the points, read on the numbers of these statuses. */
	private List<FieldViolation> orderViolations(WindowedItem item, Collection<VersionStatus> statuses) {
		return numbersOf(item, statuses).map(numbers -> {
			Optional<Point> origin = item.origin().map(numbers::pointOf);
			Stream<FieldViolation> points = item.windows()
				.stream()
				.flatMap(named -> pointRules(numbers.windowOf(named.window()), origin).stream()
					.filter(violation -> violation.rule() != PointRule.MISSING)
					.map(violation -> pointViolation(named, violation)));
			Stream<FieldViolation> periods = item.windowSequences()
				.stream()
				.flatMap(sequence -> PeriodRules.check(sequence.windows().stream().map(numbers::windowOf).toList())
					.stream()
					.map(violation -> periodViolation(sequence, violation)));
			return Stream.concat(points, periods).distinct().toList();
		}).orElse(List.of());
	}

	private static List<PointViolation> pointRules(RevealWindow window, Optional<Point> origin) {
		return origin.map(firstAppearance -> PointRules.ofWindow(window, firstAppearance))
			.orElseGet(() -> PointRules.ofWindow(window));
	}

	private static FieldViolation pointViolation(NamedWindow named, PointViolation violation) {
		String point = violation.part() == PointPart.ENDED ? NamedWindow.ENDED : NamedWindow.DISCOVERED;
		String field = named.pointField(point) + "." + axisName(violation.axis());
		return new FieldViolation(field, POINT_MESSAGES.get(violation.rule()));
	}

	private static FieldViolation periodViolation(WindowSequence sequence, PeriodViolation violation) {
		String message = PERIOD_MESSAGES.get(violation.rule());
		if (violation.axis() != null) {
			message = ON_AXIS.formatted(message, axisName(violation.axis()));
		}
		return new FieldViolation(sequence.entryField(violation.index()), message);
	}

	private static String axisName(Axis axis) {
		return axis == Axis.CHAPTER ? CHAPTER.field() : EPISODE.field();
	}

	/**
	 * The windows as the numbers online place them, in their order, for comparing the
	 * windows of several items - e.g. the users of a Devil Fruit (3.4.8). A part with no
	 * online number is not seen on its axis: going online already requires it to be.
	 */
	public List<RevealWindow> onlineWindows(List<StoryWindow> windows) {
		List<StoryPoint> points = windows.stream().flatMap(StoryWindow::points).toList();
		AxisNumbers numbers = numbersOf(points, Set.of(VersionStatus.PUBLISHED));
		return windows.stream().map(numbers::windowOf).toList();
	}

	/**
	 * The numbers of every chapter and episode of the item, as of the latest version in
	 * these statuses; empty while one of them has none.
	 */
	private Optional<AxisNumbers> numbersOf(WindowedItem item, Collection<VersionStatus> statuses) {
		List<StoryPoint> points = item.points();
		AxisNumbers numbers = numbersOf(points, statuses);
		return Optional.of(numbers).filter(known -> known.coverAll(points));
	}

	private AxisNumbers numbersOf(List<StoryPoint> points, Collection<VersionStatus> statuses) {
		return new AxisNumbers(numbersOf(idsOf(points, StoryPoint::chapterContentId), statuses, this.chapterRepository),
				numbersOf(idsOf(points, StoryPoint::episodeContentId), statuses, this.episodeRepository));
	}

	private static Set<UUID> idsOf(List<StoryPoint> points, Function<StoryPoint, UUID> part) {
		return points.stream().map(part).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	private static Map<UUID, Integer> numbersOf(Set<UUID> contentIds, Collection<VersionStatus> statuses,
			VersionBodyRepository<? extends VersionBodyEntity> repository) {
		Map<UUID, Integer> numbers = new HashMap<>();
		if (!contentIds.isEmpty()) {
			repository.findMostRecent(contentIds, statuses).forEach(version -> {
				if (version.naturalKey().value() instanceof Integer number) {
					numbers.put(version.getContentId(), number);
				}
			});
		}
		return numbers;
	}

	/** Every chapter and episode of the windows of the item, each where it is named. */
	private static Stream<PointTarget> parts(WindowedItem item) {
		return item.windows().stream().flatMap(ReaderAxisRules::partsOf);
	}

	private static Stream<PointTarget> partsOf(NamedWindow named) {
		StoryWindow window = named.window();
		return Stream
			.of(partsOf(named.pointField(NamedWindow.DISCOVERED), window.discovered()),
					partsOf(named.pointField(NamedWindow.ENDED), window.ended()))
			.flatMap(Function.identity());
	}

	private static Stream<PointTarget> partsOf(String pointField, StoryPoint point) {
		return Stream
			.of(new PointTarget(pointField, CHAPTER, point.chapterContentId()),
					new PointTarget(pointField, EPISODE, point.episodeContentId()))
			.filter(part -> part.target() != null);
	}

	private VersionBodyRepository<?> repositoryOf(RelationDescriptor<UUID> relation) {
		return relation == CHAPTER ? this.chapterRepository : this.episodeRepository;
	}

	private static RelationDescriptor<UUID> pointPart(String name, String notLinkable) {
		return RelationDescriptor.<UUID>builder()
			.name(name)
			.target(Function.identity())
			.notLinkable(notLinkable)
			.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
			.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
			.itemsName("items")
			.build();
	}

	/**
	 * A chapter or an episode of a point, where a request names it.
	 *
	 * @param pointField the point, e.g. {@code species[1].discovered}
	 */
	private record PointTarget(String pointField, RelationDescriptor<UUID> relation, UUID target) {

		/** Prefix of the part, as {@link RelationRules#notLinkable} wants it. */
		String prefix() {
			return this.pointField + ".";
		}

		/** The part, e.g. {@code species[1].discovered.chapter}. */
		String field() {
			return prefix() + this.relation.field();
		}

	}

	/** The numbers of the chapters and the episodes, by content. */
	private record AxisNumbers(Map<UUID, Integer> chapters, Map<UUID, Integer> episodes) {

		Point pointOf(StoryPoint point) {
			return new Point(numberOf(this.chapters, point.chapterContentId()),
					numberOf(this.episodes, point.episodeContentId()));
		}

		RevealWindow windowOf(StoryWindow window) {
			return new RevealWindow(pointOf(window.discovered()), pointOf(window.ended()));
		}

		/** Whether every chapter and episode of these points has a number. */
		boolean coverAll(List<StoryPoint> points) {
			return this.chapters.keySet().containsAll(idsOf(points, StoryPoint::chapterContentId))
					&& this.episodes.keySet().containsAll(idsOf(points, StoryPoint::episodeContentId));
		}

		private static Integer numberOf(Map<UUID, Integer> numbers, UUID contentId) {
			return contentId == null ? null : numbers.get(contentId);
		}

	}

}

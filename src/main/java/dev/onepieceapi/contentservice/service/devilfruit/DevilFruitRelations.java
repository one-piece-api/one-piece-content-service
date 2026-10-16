package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RevealRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

import java.util.UUID;

/**
 * The relations of a Devil Fruit (implementation plan of the Devil Fruit, D1, D2). Its
 * type keeps the reason codes it had before the rules were made generic: the wire does
 * not change.
 */
public final class DevilFruitRelations {

	/** The Devil Fruit Type of a fruit. */
	public static final RelationDescriptor<DevilFruit> TYPE = RelationDescriptor.<DevilFruit>builder()
		.name("type")
		.target(DevilFruit::typeContentId)
		.notLinkable("must be a Devil Fruit Type with an approved version")
		.targetNotOnline(BlockReason.TYPE_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_FRUITS_LINKED)
		.itemsName("fruits")
		.build();

	/** The chapter a fruit first appears in (implementation plan of the timeline, T7). */
	public static final RelationDescriptor<DevilFruit> FIRST_APPEARANCE = FirstAppearanceRules.relation("fruits");

	/**
	 * The chapter a reveal is revealed at (implementation plan of the timeline, T9), a
	 * relation of each reveal: a violation is named after it, e.g.
	 * {@code reveals[1].revealedAt.chapter}.
	 */
	public static final RelationDescriptor<DevilFruitReveal> REVEAL_CHAPTER = RevealRules.relation("fruits");

	/** The type a reveal makes known, when it holds one (T13). */
	public static final RelationDescriptor<DevilFruitReveal> REVEAL_TYPE = RelationDescriptor
		.<DevilFruitReveal>builder()
		.name("revealType")
		.field("type")
		.target(DevilFruitRelations::revealTypeId)
		.notLinkable("must be a Devil Fruit Type with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("fruits")
		.build();

	/**
	 * The type known at the first appearance, when the initial knowledge holds one
	 * (implementation plan of the timeline, T13).
	 */
	public static final RelationDescriptor<DevilFruit> INITIAL_TYPE = RelationDescriptor.<DevilFruit>builder()
		.name("initialType")
		.field(DevilFruit.INITIAL_KNOWLEDGE_PREFIX + "type")
		.target(DevilFruitRelations::initialTypeId)
		.notLinkable("must be a Devil Fruit Type with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("fruits")
		.build();

	private DevilFruitRelations() {
	}

	private static UUID revealTypeId(DevilFruitReveal reveal) {
		return reveal.values().typeContentId();
	}

	private static UUID initialTypeId(DevilFruit fruit) {
		return fruit.initialKnowledge().typeContentId();
	}

}

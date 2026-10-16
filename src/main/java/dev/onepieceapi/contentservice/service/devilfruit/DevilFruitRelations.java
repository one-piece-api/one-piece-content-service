package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RevealRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

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

	/** The type a reveal believes, when it defines one. */
	public static final RelationDescriptor<DevilFruitReveal> REVEAL_TYPE = RelationDescriptor
		.<DevilFruitReveal>builder()
		.name("revealType")
		.field("type")
		.target(DevilFruitReveal::typeContentId)
		.notLinkable("must be a Devil Fruit Type with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("fruits")
		.build();

	private DevilFruitRelations() {
	}

}

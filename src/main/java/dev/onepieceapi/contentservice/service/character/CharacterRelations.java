package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

import java.util.UUID;
import java.util.function.Function;

/**
 * The relations of a Character (implementation plan of the Character, D8, D9): the
 * chapter and the episode it first appears in (ADR-0008), each of its species and the
 * affiliation of each of its memberships (D10), each of its Devil Fruits (D12) and each
 * of its relatives (D13). All keep their target from being retired as {@code characters}.
 */
public final class CharacterRelations {

	/** What the items pointing to a target are called in a block. */
	public static final String ITEMS_NAME = "characters";

	/** The list of species as a request names it, e.g. {@code species[1]}. */
	public static final String SPECIES_FIELD = "species";

	/** The list of memberships as a request names it, e.g. {@code affiliations[1]}. */
	public static final String AFFILIATIONS_FIELD = "affiliations";

	/** The list of Devil Fruits as a request names it, e.g. {@code devilFruits[1]}. */
	public static final String DEVIL_FRUITS_FIELD = "devilFruits";

	/** The list of relatives as a request names it, e.g. {@code relatives[1]}. */
	public static final String RELATIVES_FIELD = "relatives";

	/** The chapter a character first appears in. */
	public static final RelationDescriptor<Character> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation(ITEMS_NAME);

	/** The episode a character first appears in. */
	public static final RelationDescriptor<Character> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation(ITEMS_NAME);

	/** One species of a character: each entry of the list is the target itself. */
	public static final RelationDescriptor<UUID> SPECIES = RelationDescriptor.<UUID>builder()
		.name(SPECIES_FIELD)
		.target(Function.identity())
		.notLinkable("must be a Species with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName(ITEMS_NAME)
		.build();

	/**
	 * The affiliation of one membership of a character: each entry of the list holds it.
	 * Named in the singular, so the detail of a block reads {@code affiliationId} and
	 * {@code affiliationRomaji}, and the index {@code affiliationsIndex}.
	 */
	public static final RelationDescriptor<Membership> AFFILIATION = RelationDescriptor.<Membership>builder()
		.name("affiliation")
		.target(Membership::affiliationContentId)
		.notLinkable("must be an Affiliation with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName(ITEMS_NAME)
		.build();

	/**
	 * The fruit of one entry of the Devil Fruits of a character. Named in the singular,
	 * so the detail of a block reads {@code devilFruitId} and {@code devilFruitRomaji},
	 * and the index {@code devilFruitsIndex}.
	 */
	public static final RelationDescriptor<FruitUse> DEVIL_FRUIT = RelationDescriptor.<FruitUse>builder()
		.name("devilFruit")
		.target(FruitUse::devilFruitContentId)
		.notLinkable("must be a Devil Fruit with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName(ITEMS_NAME)
		.build();

	/**
	 * The relative of one entry of the relatives of a character. Named in the singular,
	 * so the detail of a block reads {@code relativeId} and {@code relativeRomaji}, and
	 * the index {@code relativesIndex}.
	 */
	public static final RelationDescriptor<Relative> RELATIVE = RelationDescriptor.<Relative>builder()
		.name("relative")
		.target(Relative::relativeContentId)
		.notLinkable("must be a Character with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName(ITEMS_NAME)
		.build();

	private CharacterRelations() {
	}

}

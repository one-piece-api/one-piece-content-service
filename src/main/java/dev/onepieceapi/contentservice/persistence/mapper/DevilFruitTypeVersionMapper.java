package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.TranslationEmbeddable;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From the rows of a Devil Fruit Type version - its workflow in the shared table, what it
 * says in the entity's own - to the domain.
 */
@UtilityClass
public class DevilFruitTypeVersionMapper {

	public Version<DevilFruitType> toDomain(DevilFruitTypeVersionEntity entity) {
		ContentVersionEntity workflow = entity.getVersion();
		return Version.<DevilFruitType>builder()
			.number(workflow.getVersionNumber())
			.basedOn(workflow.getBasedOnNumber())
			.status(workflow.getStatus())
			.author(UserMapper.toDomain(workflow.getAuthor()))
			.claimant(UserMapper.toDomain(workflow.getClaimant()))
			.rejectionReason(workflow.getRejectionReason())
			.body(new DevilFruitType(entity.getRomaji(), toDomain(entity.getTranslations())))
			.createdAt(workflow.getCreatedAt())
			.updatedAt(workflow.getUpdatedAt())
			.build();
	}

	/** A content from the versions of it that were loaded, in the order given. */
	public Content<DevilFruitType> toContent(UUID contentId, List<DevilFruitTypeVersionEntity> versions) {
		return new Content<>(contentId, versions.stream().map(DevilFruitTypeVersionMapper::toDomain).toList());
	}

	/**
	 * A row of the list: the content the version belongs to, represented by it.
	 * @param onlineVersionNumber the version of that content currently online, or null
	 */
	public ContentSummary<DevilFruitType> toSummary(DevilFruitTypeVersionEntity version, Integer onlineVersionNumber) {
		return new ContentSummary<>(version.getVersion().getItemId(), toDomain(version), onlineVersionNumber);
	}

	/** Sorted by language code, so the same version always reads the same way. */
	private static Map<String, DevilFruitTypeTranslation> toDomain(Map<String, TranslationEmbeddable> translations) {
		Map<String, DevilFruitTypeTranslation> byLanguage = new TreeMap<>();
		translations.forEach((language, translation) -> byLanguage.put(language, toDomain(translation)));
		return byLanguage;
	}

	private static DevilFruitTypeTranslation toDomain(TranslationEmbeddable translation) {
		return new DevilFruitTypeTranslation(translation.getName(), translation.getDescription());
	}

}

package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

/**
 * From the workflow row of a version, shared by every kind of content, to the domain:
 * what the version says is the caller's to give - read from its entity's own table, or
 * just its title where kinds are listed together.
 */
@UtilityClass
public class ContentVersionMapper {

	public <T> Version<T> toDomain(ContentVersionEntity workflow, T body) {
		return Version.<T>builder()
			.number(workflow.getVersionNumber())
			.basedOn(workflow.getBasedOnNumber())
			.status(workflow.getStatus())
			.author(UserMapper.toDomain(workflow.getAuthor()))
			.claimant(UserMapper.toDomain(workflow.getClaimant()))
			.rejectionReason(workflow.getRejectionReason())
			.body(body)
			.createdAt(workflow.getCreatedAt())
			.updatedAt(workflow.getUpdatedAt())
			.build();
	}

	/**
	 * The workflow of a new version: a draft of its author, opened now.
	 * @param basedOn the version it was opened from; null for the first one
	 */
	public ContentVersionEntity toDraft(UUID contentId, int number, Integer basedOn, User author, Instant now) {
		return ContentVersionEntity.builder()
			.contentId(contentId)
			.versionNumber(number)
			.basedOnNumber(basedOn)
			.author(UserMapper.toEmbeddable(author))
			.status(VersionStatus.DRAFT)
			.createdAt(now)
			.updatedAt(now)
			.build();
	}

}

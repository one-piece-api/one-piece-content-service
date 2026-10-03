package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import lombok.experimental.UtilityClass;

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

}

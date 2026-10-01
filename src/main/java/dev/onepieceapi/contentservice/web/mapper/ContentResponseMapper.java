package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.web.dto.response.ContentResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.UserResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionEventResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionSummaryResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Function;

/**
 * From the domain to the response bodies shared by every kind of content: the workflow
 * part is mapped here, once; what a version says is mapped by the function the caller
 * passes in, e.g. {@code DevilFruitTypeResponseMapper::toResponse}.
 */
@UtilityClass
public class ContentResponseMapper {

	public ContentResponse toContentResponse(Content<?> content) {
		List<VersionSummaryResponse> versions = content.versions()
			.stream()
			.map(ContentResponseMapper::toVersionSummaryResponse)
			.toList();
		return new ContentResponse(content.id(), content.onlineVersionNumber().orElse(null), versions);
	}

	/**
	 * A row of the list.
	 * @param toBody how to turn what the version says into what the row shows of it
	 */
	public <T, R> ContentSummaryResponse<R> toSummaryResponse(ContentSummary<T> summary, Function<T, R> toBody) {
		Version<T> version = summary.version();
		return ContentSummaryResponse.<R>builder()
			.id(summary.contentId())
			.versionNumber(version.number())
			.status(version.status())
			.author(toUserResponse(version.author()))
			.updatedAt(version.updatedAt())
			.onlineVersionNumber(summary.onlineVersionNumber())
			.body(toBody.apply(version.body()))
			.build();
	}

	/**
	 * A version with what it says.
	 * @param toBody how to turn what the version says into its response body
	 */
	public <T, R> VersionResponse<R> toVersionResponse(Version<T> version, Function<T, R> toBody) {
		return VersionResponse.<R>builder()
			.number(version.number())
			.status(version.status())
			.author(toUserResponse(version.author()))
			.basedOn(version.basedOn())
			.claimant(toUserResponse(version.claimant()))
			.everPublished(version.status().everPublished())
			.rejectionReason(version.rejectionReason())
			.body(toBody.apply(version.body()))
			.createdAt(version.createdAt())
			.updatedAt(version.updatedAt())
			.build();
	}

	public VersionSummaryResponse toVersionSummaryResponse(Version<?> version) {
		return VersionSummaryResponse.builder()
			.number(version.number())
			.status(version.status())
			.author(toUserResponse(version.author()))
			.basedOn(version.basedOn())
			.claimant(toUserResponse(version.claimant()))
			.everPublished(version.status().everPublished())
			.createdAt(version.createdAt())
			.updatedAt(version.updatedAt())
			.build();
	}

	public VersionEventResponse toEventResponse(VersionEvent event) {
		UserResponse actor = toUserResponse(event.actor());
		return new VersionEventResponse(event.action(), actor, event.detail(), event.occurredAt());
	}

	/** Null stays null: a version nobody holds has no claimant. */
	public UserResponse toUserResponse(User user) {
		return user == null ? null : new UserResponse(user.id(), user.username(), user.email());
	}

}

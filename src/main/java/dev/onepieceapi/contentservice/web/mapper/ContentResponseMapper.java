package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentListSummary;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.web.dto.response.BlockedActionResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentListSummaryResponse;
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
			.allowedActions(List.copyOf(summary.allowedActions()))
			.overrideActions(List.copyOf(summary.overrideActions()))
			.build();
	}

	/**
	 * A version with what it says and what the caller may do with it.
	 * @param toBody how to turn what the version says into its response body
	 */
	public <T, R> VersionResponse<R> toVersionResponse(VersionAccess<T> access, Function<T, R> toBody) {
		Version<T> version = access.version();
		return VersionResponse.<R>builder()
			.number(version.number())
			.status(version.status())
			.author(toUserResponse(version.author()))
			.basedOn(version.basedOn())
			.claimant(toUserResponse(version.claimant()))
			.everPublished(version.everPublished())
			.rejectionReason(version.rejectionReason())
			.body(toBody.apply(version.body()))
			.allowedActions(List.copyOf(access.allowedActions()))
			.overrideActions(List.copyOf(access.overrideActions()))
			.blockedActions(access.blockedActions().stream().map(ContentResponseMapper::toBlockedResponse).toList())
			.createdAt(version.createdAt())
			.updatedAt(version.updatedAt())
			.build();
	}

	private static BlockedActionResponse toBlockedResponse(BlockedAction blocked) {
		return new BlockedActionResponse(blocked.action(), blocked.reason(), blocked.detail());
	}

	/** A link of the chain: the workflow of a version, without what it says. */
	public VersionSummaryResponse toVersionSummaryResponse(VersionAccess<?> access) {
		Version<?> version = access.version();
		return VersionSummaryResponse.builder()
			.number(version.number())
			.status(version.status())
			.author(toUserResponse(version.author()))
			.basedOn(version.basedOn())
			.claimant(toUserResponse(version.claimant()))
			.everPublished(version.everPublished())
			.allowedActions(List.copyOf(access.allowedActions()))
			.overrideActions(List.copyOf(access.overrideActions()))
			.createdAt(version.createdAt())
			.updatedAt(version.updatedAt())
			.build();
	}

	public ContentListSummaryResponse toListSummaryResponse(ContentListSummary summary) {
		return new ContentListSummaryResponse(summary.total(), summary.mine(), List.copyOf(summary.statuses()));
	}

	public VersionEventResponse toEventResponse(VersionEvent event) {
		return VersionEventResponse.builder()
			.action(event.action())
			.actor(toUserResponse(event.actor()))
			.detail(event.detail())
			.override(event.override())
			.occurredAt(event.occurredAt())
			.build();
	}

	/** Null stays null: a version nobody holds has no claimant. */
	public UserResponse toUserResponse(User user) {
		return user == null ? null : new UserResponse(user.id(), user.username(), user.email());
	}

}

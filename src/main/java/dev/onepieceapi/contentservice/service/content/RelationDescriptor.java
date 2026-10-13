package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import lombok.Builder;
import lombok.NonNull;

import java.util.UUID;
import java.util.function.Function;

/**
 * One relation from an entity to another (docs/user-flows/content-editorial-workflow.md
 * 3.4): what {@link RelationRules} needs to apply the same rules to every relation - the
 * type of a fruit, the arc of a chapter. It points to the related content, not to one of
 * its versions; whether it is required is said by the completeness of the entity
 * ({@code ContentBody#missingFields}).
 *
 * @param <T> what a version of the entity holding the relation says
 * @param name the relation as a request names it, e.g. {@code type}; also what names it
 * in the detail of a block ({@code typeId})
 * @param target the related content a version points to; {@code null} while none
 * @param notLinkable why a save is refused when the related content is not linkable
 * @param targetNotOnline why going online is refused while the related content is not
 * @param onlineItemsLinked why retiring the related content is refused while online items
 * point to it
 * @param itemsName what the items pointing to it are called in that block, e.g.
 * {@code fruits}
 */
@Builder
public record RelationDescriptor<T>(@NonNull String name, @NonNull Function<T, UUID> target,
		@NonNull String notLinkable, @NonNull BlockReason targetNotOnline, @NonNull BlockReason onlineItemsLinked,
		@NonNull String itemsName) {

	/** The related content this version points to; {@code null} while none. */
	public UUID targetOf(T body) {
		return this.target.apply(body);
	}

}

package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import lombok.Builder;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One relation from an entity to another (docs/user-flows/content-editorial-workflow.md
 * 3.4): what {@link RelationRules} needs to apply the same rules to every relation - the
 * type of a fruit, the arc of a chapter, the Devil Fruits taking part in an event. It
 * points to the related contents, not to one of their versions; whether it is required is
 * said by the completeness of the entity ({@code ContentBody#missingFields}). Built with
 * {@code target} for a relation to one content, with {@code targets} for a relation to
 * many (implementation plan of the timeline, TL2).
 *
 * @param <T> what a version of the entity holding the relation says
 * @param name the relation as a request names it, e.g. {@code type}; also what names it
 * in the detail of a block ({@code typeId})
 * @param field the relation as a request names it when it is a field of a part of the
 * body, e.g. {@code firstAppearance.chapter} (implementation plan of the timeline, TL1);
 * left out, the same as {@code name}
 * @param targets the related contents a version points to, in no particular order; empty
 * while none
 * @param toMany whether the version may point to many contents: a violation then names
 * the one at fault, e.g. {@code participants.devilFruits[<id>]}
 * @param notLinkable why a save is refused when the related content is not linkable
 * @param targetNotOnline why going online is refused while the related content is not
 * @param onlineItemsLinked why retiring the related content is refused while online items
 * point to it
 * @param itemsName what the items pointing to it are called in that block, e.g.
 * {@code fruits}
 */
@Builder
public record RelationDescriptor<T>(@NonNull String name, String field, @NonNull Function<T, List<UUID>> targets,
		boolean toMany, @NonNull String notLinkable, @NonNull BlockReason targetNotOnline,
		@NonNull BlockReason onlineItemsLinked, @NonNull String itemsName) {

	public RelationDescriptor {
		field = field == null ? name : field;
	}

	/** The related contents this version points to; empty while none. */
	public List<UUID> targetsOf(T body) {
		return this.targets.apply(body);
	}

	/** The field of a violation about this related content. */
	String fieldOf(UUID target) {
		return this.toMany ? "%s[%s]".formatted(this.field, target) : this.field;
	}

	/** Lombok fills in the rest; these two say how many contents the relation holds. */
	public static class RelationDescriptorBuilder<T> {

		/** A relation to one content, {@code null} while none. */
		public RelationDescriptorBuilder<T> target(Function<T, UUID> target) {
			this.targets = body -> Stream.ofNullable(target.apply(body)).toList();
			this.toMany = false;
			return this;
		}

		/** A relation to many contents, none while the collection is empty. */
		public RelationDescriptorBuilder<T> targets(Function<T, ? extends Collection<UUID>> targets) {
			this.targets = body -> List.copyOf(targets.apply(body));
			this.toMany = true;
			return this;
		}

	}

}

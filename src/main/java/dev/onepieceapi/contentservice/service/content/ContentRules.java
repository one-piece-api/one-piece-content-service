package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;

import java.util.Optional;
import java.util.UUID;

/**
 * The rules of one entity about its data, on top of the transition rules every entity
 * shares (implementation plan of the Devil Fruit, D2, D3): e.g. a fruit goes online only
 * while its type is. Plugged into the generic service through the entity's
 * {@link ContentDefinition} (Strategy pattern); an entity with none uses {@link #none()}.
 *
 * @param <T> what a version of the entity says
 */
public interface ContentRules<T> {

	/** The rules of an entity that has none: nothing is ever blocked. */
	@SuppressWarnings("unchecked")
	static <T> ContentRules<T> none() {
		return (ContentRules<T>) NoRules.INSTANCE;
	}

	/**
	 * Why this action is refused, if it is. Only reads: it answers both the endpoint,
	 * which refuses with {@code 409}, and the {@code blockedActions} of a response, so
	 * the two can never disagree.
	 */
	default Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<T> version) {
		return Optional.empty();
	}

	/**
	 * Called before an action that changes something, and before {@link #blockOf}: takes
	 * the lock that keeps what the rule reads from changing until the action is written.
	 */
	default void lockBefore(VersionAction action, UUID contentId, Version<T> version) {
	}

}

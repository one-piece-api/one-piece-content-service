package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Map;

/**
 * An action the transition rules allow the caller but an entity's rule refuses
 * (implementation plan of the Devil Fruit, D3): the client shows it disabled, with the
 * reason, instead of letting a click end in {@code 409}.
 */
public record BlockedAction(VersionAction action, BlockReason reason, Map<String, Object> detail) {

	public static BlockedAction of(VersionAction action, ActionBlock block) {
		return new BlockedAction(action, block.reason(), block.detail());
	}

}

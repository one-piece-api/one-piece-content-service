package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;

import java.util.Map;

/**
 * An action the caller is allowed but the entity's own rules refuse: the client shows it
 * disabled, saying why, instead of letting a click end in {@code 409}.
 *
 * @param reason why, from a closed set the client translates
 * @param detail the values that explain it, e.g. the type that is not online
 */
public record BlockedActionResponse(VersionAction action, BlockReason reason, Map<String, Object> detail) {

}

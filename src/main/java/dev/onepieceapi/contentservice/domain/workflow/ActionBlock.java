package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Map;

/**
 * What an entity's rule answers when it refuses an action: why, and the values that
 * explain it (e.g. the name of the type that is not online, how many fruits are).
 *
 * @param detail the parameters of the explanation, named as the client reads them
 */
public record ActionBlock(BlockReason reason, Map<String, Object> detail) {

}

package dev.onepieceapi.contentservice.web.dto.request;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;

/**
 * The filters of a dashboard status page, as query parameters. All optional and
 * combinable.
 *
 * @param entity only contents of this kind
 * @param author the username of the author of the version shown
 * @param mine only the caller's drafts, or the reviews they hold; ignored on any other
 * status
 */
public record StatusPageRequest(EntityType entity, String author, Boolean mine) {

}

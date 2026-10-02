package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;

import java.util.List;

/**
 * The numbers and options an entity list shows around its rows, whatever filter is on.
 *
 * @param total the contents the caller sees
 * @param mine those among them shown by a version the caller authored
 * @param statuses the statuses the caller may filter by, in workflow order
 */
public record ContentListSummaryResponse(long total, long mine, List<VersionStatus> statuses) {

}

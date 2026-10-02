package dev.onepieceapi.contentservice.domain.workflow;

import java.util.Set;

/**
 * What a caller needs to know about an entity section before any filter is applied
 * (UF-CNT-18): how big it is for them, how much of it is theirs, and which statuses they
 * may filter by.
 *
 * @param total the contents the caller sees
 * @param mine those among them shown by a version the caller authored
 * @param statuses the statuses visible to the caller
 */
public record ContentListSummary(long total, long mine, Set<VersionStatus> statuses) {

}

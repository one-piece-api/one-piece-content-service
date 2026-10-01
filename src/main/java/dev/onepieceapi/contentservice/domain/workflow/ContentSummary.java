package dev.onepieceapi.contentservice.domain.workflow;

import java.util.UUID;

/**
 * One row of an entity section (UF-CNT-18): a content represented by a single version -
 * its most recent one visible to the caller, or the one in the status being filtered on.
 *
 * @param <T> what a version of this kind of content says, e.g. a {@code DevilFruitType}
 * @param onlineVersionNumber the version currently online, which may be a different one;
 * null when nothing is online
 */
public record ContentSummary<T>(UUID contentId, Version<T> version, Integer onlineVersionNumber) {

}

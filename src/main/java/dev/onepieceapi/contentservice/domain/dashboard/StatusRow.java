package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;

import java.util.Set;
import java.util.UUID;

/**
 * One row of a dashboard status page: a content of any kind, represented by its most
 * recent version in that status, named by that version.
 *
 * @param version the version shown, its title as what it says
 * @param onlineVersionNumber the version of the content online now, which may be another
 * one; null when nothing is online
 * @param allowedActions what the caller may do with it, decided by
 * {@code TransitionPolicy} - the same as on its detail screen
 * @param overrideActions those of the allowed actions the caller may perform only through
 * {@code content:admin}
 */
public record StatusRow(EntityType entityType, UUID contentId, Version<ContentTitle> version,
		Integer onlineVersionNumber, Set<VersionAction> allowedActions, Set<VersionAction> overrideActions) {

}

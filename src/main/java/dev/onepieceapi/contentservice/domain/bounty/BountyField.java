package dev.onepieceapi.contentservice.domain.bounty;

import dev.onepieceapi.contentservice.domain.readertime.RevealableField;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * The fields of a Bounty a reveal may name (docs/user-flows/content-editorial-workflow.md
 * 3.7.1): whether it is inactive.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BountyField implements RevealableField {

	INACTIVE("inactive", false, "inactive");

	private final String fieldName;

	private final boolean alwaysKnown;

	private final String keyField;

}

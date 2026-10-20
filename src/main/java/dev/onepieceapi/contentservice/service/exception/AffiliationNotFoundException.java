package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

public class AffiliationNotFoundException extends NotFoundException {

	public AffiliationNotFoundException(UUID contentId) {
		super(ContentErrorCode.AFFILIATION_NOT_FOUND, "Affiliation " + contentId + " not found");
	}

}

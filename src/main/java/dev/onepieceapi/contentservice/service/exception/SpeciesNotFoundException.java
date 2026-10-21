package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

public class SpeciesNotFoundException extends NotFoundException {

	public SpeciesNotFoundException(UUID contentId) {
		super(ContentErrorCode.SPECIES_NOT_FOUND, "Species " + contentId + " not found");
	}

}

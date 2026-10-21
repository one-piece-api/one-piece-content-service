package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.NotFoundException;

import java.util.UUID;

public class CharacterNotFoundException extends NotFoundException {

	public CharacterNotFoundException(UUID contentId) {
		super(ContentErrorCode.CHARACTER_NOT_FOUND, "Character " + contentId + " not found");
	}

}

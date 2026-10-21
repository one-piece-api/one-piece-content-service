package dev.onepieceapi.contentservice.web.dto.response;

import java.util.Map;

/**
 * An epithet of a Character.
 *
 * @param text per language code, for the languages it is written in
 */
public record EpithetResponse(Map<String, String> text) {

}

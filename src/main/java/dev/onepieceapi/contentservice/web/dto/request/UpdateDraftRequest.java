package dev.onepieceapi.contentservice.web.dto.request;

import java.util.Map;

/**
 * Full-replace shape: the caller sends the draft's entire current state (romaji plus
 * every language tab it has touched) in one call, matching the editor's own "Salva bozza"
 * button saving both language tabs together.
 */
public record UpdateDraftRequest(String romaji, Map<String, TranslationRequest> translations) {

}

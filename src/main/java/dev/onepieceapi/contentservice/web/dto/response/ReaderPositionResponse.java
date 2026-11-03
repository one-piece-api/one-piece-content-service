package dev.onepieceapi.contentservice.web.dto.response;

import dev.onepieceapi.readertime.Axis;

/** Where the reader of a preview is: chapter or episode N. */
public record ReaderPositionResponse(Axis axis, int number) {

}

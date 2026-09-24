package dev.onepieceapi.contentservice.domain;

/**
 * A single language's {@code name}/{@code description} pair - the same shape whether it
 * came from a still-editable working revision or an immutable published version's
 * snapshot, so one type serves both instead of duplicating it per source.
 */
public record Translation(String name, String description) {

}

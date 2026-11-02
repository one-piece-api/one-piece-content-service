package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.image.GalleryUploads;
import dev.onepieceapi.contentservice.domain.readertime.Galleried;
import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.web.dto.request.GalleryImageRequest;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A gallery read from its request (workflow 3.5.1; reveals plan R5): the images in order,
 * and the files of the multipart part {@code images} attached to the entries that point
 * to them. Each entry names an {@code imageId} or an {@code upload}, never both nor
 * neither; each file is pointed to by exactly one entry.
 */
@UtilityClass
public class GalleryRequestMapper {

	/** The multipart part that carries the files of a gallery. */
	public static final String IMAGES_PART = "images";

	private static final String UPLOAD_FIELD = "%s.upload";

	private static final String FILE_FIELD = IMAGES_PART + "[%d]";

	private static final String ONE_SOURCE = "must name either imageId or upload";

	private static final String NO_SUCH_FILE = "must be the index of a file of the part " + IMAGES_PART;

	private static final String FILE_TAKEN = "points to a file another image already uses";

	private static final String FILE_UNUSED = "is not used by any image of the gallery";

	/**
	 * The images as typed, an upload still without an id: tidying up is left to the
	 * domain.
	 */
	public List<GalleryImage> toDomain(List<GalleryImageRequest> requests) {
		return entries(requests).map(GalleryRequestMapper::toDomain).toList();
	}

	/**
	 * The file of each entry that names an upload; every mismatch between entries and
	 * files is refused at once, each at its field.
	 * @param files the part {@code images}; null or empty for none
	 */
	public GalleryUploads toUploads(List<GalleryImageRequest> requests, List<MultipartFile> files) {
		List<MultipartFile> sent = Optional.ofNullable(files).orElseGet(List::of);
		List<GalleryImageRequest> images = entries(requests).toList();
		List<FieldViolation> violations = new ArrayList<>();
		Map<Integer, Integer> fileOfEntry = new HashMap<>();
		Map<Integer, Integer> entryOfFile = new HashMap<>();
		for (int index = 0; index < images.size(); index++) {
			GalleryImageRequest image = images.get(index);
			String field = Galleried.entryField(index);
			if ((image.imageId() == null) == (image.upload() == null)) {
				violations.add(new FieldViolation(field, ONE_SOURCE));
			}
			else if (image.upload() != null) {
				attach(index, image.upload(), sent.size(), fileOfEntry, entryOfFile, violations);
			}
		}
		for (int file = 0; file < sent.size(); file++) {
			if (!entryOfFile.containsKey(file)) {
				violations.add(new FieldViolation(FILE_FIELD.formatted(file), FILE_UNUSED));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
		Map<Integer, byte[]> byEntry = new HashMap<>();
		fileOfEntry.forEach((entry, file) -> byEntry.put(entry, bytesOf(sent.get(file))));
		return new GalleryUploads(byEntry);
	}

	private static void attach(int entry, int file, int fileCount, Map<Integer, Integer> fileOfEntry,
			Map<Integer, Integer> entryOfFile, List<FieldViolation> violations) {
		String field = UPLOAD_FIELD.formatted(Galleried.entryField(entry));
		if (file >= fileCount) {
			violations.add(new FieldViolation(field, NO_SUCH_FILE));
		}
		else if (entryOfFile.putIfAbsent(file, entry) != null) {
			violations.add(new FieldViolation(field, FILE_TAKEN));
		}
		else {
			fileOfEntry.put(entry, file);
		}
	}

	/** An upload has no id until it is stored. */
	private static GalleryImage toDomain(GalleryImageRequest image) {
		String imageId = image.upload() == null ? image.imageId() : null;
		return new GalleryImage(imageId, image.kind(), FirstAppearanceMapper.toWindow(image), written(image.caption()));
	}

	private static Stream<GalleryImageRequest> entries(List<GalleryImageRequest> requests) {
		return Optional.ofNullable(requests).orElseGet(List::of).stream();
	}

	/** A language sent as null is a language with nothing written. */
	private static Map<String, String> written(Map<String, String> values) {
		Map<String, String> written = new HashMap<>();
		Optional.ofNullable(values).orElseGet(Map::of).forEach((language, value) -> {
			if (value != null) {
				written.put(language, value);
			}
		});
		return written;
	}

	private static byte[] bytesOf(MultipartFile upload) {
		try {
			return upload.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}

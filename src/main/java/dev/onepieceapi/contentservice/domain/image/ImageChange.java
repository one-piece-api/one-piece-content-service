package dev.onepieceapi.contentservice.domain.image;

/**
 * What a save does to the image of a draft (implementation plan of the Devil Fruit, D5):
 * keeps it when nothing is said about it, removes it when asked, or replaces it with an
 * upload.
 */
public sealed interface ImageChange {

	ImageChange KEEP = new Keep();

	ImageChange REMOVE = new Remove();

	static ImageChange replaceWith(byte[] upload) {
		return new Replace(upload);
	}

	record Keep() implements ImageChange {
	}

	record Remove() implements ImageChange {
	}

	/** An upload, still to be checked and normalized. */
	record Replace(byte[] upload) implements ImageChange {
	}

}

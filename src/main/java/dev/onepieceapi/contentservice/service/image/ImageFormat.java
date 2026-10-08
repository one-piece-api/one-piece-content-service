package dev.onepieceapi.contentservice.service.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The formats an upload may come in (implementation plan of the Devil Fruit, D6),
 * recognized by their leading bytes - never by file name or {@code Content-Type} - and
 * only when static: an animated PNG is not recognized. PNG only for now: WebP waits for a
 * Java reader that decodes the transparency of lossless WebP right (TwelveMonkeys 3.15.3
 * reads its transparent pixels as alpha 3), a new constant here when it does.
 */
public enum ImageFormat {

	PNG {
		@Override
		boolean recognizes(byte[] bytes) {
			return startsWith(bytes, PNG_SIGNATURE) && !hasPngChunkBeforeData(bytes, "acTL");
		}

		@Override
		public ImageReader newReader() {
			return ImageIO.getImageReadersByFormatName("png").next();
		}
	};

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	public static Optional<ImageFormat> of(byte[] bytes) {
		return Arrays.stream(values()).filter(format -> format.recognizes(bytes)).findFirst();
	}

	abstract boolean recognizes(byte[] bytes);

	/** A reader of this format. */
	public abstract ImageReader newReader();

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static String ascii(byte[] bytes, int offset, int length) {
		return new String(bytes, offset, length, StandardCharsets.US_ASCII);
	}

	/** Walks the chunks (length, type, data, CRC) up to the first image data. */
	private static boolean hasPngChunkBeforeData(byte[] bytes, String type) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		int offset = PNG_SIGNATURE.length;
		while (offset + 8 <= bytes.length) {
			String chunk = ascii(bytes, offset + 4, 4);
			if (chunk.equals(type)) {
				return true;
			}
			if (chunk.equals("IDAT")) {
				return false;
			}
			long next = offset + 12L + Integer.toUnsignedLong(buffer.getInt(offset));
			if (next > bytes.length) {
				return false;
			}
			offset = (int) next;
		}
		return false;
	}

}

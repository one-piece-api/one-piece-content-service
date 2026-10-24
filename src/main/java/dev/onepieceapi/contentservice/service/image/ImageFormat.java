package dev.onepieceapi.contentservice.service.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * The formats an upload may come in (implementation plan of the Devil Fruit, D6;
 * implementation plan WebP, W-D4), recognized by their leading bytes - never by file name
 * or {@code Content-Type} - and only when static: an animated PNG or WebP is not
 * recognized. Whatever the format, the stored image is WebP ({@link ImageEncoder}).
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
	},

	/**
	 * Lossy or lossless, with or without alpha (RFC 9649): a RIFF container whose first
	 * chunk is the image itself, or the extended header without its animation flag.
	 */
	WEBP {
		@Override
		boolean recognizes(byte[] bytes) {
			if (bytes.length < WEBP_HEADER_LENGTH || !ascii(bytes, 0, 4).equals("RIFF")
					|| !ascii(bytes, 8, 4).equals("WEBP")) {
				return false;
			}
			String firstChunk = ascii(bytes, 12, 4);
			return firstChunk.equals("VP8 ") || firstChunk.equals("VP8L")
					|| (firstChunk.equals("VP8X") && (bytes[WEBP_EXTENDED_FLAGS] & WEBP_ANIMATION_FLAG) == 0);
		}

		@Override
		public ImageReader newReader() {
			return ImageIO.getImageReadersByMIMEType(WebpImageEncoder.CONTENT_TYPE).next();
		}
	};

	private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	/** "RIFF", its size, "WEBP", the first chunk's type and size, the extended flags. */
	private static final int WEBP_HEADER_LENGTH = 21;

	private static final int WEBP_EXTENDED_FLAGS = 20;

	private static final int WEBP_ANIMATION_FLAG = 0x02;

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

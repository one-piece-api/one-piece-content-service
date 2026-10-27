# ADR-0010: Images uploaded in WebP or PNG, stored as lossy WebP

Revisits the "WebP" alternative of ADR-0006; the rest of ADR-0006 stands.

## Context

ADR-0006 stored every image as PNG and accepted only PNG uploads: no Java library wrote WebP, and the one
reader tried (TwelveMonkeys 3.15.3) decoded the transparent pixels of lossless WebP as alpha 3. Editors'
sources are often WebP (volume 1's cover was converted by hand), and PNG covers weigh 0.5-0.6 MB each, so a
page of covers is several MB (`docs/implementation-plan-webp/` in `one-piece-api`, W0 spike).

## Decision

- **One ImageIO plugin for both directions:** `com.github.usefulness:webp-imageio` (Apache-2.0, libwebp
  1.6.0 bundled as native code for Linux x86_64/aarch64 glibc, Windows, macOS). It plugs into
  `ImageFormat.newReader()` and the `ImageEncoder` Strategy as they are. Its lossless decoding keeps alpha
  exact (checked against Google's reference files).
- **Upload:** static PNG or static WebP (RFC 9649: a `VP8 `/`VP8L` image, or `VP8X` without the animation
  flag), recognized by its bytes. No JPEG: the JDK reader fails on CMYK and ignores EXIF orientation; it is
  one more `ImageFormat` constant if ever needed.
- **Stored:** lossy WebP for every profile (`WebpImageEncoder`), quality 90, method 4
  (`content.images.webp.*`); alpha stays lossless, so transparency is exact. `ImageController` serves the
  stored `content_type` (`image/webp`, `image/png` for rows stored before).
- **Runtime:** base image `eclipse-temurin:25-jre` (Ubuntu, glibc) instead of `-alpine`. The plugin extracts
  its library into `java.io.tmpdir` at first use (`/tmp` must stay writable); `WebpImageEncoder` loads it at
  startup, so a runtime that cannot fails to start. Native access is allowed explicitly
  (`Enable-Native-Access: ALL-UNNAMED` in the jar manifest, `--enable-native-access` for tests).

## Alternatives considered

- **Lossless WebP:** bit-exact, 40-45 % smaller than PNG, but 4x the lossy size; no visible gain at the
  sizes served (cover 333 KB lossless vs 81 KB lossy, PNG 574 KB).
- **One mode per profile** (lossless for transparent profiles): more configuration, no visible difference.
- **`cwebp` run as a process:** an external process and temp files per upload.
- **Cloudflare Polish:** paid plan, against the Always Free rule.
- **Staying on Alpine:** musl happens to load the glibc build, but upstream ships no musl build and does not
  support it (issue #303).

## Consequences

- Images about 85-90 % smaller than PNG; a re-encoding is generation loss, so a future change of a profile's
  canvas re-encodes an already lossy picture.
- Base image +40 MB compressed (GHCR, no Oracle cost); `kotlin-stdlib` comes with the plugin (pinned to the
  plugin's 2.4.0 over Spring Boot's managed version).
- Native code in the service: a new platform (e.g. musl, another architecture) needs the plugin's support.
- PNG rows stored before keep working until replaced (plan WebP, W5).

## Addendum - Volume canvas at the real covers' ratio (plan WebP, W5)

- **Context:** the volume canvas was 420 x 600 (7:10). Real covers measure 0.628-0.636 (11 volumes from 1 to
  110 on One Piece Wiki), so each one was scaled to about 381 x 600 and centred between two transparent bands.
- **Decision:** canvas 420 x 665 (12:19, about 0.632), minimum 504 x 800, tolerance unchanged (10 %). A real
  cover fills it to within 2 px.
- **Alternatives:** 420 x 672 (5:8, a simpler ratio for editors, bands up to 6 px); filling the canvas by
  cropping (against "never cropped"). The bounty poster keeps 420 x 600.
- **Consequences:** covers stored at 420 x 600 keep that size until replaced (only volume 1's, in review); an
  upload at 7:10 is now 10.8 % off the canvas and refused as `CONTENT_IMAGE_WRONG_RATIO`.

## Addendum - Bounty poster at the size of the loaded posters (2026-10-27)

- **Context:** the posters loaded by the AI editor are 350 x 520 (0.673); the profile asked for at least 560 x 800
  and a 420 x 600 canvas, so none was accepted. The statement above that the bounty poster keeps 420 x 600 is
  superseded.
- **Decision:** canvas 350 x 520, minimum 350 x 520, tolerance unchanged (10 %). A poster is never enlarged, so
  asking more than the real size would refuse every loaded poster.
- **Consequences:** posters stored at 420 x 600 keep that size until replaced; a poster at 7:10 is about 4 % off the
  canvas, within the tolerance, and is fitted with thin bands.

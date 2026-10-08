# ADR-0006: Images of contents in PostgreSQL, part of the version

## Context

A Devil Fruit has an optional image (`docs/implementation-plan-devil-fruit.md` in
`one-piece-api`, D4-D6), and later entities will have theirs. The image goes through the
same workflow as the text: a reviewer must not see a draft's image, a published version's
image must not change under it, and a version saved with an image must never point to
nothing. Scale is small (hundreds of images of a few hundred KB) and the infrastructure must
stay within OCI Always Free.

## Decision

- **Stored in PostgreSQL**, table `image` (`bytes` as `bytea`), behind the `ImageStore`
  Repository (`JpaImageStore`). The id is the SHA-256 of the stored bytes: identical images
  are one row, and an image never changes under its id.
- **Part of the version.** `devil_fruit_version.image_id` references `image` (foreign key,
  no cascade). A draft is created and edited with its image in one `multipart/form-data`
  request (part `version` the JSON, optional part `image`); no part keeps the image,
  `"removeImage": true` removes it. Text and image are saved in one transaction. A new
  version takes its base's image; two versions differing only in their image are not
  identical.
- **No orphans, no sweeping job.** In the transaction that stops using an image (replaced,
  removed, draft discarded), `ContentImages` deletes it unless another version still uses
  it. The foreign key is the guarantee: when two drafts change at once, the delete fails
  rather than leave a version pointing to nothing.
- **Strict input, normalized output.** `ImageValidator` checks the upload against the
  entity's profile (`content.images.profiles.<entity>.*`): a static PNG recognized by its
  bytes, size and pixels read from the header before decoding, ratio, least size, share of
  transparent pixels; one `422` code per reason on field `image`. `ImageNormalizer` scales
  down to the canvas, centers on transparency and re-encodes through an `ImageEncoder`
  Strategy (PNG): metadata and anything hidden in the upload are dropped.
- **Read through the service.** `GET /images/{id}` (`content:read`) answers only if a version
  visible to the caller uses the image (`VisibilityPolicy`), `404` alike otherwise;
  `Cache-Control: private, max-age=1 year, immutable` and the id as `ETag`. Responses give
  `"image": {id, url}`, the URL built from `content.images.base-url`, so moving images
  elsewhere changes no client.

## Alternatives considered

- **Object storage (OCI bucket, S3-compatible):** a new component with credentials, two
  stores to keep consistent, orphans to sweep; not needed at this scale.
- **External hosting (Cloudinary, ImageKit) or links to images elsewhere:** draft images
  public, a third party, link rot, nothing immutable.
- **A separate `POST /images` returning an id:** abandoned uploads become orphans needing a
  job, plus a visibility case for "uploaded, not yet linked".
- **WebP:** smaller files, but ImageIO cannot write it and every writer is native code
  (fragile on Alpine and ARM). Accepting WebP uploads was dropped too: TwelveMonkeys 3.15.3
  decodes the transparent pixels of lossless WebP with alpha 3, so valid uploads would be
  refused. The encoder and `ImageFormat` are where either comes back.
- **`Cache-Control: public` behind Cloudflare:** an image's visibility depends on who asks,
  so no shared cache may keep it.

## Consequences

- One store, one transaction, one backup; images count in the database size (about 0.3-0.8
  MB per image as PNG).
- Every table referencing `image` is listed in `ImageRepository.deleteIfUnreferenced` and
  asked in `ImageService`: a new entity with images adds itself there and gets its own
  profile.
- Uploaders send PNG; WebP waits for a correct Java decoder.

package org.mailoverlord.server.model;

/**
 * One leaf part of a captured message's MIME tree.
 *
 * <p>Every leaf is reported rather than only the parts that carry a filename. A part with no
 * filename is still part of the message: an HTML-only mail has no attachment at all, and the
 * reason its text is shown as raw MIME is that its only part is {@code text/html}. Listing
 * attachments alone would leave that message looking empty, which is the same invisibility this
 * replaces.
 *
 * <p>A part nested inside {@code message/rfc822} is reported as one part rather than descended
 * into, because it is an attachment in its own right rather than structure wrapping this message.
 *
 * @param filename     the filename the part declares, or null when it declares none. Metadata
 *                     only: nothing here resolves it against a filesystem
 * @param contentType  the MIME type without its parameters, so a part reads as
 *                     {@code application/pdf} rather than {@code application/pdf; name="x.pdf"}
 * @param sizeBytes    the decoded size in bytes, or null when the part reported no size. Null
 *                     is not the same as zero, which is a genuinely empty part
 * @param disposition  {@code attachment}, {@code inline}, or null when the message does not say
 * @param displayedBody whether {@link MessageDetail#body()} is this part's content
 */
public record MessagePart(
        String filename,
        String contentType,
        Long sizeBytes,
        String disposition,
        boolean displayedBody) {
}
package com.orbitcrm.attachment.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Determines the content type of an uploaded file from its leading bytes ("magic numbers").
 *
 * <p>Purpose: prevent dangerous uploads. Only an allow-list of formats is accepted - PDF, PNG, JPEG, GIF, WebP,
 * Office documents (docx, xlsx, pptx and the legacy doc, xls, ppt) and plain-text {@code .txt} / {@code .csv} files
 * (valid UTF-8 without NUL bytes) - and the file extension must agree with the detected format. HTML, SVG, scripts and
 * executables are rejected, so a stored file can never be served back as active content. The client-supplied content
 * type is never trusted.
 *
 * @author Abhay
 */
public final class FileTypeDetector {

    private static final Map<String, String> OOXML = Map.of(
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final Map<String, String> OLE = Map.of(
            "doc", "application/msword",
            "xls", "application/vnd.ms-excel",
            "ppt", "application/vnd.ms-powerpoint");

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] head, String fileName) {
        String ext = extension(fileName);
        if (startsWith(head, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return ext.equals("pdf") ? Optional.of("application/pdf") : Optional.empty();
        }
        if (startsWith(head, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return ext.equals("png") ? Optional.of("image/png") : Optional.empty();
        }
        if (startsWith(head, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return ext.equals("jpg") || ext.equals("jpeg") ? Optional.of("image/jpeg") : Optional.empty();
        }
        if (startsWith(head, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(head, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return ext.equals("gif") ? Optional.of("image/gif") : Optional.empty();
        }
        if (head.length >= 12 && startsWith(head, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(Arrays.copyOfRange(head, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return ext.equals("webp") ? Optional.of("image/webp") : Optional.empty();
        }
        if (startsWith(head, new byte[] {'P', 'K', 0x03, 0x04})) {
            return Optional.ofNullable(OOXML.get(ext));
        }
        if (startsWith(head, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
                (byte) 0xE1})) {
            return Optional.ofNullable(OLE.get(ext));
        }
        if ((ext.equals("txt") || ext.equals("csv")) && isText(head)) {
            return Optional.of(ext.equals("csv") ? "text/csv" : "text/plain");
        }
        return Optional.empty();
    }

    public static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** No NUL bytes, valid UTF-8 (ignoring a multi-byte sequence cut off at the end of the sample). */
    private static boolean isText(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return false;
            }
        }
        int end = head.length;
        while (end > 0 && end > head.length - 4 && (head[end - 1] & 0xC0) == 0x80) {
            end--;
        }
        if (end > 0 && (head[end - 1] & 0xC0) == 0xC0) {
            end--;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(head, 0, end));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

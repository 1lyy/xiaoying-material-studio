package com.xiaoying.materialstudio.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xiaoying.materialstudio.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class PrivateImageStorageTest {
    @TempDir Path root;

    @Test
    void validPngAndJpegAreDecodedAndStoredUnderRandomNames() throws Exception {
        PrivateImageStorage storage = new PrivateImageStorage(root.toString());
        var png = storage.save(file("sample.png", "image/png", image("png")),
                PrivateImageStorage.MAX_BYTES, List.of("image/png", "image/jpeg"));
        var jpeg = storage.save(file("sample.jpg", "image/jpeg", image("jpeg")),
                PrivateImageStorage.MAX_BYTES, List.of("image/png", "image/jpeg"));
        assertEquals("image/png", png.mimeType());
        assertEquals(3, png.width());
        assertEquals(2, png.height());
        assertTrue(png.storagePath().matches("uploads/[0-9a-f-]{36}\\.png"));
        assertTrue(jpeg.storagePath().matches("uploads/[0-9a-f-]{36}\\.jpg"));
        assertTrue(Files.exists(storage.resolveOwnedFile(png.storagePath())));
        assertTrue(Files.exists(storage.resolveOwnedFile(jpeg.storagePath())));
    }

    @Test
    void rejectsEmptySpoofedOversizeAndCorruptImagesWithoutLeavingFiles() throws Exception {
        PrivateImageStorage storage = new PrivateImageStorage(root.toString());
        byte[] png = image("png");
        byte[] jpeg = image("jpeg");
        assertFailure(storage, file("empty.png", "image/png", new byte[0]), 400);
        assertFailure(storage, file("fake.jpg", "image/jpeg", png), 415);
        assertFailure(storage, file("fake.png", "image/png", jpeg), 415);
        assertFailure(storage, file("fake.gif", "image/png", png), 415);
        assertFailure(storage, file("corrupt.png", "image/png", new byte[] {(byte) 0x89, 80, 78, 71}), 415);
        assertFailure(storage, file("too-many-pixels.png", "image/png", oversizedDimensions(png)), 415);
        assertFailure(storage, file("large.png", "image/png", new byte[(int) PrivateImageStorage.MAX_BYTES + 1]), 413);
        assertFailure(storage, file("too-large-for-template.png", "image/png", png), 413, png.length - 1);
        try (var uploads = Files.list(root.resolve("uploads"));
             var temporary = Files.list(root.resolve("tmp"))) {
            assertEquals(0, uploads.count());
            assertEquals(0, temporary.count());
        }
    }

    @Test
    void relativePathMustStayWithinUploadDirectoryIncludingSymlinks() throws Exception {
        PrivateImageStorage storage = new PrivateImageStorage(root.toString());
        Path outside = root.resolve("private.txt");
        Files.writeString(outside, "secret");
        assertEquals(404, assertThrows(ApiException.class,
                () -> storage.resolveOwnedFile("uploads/../../private.txt")).status().value());
        assertEquals(404, assertThrows(ApiException.class,
                () -> storage.resolveOwnedFile(outside.toString())).status().value());
        Path link = root.resolve("uploads").resolve("escape.png");
        Files.createSymbolicLink(link, outside);
        assertEquals(404, assertThrows(ApiException.class,
                () -> storage.resolveOwnedFile("uploads/escape.png")).status().value());
        assertFalse(Files.exists(root.resolve("uploads").resolve("private.txt")));
    }

    static byte[] image(String format) throws Exception {
        BufferedImage bitmap = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        bitmap.setRGB(0, 0, 0x193c69);
        bitmap.setRGB(1, 0, 0xc46734);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(bitmap, format, bytes));
        return bytes.toByteArray();
    }

    static MockMultipartFile file(String name, String mime, byte[] data) {
        return new MockMultipartFile("file", name, mime, data);
    }

    private static byte[] oversizedDimensions(byte[] original) {
        byte[] bytes = original.clone();
        // PNG IHDR width/height are big-endian at offsets 16 and 20.
        for (int offset : new int[] {16, 20}) {
            bytes[offset] = 0;
            bytes[offset + 1] = 0;
            bytes[offset + 2] = 0x17;
            bytes[offset + 3] = 0x70; // 6000 * 6000 > the 20 MP cap.
        }
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        long value = crc.getValue();
        for (int index = 0; index < 4; index++) bytes[29 + index] = (byte) (value >>> (24 - index * 8));
        return bytes;
    }

    private static void assertFailure(PrivateImageStorage storage, MockMultipartFile file, int status) throws Exception {
        assertFailure(storage, file, status, PrivateImageStorage.MAX_BYTES);
    }

    private static void assertFailure(PrivateImageStorage storage, MockMultipartFile file, int status, long limit)
            throws Exception {
        assertEquals(status, assertThrows(ApiException.class,
                () -> storage.save(file, limit, List.of("image/png", "image/jpeg"))).status().value());
    }
}

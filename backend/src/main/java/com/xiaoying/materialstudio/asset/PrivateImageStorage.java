package com.xiaoying.materialstudio.asset;

import com.xiaoying.materialstudio.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PrivateImageStorage {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final long MAX_PIXELS = 20_000_000L;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] PNG_END = {0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44,
            (byte) 0xae, 0x42, 0x60, (byte) 0x82};
    private final Path uploads;
    private final Path temporary;

    public PrivateImageStorage(@Value("${app.storage.root}") String root) throws IOException {
        Path base = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(base.resolve("uploads"));
        Files.createDirectories(base.resolve("tmp"));
        Path realBase = base.toRealPath();
        uploads = base.resolve("uploads").toRealPath();
        temporary = base.resolve("tmp").toRealPath();
        if (!uploads.startsWith(realBase) || !temporary.startsWith(realBase)
                || uploads.equals(realBase) || temporary.equals(realBase)) {
            throw new IOException("Storage directories must be below STORAGE_ROOT");
        }
    }

    public StoredImage save(MultipartFile file, long templateLimit, java.util.List<String> acceptedMimeTypes)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "请选择非空图片");
        }
        long limit = Math.min(MAX_BYTES, templateLimit);
        if (limit < 1) throw new IllegalStateException("Invalid template size limit");
        if (file.getSize() > limit) throw tooLarge();

        String declared = file.getContentType();
        String name = file.getOriginalFilename();
        if (declared == null || !acceptedMimeTypes.contains(declared)
                || !(declared.equals("image/png") || declared.equals("image/jpeg"))
                || !extensionMatches(name, declared)) throw invalidImage();

        Path temp = Files.createTempFile(temporary, "incoming-", ".part");
        try {
            long size = copyBounded(file, temp, limit);
            ImageInfo info = inspect(temp);
            if (!declared.equals(info.mimeType())) throw invalidImage();
            String suffix = declared.equals("image/png") ? ".png" : ".jpg";
            Path destination = uploads.resolve(UUID.randomUUID() + suffix).normalize();
            if (!destination.startsWith(uploads)) throw new IOException("Invalid storage destination");
            try {
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, destination);
            }
            return new StoredImage("uploads/" + destination.getFileName(), declared, size,
                    info.width(), info.height());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public Path resolveOwnedFile(String storagePath) {
        if (storagePath == null) throw notFound();
        try {
            Path relative = Path.of(storagePath);
            if (relative.isAbsolute() || relative.getNameCount() != 2
                    || !relative.getName(0).toString().equals("uploads")
                    || relative.getName(1).toString().equals(".")
                    || relative.getName(1).toString().equals("..")) throw notFound();
            Path candidate = uploads.resolve(relative.getName(1)).normalize();
            if (!candidate.startsWith(uploads)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) throw notFound();
            Path real = candidate.toRealPath();
            if (!real.startsWith(uploads) || !Files.isRegularFile(real)) throw notFound();
            return real;
        } catch (IOException | IllegalArgumentException exception) {
            throw notFound();
        }
    }

    public boolean deleteNewFile(String storagePath) {
        try {
            Files.deleteIfExists(resolveOwnedFile(storagePath));
            return true;
        } catch (ApiException | IOException ignored) {
            return false;
        }
    }

    public void verifyStored(String storagePath, String expectedMime, long expectedSize,
                             int expectedWidth, int expectedHeight) {
        Path path = resolveOwnedFile(storagePath);
        try {
            if (Files.size(path) != expectedSize || expectedSize < 1 || expectedSize > MAX_BYTES) throw notFound();
        } catch (IOException exception) {
            throw notFound();
        }
        ImageInfo info = inspect(path);
        if (!info.mimeType().equals(expectedMime) || info.width() != expectedWidth
                || info.height() != expectedHeight) throw notFound();
    }

    public Path createGeneratedTemp() throws IOException {
        return Files.createTempFile(temporary, "generated-", ".png");
    }

    public StoredImage persistGenerated(Path temp) throws IOException {
        if (temp == null || !temp.normalize().startsWith(temporary)
                || !Files.isRegularFile(temp, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid generated file");
        }
        long size = Files.size(temp);
        if (size < 1 || size > MAX_BYTES) throw new IOException("Generated file size is invalid");
        ImageInfo info = inspect(temp);
        if (!info.mimeType().equals("image/png")) throw new IOException("Generated file is not PNG");
        Path destination = uploads.resolve(UUID.randomUUID() + ".png").normalize();
        if (!destination.startsWith(uploads)) throw new IOException("Invalid generated destination");
        try {
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, destination);
        }
        return new StoredImage("uploads/" + destination.getFileName(), "image/png", size,
                info.width(), info.height());
    }

    public void deleteGeneratedTemp(Path temp) {
        if (temp == null || !temp.normalize().startsWith(temporary)) return;
        try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
    }

    private static long copyBounded(MultipartFile file, Path temp, long limit) throws IOException {
        long total = 0;
        try (InputStream input = file.getInputStream(); var output = Files.newOutputStream(temp)) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > limit) throw tooLarge();
                output.write(chunk, 0, read);
            }
        }
        if (total == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "请选择非空图片");
        return total;
    }

    private static ImageInfo inspect(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String signature;
            if (bytes.length >= PNG_HEADER.length + PNG_END.length
                    && Arrays.equals(Arrays.copyOf(bytes, PNG_HEADER.length), PNG_HEADER)
                    && Arrays.equals(Arrays.copyOfRange(bytes, bytes.length - PNG_END.length, bytes.length), PNG_END)) {
                signature = "image/png";
            } else if (bytes.length >= 5 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff
                    && (bytes[bytes.length - 2] & 0xff) == 0xff
                    && (bytes[bytes.length - 1] & 0xff) == 0xd9) {
                signature = "image/jpeg";
            } else throw invalidImage();
            try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
                if (stream == null) throw invalidImage();
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream, false, false);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String decodedMime = format.equals("png") ? "image/png"
                            : (format.equals("jpeg") || format.equals("jpg") ? "image/jpeg" : "");
                    if (!signature.equals(decodedMime)) throw invalidImage();
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) throw invalidImage();
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                        throw invalidImage();
                    }
                    return new ImageInfo(decodedMime, width, height);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ApiException api) throw api;
            throw invalidImage();
        }
    }

    private static boolean extensionMatches(String name, String mime) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return mime.equals("image/png") ? lower.endsWith(".png")
                : lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片超过模板允许的大小");
    }

    private static ApiException invalidImage() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_IMAGE", "仅支持真实可读取的 JPEG 或 PNG 图片");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "素材不存在");
    }

    public record StoredImage(String storagePath, String mimeType, long sizeBytes, int width, int height) {
    }

    private record ImageInfo(String mimeType, int width, int height) {
    }
}

package com.xiaoying.materialstudio.task;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class DemoImageGenerator implements TaskImageGenerator {
    @Override
    public void generate(List<Path> inputImages, Path output) throws IOException {
        if (inputImages.isEmpty()) throw new IOException("No input images");
        BufferedImage canvas = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(new Color(248, 245, 238));
            graphics.fillRect(0, 0, 900, 600);
            int tileWidth = 820 / inputImages.size();
            for (int i = 0; i < inputImages.size(); i++) {
                BufferedImage input = ImageIO.read(inputImages.get(i).toFile());
                if (input == null) throw new IOException("Input image unavailable");
                graphics.drawImage(input, 40 + i * tileWidth, 80, tileWidth - 10, 380, null);
            }
            graphics.setColor(new Color(28, 54, 75));
            graphics.fillRoundRect(40, 500, 250, 28, 14, 14);
            graphics.setColor(new Color(112, 157, 171));
            graphics.fillRoundRect(305, 500, 120, 28, 14, 14);
        } finally {
            graphics.dispose();
        }
        try (var stream = Files.newOutputStream(output)) {
            if (!ImageIO.write(canvas, "png", stream)) throw new IOException("PNG encoder unavailable");
        }
    }
}

package com.psymate.directory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;

/**
 * Vérifie la signature (JPEG ou PNG), recadre en carré, réduit à 512 px et réécrit en JPEG. La
 * réécriture via ImageIO sans métadonnées supprime l'EXIF (et toute autre métadonnée).
 */
final class PhotoProcessor {

  static final int MAX_BYTES = 5 * 1024 * 1024;
  static final int SIZE = 512;
  private static final int MAX_PIXELS_SIDE = 12_000;

  private PhotoProcessor() {}

  static byte[] process(byte[] input) {
    if (input == null || input.length == 0) {
      throw ProfileException.of(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_INVALID", "Fichier vide.");
    }
    if (input.length > MAX_BYTES) {
      throw ProfileException.of(
          HttpStatus.CONTENT_TOO_LARGE, "PHOTO_TOO_LARGE", "La photo dépasse 5 Mo.");
    }
    if (!isJpeg(input) && !isPng(input)) {
      throw ProfileException.of(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "PHOTO_UNSUPPORTED",
          "Seuls les formats JPEG et PNG sont acceptés.");
    }
    try {
      BufferedImage src = read(input);
      int side = Math.min(src.getWidth(), src.getHeight());
      BufferedImage square =
          src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
      int target = Math.min(side, SIZE);
      BufferedImage out = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      try {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, target, target);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(square, 0, 0, target, target, null);
      } finally {
        g.dispose();
      }
      return writeJpeg(out);
    } catch (IOException | RuntimeException e) {
      if (e instanceof ProfileException pe) {
        throw pe;
      }
      throw ProfileException.of(
          HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_INVALID", "Image illisible ou corrompue.");
    }
  }

  private static BufferedImage read(byte[] input) throws IOException {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw new IOException("format inconnu");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        if (reader.getWidth(0) > MAX_PIXELS_SIDE || reader.getHeight(0) > MAX_PIXELS_SIDE) {
          throw ProfileException.of(
              HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_INVALID", "Dimensions trop grandes.");
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    }
  }

  private static byte[] writeJpeg(BufferedImage image) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
        writer.setOutput(out);
        // Métadonnées absentes (null) : ni EXIF, ni GPS, ni commentaire.
        writer.write(null, new IIOImage(image, null, null), param);
      }
      return bytes.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  static boolean isJpeg(byte[] b) {
    return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
  }

  static boolean isPng(byte[] b) {
    int[] sig = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    if (b.length < sig.length) {
      return false;
    }
    for (int i = 0; i < sig.length; i++) {
      if ((b[i] & 0xFF) != sig[i]) {
        return false;
      }
    }
    return true;
  }
}

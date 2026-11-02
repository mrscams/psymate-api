package com.psymate.identity.verification.service;

import com.psymate.identity.verification.model.VerificationException;
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
 * Contrôle d'un justificatif : taille, signature du fichier (PDF, JPEG ou PNG, jamais l'extension
 * ni le Content-Type déclarés) et retrait des métadonnées des images (EXIF, GPS, commentaires) par
 * réécriture ImageIO sans métadonnées. Les PDF sont conservés tels quels.
 */
final class DocumentSanitizer {

  static final int MAX_BYTES = 10 * 1024 * 1024;
  static final long MAX_PIXELS = 25_000_000L;

  /** Contenu nettoyé et type MIME détecté par signature. */
  record Clean(byte[] bytes, String mime) {}

  private DocumentSanitizer() {}

  static Clean clean(byte[] input) {
    if (input == null || input.length == 0) {
      throw VerificationException.of(
          HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_INVALID", "verification.error.empty");
    }
    if (input.length > MAX_BYTES) {
      throw tooLarge();
    }
    String mime = detect(input);
    if (mime == null) {
      throw VerificationException.of(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "DOCUMENT_UNSUPPORTED",
          "verification.error.unsupported");
    }
    if ("application/pdf".equals(mime)) {
      return new Clean(input, mime);
    }
    byte[] out = reencode(input, mime);
    if (out.length > MAX_BYTES) {
      throw tooLarge();
    }
    return new Clean(out, mime);
  }

  static String detect(byte[] b) {
    if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
      return "application/pdf";
    }
    if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
      return "image/jpeg";
    }
    int[] png = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    if (b.length >= png.length) {
      boolean match = true;
      for (int i = 0; i < png.length && match; i++) {
        match = (b[i] & 0xFF) == png[i];
      }
      if (match) {
        return "image/png";
      }
    }
    return null;
  }

  static VerificationException tooLarge() {
    return VerificationException.of(
        HttpStatus.CONTENT_TOO_LARGE, "DOCUMENT_TOO_LARGE", "verification.error.too_large");
  }

  private static byte[] reencode(byte[] input, String mime) {
    boolean jpeg = "image/jpeg".equals(mime);
    try {
      BufferedImage src = read(input);
      BufferedImage image = src;
      if (jpeg && src.getColorModel().hasAlpha()) {
        image = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        image.createGraphics().drawImage(src, 0, 0, java.awt.Color.WHITE, null);
      }
      if (jpeg) {
        image = applyOrientation(image, exifOrientation(input));
      }
      ImageWriter writer = ImageIO.getImageWritersByFormatName(jpeg ? "jpeg" : "png").next();
      try {
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (jpeg) {
          param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
          param.setCompressionQuality(0.92f);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
          writer.setOutput(out);
          // Métadonnées nulles : ni EXIF, ni GPS, ni commentaire.
          writer.write(null, new IIOImage(image, null, null), param);
        }
        return bytes.toByteArray();
      } finally {
        writer.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof VerificationException ve) {
        throw ve;
      }
      throw VerificationException.of(
          HttpStatus.UNPROCESSABLE_CONTENT, "DOCUMENT_INVALID", "verification.error.unreadable");
    }
  }

  /** Tag EXIF Orientation (1 à 8) lu à la main dans le segment APP1 ; 1 si absent ou illisible. */
  static int exifOrientation(byte[] b) {
    try {
      int pos = 2;
      while (pos + 4 <= b.length && (b[pos] & 0xFF) == 0xFF) {
        int marker = b[pos + 1] & 0xFF;
        if (marker == 0xDA) {
          break;
        }
        int len = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
        if (marker == 0xE1
            && b[pos + 4] == 'E'
            && b[pos + 5] == 'x'
            && b[pos + 6] == 'i'
            && b[pos + 7] == 'f'
            && b[pos + 8] == 0
            && b[pos + 9] == 0) {
          int tiff = pos + 10;
          boolean le = b[tiff] == 'I';
          int ifd = tiff + (int) u32(b, tiff + 4, le);
          int count = u16(b, ifd, le);
          for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (u16(b, entry, le) == 0x0112) {
              int o = u16(b, entry + 8, le);
              return o >= 1 && o <= 8 ? o : 1;
            }
          }
          return 1;
        }
        pos += 2 + len;
      }
    } catch (RuntimeException e) {
      // segment tronqué ou incohérent : orientation par défaut
    }
    return 1;
  }

  private static int u16(byte[] b, int at, boolean le) {
    int x = b[at] & 0xFF;
    int y = b[at + 1] & 0xFF;
    return le ? (y << 8) | x : (x << 8) | y;
  }

  private static long u32(byte[] b, int at, boolean le) {
    long hi = u16(b, le ? at + 2 : at, le);
    long lo = u16(b, le ? at : at + 2, le);
    return (hi << 16) | lo;
  }

  /** Redresse l'image selon l'orientation EXIF (le tag est perdu à la réécriture). */
  static BufferedImage applyOrientation(BufferedImage src, int orientation) {
    if (orientation == 1) {
      return src;
    }
    int w = src.getWidth();
    int h = src.getHeight();
    boolean swap = orientation >= 5;
    int nw = swap ? h : w;
    int nh = swap ? w : h;
    int[] in = src.getRGB(0, 0, w, h, null, 0, w);
    int[] out = new int[in.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int dx;
        int dy;
        switch (orientation) {
          case 2 -> {
            dx = w - 1 - x;
            dy = y;
          }
          case 3 -> {
            dx = w - 1 - x;
            dy = h - 1 - y;
          }
          case 4 -> {
            dx = x;
            dy = h - 1 - y;
          }
          case 5 -> {
            dx = y;
            dy = x;
          }
          case 6 -> {
            dx = h - 1 - y;
            dy = x;
          }
          case 7 -> {
            dx = h - 1 - y;
            dy = w - 1 - x;
          }
          default -> {
            dx = y;
            dy = w - 1 - x;
          }
        }
        out[dy * nw + dx] = in[y * w + x];
      }
    }
    BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
    result.setRGB(0, 0, nw, nh, out, 0, nw);
    return result;
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
        if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
          throw VerificationException.of(
              HttpStatus.UNPROCESSABLE_CONTENT,
              "DOCUMENT_INVALID",
              "verification.error.dimensions");
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    }
  }
}

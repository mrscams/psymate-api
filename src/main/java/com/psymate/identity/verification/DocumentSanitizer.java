package com.psymate.identity.verification;

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

package com.psymate.identity.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.identity.verification.model.VerificationException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class DocumentSanitizerTest {

  public static byte[] jpegWithExif() throws Exception {
    BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpeg", out);
    byte[] jpeg = out.toByteArray();
    byte[] exif = "Exif\0\0GPS-LATITUDE-48.85".getBytes(StandardCharsets.ISO_8859_1);
    int len = exif.length + 2;
    ByteArrayOutputStream withExif = new ByteArrayOutputStream();
    withExif.write(jpeg, 0, 2); // SOI
    withExif.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len});
    withExif.write(exif);
    withExif.write(jpeg, 2, jpeg.length - 2);
    return withExif.toByteArray();
  }

  static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), "png", out);
    return out.toByteArray();
  }

  @Test
  void detectsBySignature() throws Exception {
    assertThat(DocumentSanitizer.detect("%PDF-1.7 x".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo("application/pdf");
    assertThat(DocumentSanitizer.detect(jpegWithExif())).isEqualTo("image/jpeg");
    assertThat(DocumentSanitizer.detect(png())).isEqualTo("image/png");
    assertThat(DocumentSanitizer.detect("MZ\u0090\u0000 exe".getBytes(StandardCharsets.ISO_8859_1)))
        .isNull();
  }

  @Test
  void exifIsRemovedFromJpeg() throws Exception {
    byte[] input = jpegWithExif();
    assertThat(new String(input, StandardCharsets.ISO_8859_1)).contains("GPS-LATITUDE");
    DocumentSanitizer.Clean clean = DocumentSanitizer.clean(input);
    assertThat(clean.mime()).isEqualTo("image/jpeg");
    String text = new String(clean.bytes(), StandardCharsets.ISO_8859_1);
    assertThat(text).doesNotContain("Exif").doesNotContain("GPS-LATITUDE");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(clean.bytes()))).isNotNull();
  }

  @Test
  void pngIsReencoded() throws Exception {
    DocumentSanitizer.Clean clean = DocumentSanitizer.clean(png());
    assertThat(clean.mime()).isEqualTo("image/png");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(clean.bytes()))).isNotNull();
  }

  @Test
  void pdfIsKeptAsIs() {
    byte[] pdf = "%PDF-1.4\nbody".getBytes(StandardCharsets.US_ASCII);
    assertThat(DocumentSanitizer.clean(pdf).bytes()).isEqualTo(pdf);
  }

  @Test
  void rejectsUnknownSignatureEmptyAndOversize() {
    assertThatThrownBy(
            () -> DocumentSanitizer.clean("MZ not a pdf".getBytes(StandardCharsets.US_ASCII)))
        .isInstanceOfSatisfying(
            VerificationException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    assertThatThrownBy(() -> DocumentSanitizer.clean(new byte[0]))
        .isInstanceOfSatisfying(
            VerificationException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    byte[] big = new byte[DocumentSanitizer.MAX_BYTES + 1];
    System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
    assertThatThrownBy(() -> DocumentSanitizer.clean(big))
        .isInstanceOfSatisfying(
            VerificationException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
  }

  @Test
  void corruptImageIsRejected() {
    byte[] bad = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};
    assertThatThrownBy(() -> DocumentSanitizer.clean(bad))
        .isInstanceOfSatisfying(
            VerificationException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
  }
}

package com.psymate.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Le tag EXIF Orientation est lu à la main puis appliqué avant la réécriture sans métadonnées. */
class OrientationTest {

  static byte[] jpegWithOrientation(int width, int height, int orientation) throws Exception {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpeg", out);
    byte[] jpeg = out.toByteArray();
    byte[] tiff = {
      'E',
      'x',
      'i',
      'f',
      0,
      0,
      'M',
      'M',
      0,
      42,
      0,
      0,
      0,
      8,
      0,
      1,
      0x01,
      0x12,
      0,
      3,
      0,
      0,
      0,
      1,
      0,
      (byte) orientation,
      0,
      0,
      0,
      0,
      0,
      0
    };
    int len = tiff.length + 2;
    ByteArrayOutputStream res = new ByteArrayOutputStream();
    res.write(jpeg, 0, 2);
    res.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len});
    res.write(tiff);
    res.write(jpeg, 2, jpeg.length - 2);
    return res.toByteArray();
  }

  @Test
  void tagIsReadFromTheApp1Segment() throws Exception {
    assertThat(DocumentSanitizer.exifOrientation(jpegWithOrientation(40, 30, 6))).isEqualTo(6);
    assertThat(DocumentSanitizer.exifOrientation(jpegWithOrientation(40, 30, 1))).isEqualTo(1);
    assertThat(DocumentSanitizer.exifOrientation(DocumentSanitizerTest.png())).isEqualTo(1);
    assertThat(DocumentSanitizer.exifOrientation(DocumentSanitizerTest.jpegWithExif()))
        .isEqualTo(1);
  }

  @Test
  void cleanRotatesQuarterTurnOrientations() throws Exception {
    for (int o : new int[] {5, 6, 7, 8}) {
      BufferedImage out = read(DocumentSanitizer.clean(jpegWithOrientation(40, 30, o)).bytes());
      assertThat(out.getWidth()).as("orientation %d", o).isEqualTo(30);
      assertThat(out.getHeight()).isEqualTo(40);
    }
    for (int o : new int[] {1, 2, 3, 4}) {
      BufferedImage out = read(DocumentSanitizer.clean(jpegWithOrientation(40, 30, o)).bytes());
      assertThat(out.getWidth()).as("orientation %d", o).isEqualTo(40);
    }
  }

  @Test
  void rotationPlacesPixelsLikeAViewerWould() {
    BufferedImage src = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
    src.setRGB(0, 0, 0xFF0000);
    src.setRGB(1, 0, 0x0000FF);
    // 6 : rotation de 90 degrés horaire, le pixel de gauche passe en haut.
    BufferedImage r = DocumentSanitizer.applyOrientation(src, 6);
    assertThat(r.getWidth()).isEqualTo(1);
    assertThat(r.getHeight()).isEqualTo(2);
    assertThat(r.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
    assertThat(r.getRGB(0, 1) & 0xFFFFFF).isEqualTo(0x0000FF);
  }

  private static BufferedImage read(byte[] bytes) throws Exception {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }
}

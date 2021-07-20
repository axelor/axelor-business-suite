/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.base.service.excelreport.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A tool for listing all the fonts in a directory. */
public class FontFactory {

  private FontFactory() {}

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static List<CSSFont> findFontsInDirectory(
      Path directory, List<String> validFileExtensions, boolean recurse, boolean followLinks)
      throws IOException {
    File[] files = filterDirectory(directory, validFileExtensions);
    return getCSSFonts(files);
  }

  protected static List<CSSFont> getCSSFonts(File[] files) throws IOException {
    List<CSSFont> fontsAdded = new ArrayList<>();
    for (File fontFile : files) {
      try {
        CSSFont font = getCSSFont(fontFile);
        fontsAdded.add(font);
        logger.debug(
            String.format(
                "Adding font with name = '%s', weight = %d, style = %s",
                font.family, font.weight, font.style.name()));

      } catch (FontFormatException ffe) {
        onInvalidFont(fontFile.toPath(), ffe);
      }
    }
    return fontsAdded;
  }

  public static List<CSSFont> findFontsInDirectory(Path directory) throws IOException {
    return findFontsInDirectory(directory, Collections.singletonList("ttf"), true, true);
  }

  public static String toCSSEscapedFontFamily(List<CSSFont> fontsList) {
    return fontsList.stream()
        .map(fnt -> '\'' + fnt.familyCssEscaped() + '\'')
        .distinct()
        .collect(Collectors.joining(", "));
  }

  public static void toBuilder(PdfRendererBuilder builder, List<CSSFont> fonts) {
    for (CSSFont font : fonts) {
      builder.useFont(font.path.toFile(), font.family, font.weight, font.style, true);
    }
  }

  protected static CSSFont getCSSFont(File fontFile) throws FontFormatException, IOException {
    Path fontPath = fontFile.toPath();
    Font f = Font.createFont(Font.TRUETYPE_FONT, new File(fontPath.toUri()));
    String family = f.getFamily();
    String name = f.getFontName(Locale.US).toLowerCase(Locale.US);
    int weight = name.contains("bold") ? 700 : 400;
    FontStyle style = name.contains("italic") ? FontStyle.ITALIC : FontStyle.NORMAL;

    return new CSSFont(fontPath, family, weight, style);
  }

  protected static File[] filterDirectory(Path directory, List<String> validFileExtensions) {
    return directory
        .toFile()
        .listFiles(
            new FileFilter() {
              @Override
              public boolean accept(File pathname) {
                return pathname.isFile()
                    && validFileExtensions.stream()
                        .anyMatch(ext -> pathname.getName().endsWith(ext));
              }
            });
  }

  protected static void onInvalidFont(Path font, FontFormatException ffe) {
    logger.debug("Ignoring font file with invalid font format: " + font);
  }
}

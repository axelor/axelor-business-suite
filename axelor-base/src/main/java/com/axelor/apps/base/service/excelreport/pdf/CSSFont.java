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
import java.nio.file.Path;
import java.util.Objects;

public class CSSFont {
  public final Path path;
  public final String family;

  /** WARNING: Heuristics are used to determine if a font is bold (700) or normal (400) weight. */
  public final int weight;

  /** WARNING: Heuristics are used to determine if a font is italic or normal style. */
  public final FontStyle style;

  public CSSFont(Path path, String family, int weight, FontStyle style) {
    this.path = path;
    this.family = family;
    this.weight = weight;
    this.style = style;
  }

  /** WARNING: Basic escaping, may not be robust to attack. */
  public String familyCssEscaped() {
    return this.family.replace("'", "\\'");
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, family, weight, style);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    CSSFont b = (CSSFont) other;

    return Objects.equals(this.path, b.path)
        && Objects.equals(this.family, b.family)
        && this.weight == b.weight
        && this.style == b.style;
  }
}

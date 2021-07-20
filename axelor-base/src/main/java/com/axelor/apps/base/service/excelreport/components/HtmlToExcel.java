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
package com.axelor.apps.base.service.excelreport.components;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Source;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;

public class HtmlToExcel {

  private static final int START_TAG = 0;
  private static final int END_TAG = 1;

  public RichTextDetails createCellValue(String html, Workbook workBook) {
    html = html.replaceAll("\\(", "'").replaceAll("\\)", "'");
    Source source = new Source(html);
    Map<String, TagInfo> tagMap = new LinkedHashMap<String, HtmlToExcel.TagInfo>();
    for (Element e : source.getChildElements()) {
      getInfo(e, tagMap);
    }

    String patternString = "(" + StringUtils.join(tagMap.keySet(), "|") + ")";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(html);

    StringBuffer textBuffer = new StringBuffer();
    List<RichTextInfo> textInfos = new ArrayList<HtmlToExcel.RichTextInfo>();
    Stack<RichTextInfo> richTextBuffer = new Stack<HtmlToExcel.RichTextInfo>();

    if (tagMap.isEmpty()) {
      return null;
    }

    while (matcher.find()) {
      matcher.appendReplacement(textBuffer, "");
      TagInfo currentTag = tagMap.get(matcher.group(1));

      if (START_TAG == currentTag.getTagType()) {
        List<RichTextInfo> infoList =
            getRichTextInfoList(currentTag, textBuffer.length(), workBook);
        if (!infoList.isEmpty()) {
          richTextBuffer.addAll(infoList);
        }
      } else {
        if (!richTextBuffer.isEmpty()) {
          for (int i = 0; i <= richTextBuffer.size(); i++) {
            RichTextInfo info = richTextBuffer.pop();
            if (info != null) {
              info.setEndIndex(textBuffer.length());
              textInfos.add(info);
            }
          }
        }
      }
    }
    matcher.appendTail(textBuffer);
    Map<Integer, Font> fontMap = buildFontMap(textInfos, workBook);

    return new RichTextDetails(textBuffer.toString(), fontMap);
  }

  public RichTextString mergeTextDetails(List<RichTextDetails> cellValues) {
    StringBuilder textBuffer = new StringBuilder();
    Map<Integer, Font> mergedMap = new LinkedHashMap<>();
    int currentIndex = 0;
    for (RichTextDetails richTextDetail : cellValues) {
      //  textBuffer.append(BULLET_CHARACTER + " ");
      currentIndex = textBuffer.length();
      for (Entry<Integer, Font> entry : richTextDetail.getFontMap().entrySet()) {
        mergedMap.put(entry.getKey() + currentIndex, entry.getValue());
      }
      textBuffer.append(richTextDetail.getRichText());
      // textBuffer.append(richTextDetail.getRichText()).append(NEW_LINE);
    }

    RichTextString richText = new XSSFRichTextString(textBuffer.toString());
    for (int i = 0; i < textBuffer.length(); i++) {
      Font currentFont = mergedMap.get(i);
      if (currentFont != null) {
        richText.applyFont(i, i + 1, currentFont);
      }
    }
    return richText;
  }

  private static Map<Integer, Font> buildFontMap(List<RichTextInfo> textInfos, Workbook workBook) {
    Map<Integer, Font> fontMap = new LinkedHashMap<Integer, Font>();
    for (RichTextInfo richTextInfo : textInfos) {
      if (richTextInfo.isValid()) {
        for (int i = richTextInfo.getStartIndex(); i < richTextInfo.getEndIndex(); i++) {
          fontMap.put(
              i,
              mergeFont(
                  fontMap.get(i),
                  richTextInfo.getFontStyle(),
                  richTextInfo.getFontValue(),
                  workBook));
        }
      }
    }

    return fontMap;
  }

  private static Font mergeFont(Font font, STYLES fontStyle, String fontValue, Workbook workBook) {
    if (font == null) {
      font = workBook.createFont();
    }

    switch (fontStyle) {
      case FONT:
        if (!isEmpty(fontValue)) {
          String[] fontValues = fontValue.split(",");

          font.setFontName(fontValues[0].trim());
        }
        break;
      case SIZE:
        font.setFontHeightInPoints(Short.valueOf(fontValue.trim().concat("0")));
        break;
      case BOLD:
        if (!isEmpty(fontValue) && fontValue.equalsIgnoreCase("bold")) {
          font.setBold(true);
        }
        break;
      case EM:
      case STRONG:
        font.setBold(true);
        break;
      case LINE:
        if (!isEmpty(fontValue)) {
          for (String value : fontValue.trim().split(" ")) {
            if (value.trim().equalsIgnoreCase("underline")) {
              font.setUnderline(XSSFFont.U_SINGLE);
            } else if (value.trim().equalsIgnoreCase("line-through")) {
              font.setStrikeout(true);
            }
          }
        }

        break;
      case ITALLICS:
        if (!isEmpty(fontValue) && fontValue.equalsIgnoreCase("italic")) {
          font.setItalic(true);
        }
        break;
      case COLOR:
        if (!isEmpty(fontValue)) {
          String[] rgb = fontValue.substring(4, fontValue.lastIndexOf("'")).split(",");
          XSSFColor myColor =
              new XSSFColor(
                  new Color(
                      Integer.valueOf(rgb[0].trim()),
                      Integer.valueOf(rgb[1].trim()),
                      Integer.valueOf(rgb[2].trim())),
                  null);
          font.setColor(myColor.getIndex());
        }
        break;
      case PARAGRAPH:
        break;
      default:
        break;
    }

    return font;
  }

  private static List<RichTextInfo> getRichTextInfoList(
      TagInfo currentTag, int startIndex, Workbook workBook) {
    List<RichTextInfo> infoList = new ArrayList<>();
    switch (STYLES.fromValue(currentTag.getTagName())) {
      case SPAN:
        if (!isEmpty(currentTag.getStyle())) {
          for (String style : currentTag.getStyle().split(";")) {

            String[] styleDetails = style.split(":");
            if (styleDetails != null && styleDetails.length > 1) {
              if ("COLOR".equalsIgnoreCase(styleDetails[0].trim())) {
                infoList.add(
                    new RichTextInfo(startIndex, -1, STYLES.COLOR, styleDetails[1].trim()));
              } else if ("FONT-WEIGHT".equalsIgnoreCase(styleDetails[0].trim())) {
                infoList.add(new RichTextInfo(startIndex, -1, STYLES.BOLD, styleDetails[1].trim()));
              } else if ("FONT-STYLE".equalsIgnoreCase(styleDetails[0].trim())) {
                infoList.add(
                    new RichTextInfo(startIndex, -1, STYLES.ITALLICS, styleDetails[1].trim()));
              } else if ("TEXT-DECORATION-LINE".equalsIgnoreCase(styleDetails[0].trim())) {
                infoList.add(new RichTextInfo(startIndex, -1, STYLES.LINE, styleDetails[1].trim()));
              }
            }
          }
        }
        break;
      case FONT:
        if (!isEmpty(currentTag.getStyle())) {

          String[] style = currentTag.getStyle().split(";");
          infoList.add(new RichTextInfo(startIndex, -1, STYLES.FONT, style[0].trim()));
          infoList.add(new RichTextInfo(startIndex, -1, STYLES.SIZE, style[1].trim()));
        }
        break;
      case PARAGRAPH:
        if (!isEmpty(currentTag.getStyle())) {
          infoList.add(new RichTextInfo(startIndex, -1, STYLES.PARAGRAPH, ""));
        }
        break;
      default:
        infoList.add(new RichTextInfo(startIndex, -1, STYLES.fromValue(currentTag.getTagName())));
        break;
    }
    return infoList;
  }

  private static boolean isEmpty(String str) {
    return (str == null || str.trim().length() == 0);
  }

  private static void getInfo(Element e, Map<String, HtmlToExcel.TagInfo> tagMap) {
    if (e.getStartTag().getName().equalsIgnoreCase("p")) {
      tagMap.put(e.getStartTag().toString(), new TagInfo(e.getStartTag().getName(), "", START_TAG));
    }
    if (e.getStartTag().getName().equalsIgnoreCase("font")) {
      String fontFamily =
          StringUtils.isEmpty(e.getAttributeValue("face"))
              ? "Times New Roman"
              : e.getAttributeValue("face");
      String fontSize =
          StringUtils.isEmpty(e.getAttributeValue("size")) ? "10" : e.getAttributeValue("size");
      String tagStyle = String.format("%s ; %s", fontFamily, fontSize);
      tagMap.put(
          e.getStartTag().toString(), new TagInfo(e.getStartTag().getName(), tagStyle, START_TAG));
    }
    if (e.getStartTag().getName().equalsIgnoreCase("span")) {
      tagMap.put(
          e.getStartTag().toString(),
          new TagInfo(e.getStartTag().getName(), e.getAttributeValue("style"), START_TAG));
    }

    if (e.getChildElements().size() > 0) {
      List<Element> children = e.getChildElements();
      for (Element child : children) {
        getInfo(child, tagMap);
      }
    }
    if (e.getEndTag() != null) {
      tagMap.put(e.getEndTag().toString(), new TagInfo(e.getEndTag().getName(), END_TAG));
    } else {
      // Handling self closing tags
      tagMap.put(e.getStartTag().toString(), new TagInfo(e.getStartTag().getName(), END_TAG));
    }
  }

  static class RichTextInfo {
    private int startIndex;
    private int endIndex;
    private STYLES fontStyle;
    private String fontValue;

    public RichTextInfo(int startIndex, int endIndex, STYLES fontStyle) {
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.fontStyle = fontStyle;
    }

    public RichTextInfo(int startIndex, int endIndex, STYLES fontStyle, String fontValue) {
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.fontStyle = fontStyle;
      this.fontValue = fontValue;
    }

    public int getStartIndex() {
      return startIndex;
    }

    public void setStartIndex(int startIndex) {
      this.startIndex = startIndex;
    }

    public int getEndIndex() {
      return endIndex;
    }

    public void setEndIndex(int endIndex) {
      this.endIndex = endIndex;
    }

    public STYLES getFontStyle() {
      return fontStyle;
    }

    public void setFontStyle(STYLES fontStyle) {
      this.fontStyle = fontStyle;
    }

    public String getFontValue() {
      return fontValue;
    }

    public void setFontValue(String fontValue) {
      this.fontValue = fontValue;
    }

    public boolean isValid() {
      return (startIndex != -1 && endIndex != -1 && endIndex >= startIndex);
    }

    @Override
    public String toString() {
      return "RichTextInfo [startIndex="
          + startIndex
          + ", endIndex="
          + endIndex
          + ", fontStyle="
          + fontStyle
          + ", fontValue="
          + fontValue
          + "]";
    }
  }

  static class RichTextDetails {
    private String richText;
    private Map<Integer, Font> fontMap;

    public RichTextDetails(String richText, Map<Integer, Font> fontMap) {
      this.richText = richText;
      this.fontMap = fontMap;
    }

    public String getRichText() {
      return richText;
    }

    public void setRichText(String richText) {
      this.richText = richText;
    }

    public Map<Integer, Font> getFontMap() {
      return fontMap;
    }

    public void setFontMap(Map<Integer, Font> fontMap) {
      this.fontMap = fontMap;
    }
  }

  static class TagInfo {
    private String tagName;
    private String style;
    private int tagType;

    public TagInfo(String tagName, String style, int tagType) {
      this.tagName = tagName;
      this.style = style;
      this.tagType = tagType;
    }

    public TagInfo(String tagName, int tagType) {
      this.tagName = tagName;
      this.tagType = tagType;
    }

    public String getTagName() {
      return tagName;
    }

    public void setTagName(String tagName) {
      this.tagName = tagName;
    }

    public int getTagType() {
      return tagType;
    }

    public void setTagType(int tagType) {
      this.tagType = tagType;
    }

    public String getStyle() {
      return style;
    }

    public void setStyle(String style) {
      this.style = style;
    }

    @Override
    public String toString() {
      return "TagInfo [tagName=" + tagName + ", style=" + style + ", tagType=" + tagType + "]";
    }
  }

  enum STYLES {
    BOLD("b"),
    EM("em"),
    STRONG("strong"),
    COLOR("color"),
    LINE("line"),
    SPAN("span"),
    PARAGRAPH("p"),
    FONT("font"),
    SIZE("size"),
    ITALLICS("i"),
    UNKNOWN("unknown");

    private String type;

    private STYLES(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }

    public static STYLES fromValue(String type) {
      for (STYLES style : values()) {
        if (style.type.equalsIgnoreCase(type)) {
          return style;
        }
      }
      return UNKNOWN;
    }
  }
}

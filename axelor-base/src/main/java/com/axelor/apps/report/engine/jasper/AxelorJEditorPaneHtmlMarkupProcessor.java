package com.axelor.apps.report.engine.jasper;

import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import net.sf.jasperreports.engine.JRPrintHyperlink;
import net.sf.jasperreports.engine.base.JRBasePrintHyperlink;
import net.sf.jasperreports.engine.type.HyperlinkTypeEnum;
import net.sf.jasperreports.engine.util.JEditorPaneHtmlMarkupProcessor;
import net.sf.jasperreports.engine.util.JRStringUtil;
import net.sf.jasperreports.engine.util.JRStyledText;
import net.sf.jasperreports.engine.util.JRStyledTextParser;
import net.sf.jasperreports.engine.util.JRTextAttribute;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Specific implementation of HTML formatting to avoid inconsistencies with lists wrt BIRT. */
public class AxelorJEditorPaneHtmlMarkupProcessor extends JEditorPaneHtmlMarkupProcessor {
  private static final Log log = LogFactory.getLog(JEditorPaneHtmlMarkupProcessor.class);

  private static AxelorJEditorPaneHtmlMarkupProcessor instance;

  public static AxelorJEditorPaneHtmlMarkupProcessor getInstance() {
    if (instance == null) {
      instance = new AxelorJEditorPaneHtmlMarkupProcessor();
    }
    return instance;
  }

  // Sadly, we've to copy/paste huge amount of code since
  @Override
  public String convert(String srcText) {
    JEditorPane editorPane = new JEditorPane("text/html", srcText);
    editorPane.setEditable(false);
    List<Element> elements = new ArrayList<>();
    Document document = editorPane.getDocument();
    Element root = document.getDefaultRootElement();
    if (root != null) {
      this.addElements(elements, root);
    }

    int startOffset = 0;
    int endOffset = 0;
    int crtOffset = 0;
    String chunk = null;
    JRPrintHyperlink hyperlink = null;
    Element element = null;
    Element previous;
    Element parent;
    boolean bodyOccurred = false;
    int[] orderedListIndex = new int[elements.size()];
    String whitespace = "    ";
    String[] whitespaces = new String[elements.size()];

    for (int i = 0; i < elements.size(); ++i) {
      whitespaces[i] = "";
    }

    StringBuilder text = new StringBuilder();
    List<JRStyledText.Run> styleRuns = new ArrayList<>();

    int i;
    for (i = 0; i < elements.size(); ++i) {
      if (bodyOccurred
          && chunk != null
          && (text.length() > 0 || StringUtils.containsOnly(chunk, "\n") == false)) {
        text.append(chunk);
        Map<AttributedCharacterIterator.Attribute, Object> styleAttributes =
            this.getAttributes(element.getAttributes());
        if (hyperlink != null) {
          styleAttributes.put(JRTextAttribute.HYPERLINK, hyperlink);
          hyperlink = null;
        }

        if (!styleAttributes.isEmpty()) {
          styleRuns.add(
              new JRStyledText.Run(
                  styleAttributes, startOffset + crtOffset, endOffset + crtOffset));
        }
      }

      chunk = null;
      previous = element;
      element = (Element) elements.get(i);
      parent = element.getParentElement();
      startOffset = element.getStartOffset();
      endOffset = element.getEndOffset();
      AttributeSet attrs = element.getAttributes();
      Object elementName = attrs.getAttribute("$ename");
      Object object = elementName != null ? null : attrs.getAttribute(StyleConstants.NameAttribute);
      if (object instanceof HTML.Tag) {
        HTML.Tag htmlTag = (HTML.Tag) object;
        if (htmlTag == HTML.Tag.BODY) {
          bodyOccurred = true;
          crtOffset = -startOffset;
        } else if (htmlTag == HTML.Tag.BR) {
          if (text.length() > 0) {
            chunk = "\n";
          } else {
            chunk = "";
          }
        } else {
          String parentName;
          if (htmlTag == HTML.Tag.OL) {
            orderedListIndex[i] = 0;
            parentName = parent.getName().toLowerCase();
            whitespaces[i] = whitespaces[elements.indexOf(parent)] + whitespace;
            if (parentName.equals("li")
                || text.length() == 0
                || text.charAt(text.length() - 1) == '\n') {
              chunk = "";
            } else {
              chunk = "\n";
              ++crtOffset;
            }
          } else if (htmlTag == HTML.Tag.UL) {
            whitespaces[i] = whitespaces[elements.indexOf(parent)] + whitespace;
            parentName = parent.getName().toLowerCase();
            if (parentName.equals("li")
                || text.length() == 0
                || text.charAt(text.length() - 1) == '\n') {
              chunk = "";
            } else {
              chunk = "\n";
              ++crtOffset;
            }
          } else if (htmlTag != HTML.Tag.LI) {
            if (element instanceof AbstractDocument.LeafElement) {
              if (element instanceof HTMLDocument.RunElement) {
                HTMLDocument.RunElement runElement = (HTMLDocument.RunElement) element;
                AttributeSet attrSet = (AttributeSet) runElement.getAttribute(HTML.Tag.A);
                if (attrSet != null) {
                  hyperlink = new JRBasePrintHyperlink();
                  hyperlink.setHyperlinkType(HyperlinkTypeEnum.REFERENCE);
                  hyperlink.setHyperlinkReference(
                      (String) attrSet.getAttribute(javax.swing.text.html.HTML.Attribute.HREF));
                  hyperlink.setLinkTarget(
                      (String) attrSet.getAttribute(javax.swing.text.html.HTML.Attribute.TARGET));
                }
              }

              try {
                chunk = document.getText(startOffset, endOffset - startOffset);
              } catch (BadLocationException var29) {
                if (log.isDebugEnabled()) {
                  log.debug("Error converting markup.", var29);
                }
              }
            }
          } else {
            whitespaces[i] = whitespaces[elements.indexOf(parent)];
            if (element.getElement(0) == null
                || !element.getElement(0).getName().toLowerCase().equals("ol")
                    && !element.getElement(0).getName().toLowerCase().equals("ul")) {
              if (parent.getName().equals("ol")) {
                int index = elements.indexOf(parent);
                Object type =
                    parent.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.TYPE);
                Object startObject =
                    parent.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.START);
                int start =
                    startObject == null
                        ? 0
                        : Math.max(0, Integer.valueOf(startObject.toString()) - 1);
                String suffix = "";
                ++orderedListIndex[index];
                if (type != null) {
                  switch (((String) type).charAt(0)) {
                    case '1':
                    default:
                      suffix = String.valueOf(orderedListIndex[index] + start);
                      break;
                    case 'A':
                      suffix = getOLBulletChars(orderedListIndex[index] + start, true);
                      break;
                    case 'I':
                      suffix = JRStringUtil.getRomanNumeral(orderedListIndex[index] + start, true);
                      break;
                    case 'a':
                      suffix = getOLBulletChars(orderedListIndex[index] + start, false);
                      break;
                    case 'i':
                      suffix = JRStringUtil.getRomanNumeral(orderedListIndex[index] + start, false);
                  }
                } else {
                  suffix = suffix + (orderedListIndex[index] + start);
                }

                chunk = whitespaces[index] + suffix + "." + "  ";
              } else {
                chunk = whitespaces[elements.indexOf(parent)] + "•" + "  ";
              }
            } else {
              chunk = "";
            }

            crtOffset += chunk.length();
          }
        }
      }
    }

    JRStyledText.Run run;
    if (chunk != null) {
      if (StringUtils.containsOnly(chunk, "\n") == false) {
        text.append(chunk);
        Map<AttributedCharacterIterator.Attribute, Object> styleAttributes =
            this.getAttributes(element.getAttributes());
        if (hyperlink != null) {
          styleAttributes.put(JRTextAttribute.HYPERLINK, hyperlink);
          hyperlink = null;
        }

        if (!styleAttributes.isEmpty()) {
          styleRuns.add(
              new JRStyledText.Run(
                  styleAttributes, startOffset + crtOffset, endOffset + crtOffset));
        }
      } else {
        i = text.length();
        ListIterator<JRStyledText.Run> it = styleRuns.listIterator();

        while (it.hasNext()) {
          run = (JRStyledText.Run) it.next();
          if (run.endIndex == i + 1) {
            if (run.startIndex < run.endIndex - 1) {
              it.set(new JRStyledText.Run(run.attributes, run.startIndex, run.endIndex - 1));
            } else {
              it.remove();
            }
          }
        }
      }
    }

    JRStyledText styledText = new JRStyledText((Locale) null, text.toString());

    for (JRStyledText.Run styleRun : styleRuns) {
      styledText.addRun(styleRun);
    }

    styledText.setGlobalAttributes(new HashMap<>());
    return JRStyledTextParser.getInstance().write(styledText);
  }
}

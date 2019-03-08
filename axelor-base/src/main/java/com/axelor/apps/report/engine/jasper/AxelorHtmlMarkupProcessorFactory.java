package com.axelor.apps.report.engine.jasper;

import net.sf.jasperreports.engine.util.MarkupProcessor;
import net.sf.jasperreports.engine.util.MarkupProcessorFactory;

public class AxelorHtmlMarkupProcessorFactory implements MarkupProcessorFactory {
  @Override
  public MarkupProcessor createMarkupProcessor() {
    return AxelorJEditorPaneHtmlMarkupProcessor.getInstance();
  }
}

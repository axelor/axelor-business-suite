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
package com.axelor.apps.base.service;

import com.axelor.app.internal.AppFilter;
import com.axelor.apps.base.db.BirtTemplate;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Language;
import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintLine;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.apps.base.db.PrintTemplateLine;
import com.axelor.apps.base.db.repo.PrintRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.message.TemplateMessageServiceBaseImpl;
import com.axelor.apps.message.db.TemplateContext;
import com.axelor.apps.message.service.TemplateContextService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.axelor.meta.db.repo.MetaSelectRepository;
import com.axelor.rpc.Context;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintTemplateServiceImpl implements PrintTemplateService {

  private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected static final char TEMPLATE_DELIMITER = '$';
  protected static final String TEMPLATE_CONTEXT_SEQUENCE_KEY_PREFIX = "Seq_";

  protected PrintRepository printRepo;
  protected PrintService printService;
  protected TemplateMessageServiceBaseImpl templateMessageService;
  protected TemplateContextService templateContextService;
  protected int rank;

  @Inject
  public PrintTemplateServiceImpl(
      PrintRepository printRepo,
      PrintService printService,
      TemplateMessageServiceBaseImpl templateMessageService,
      TemplateContextService templateContextService) {
    this.printRepo = printRepo;
    this.printService = printService;
    this.templateMessageService = templateMessageService;
    this.templateContextService = templateContextService;
  }

  @SuppressWarnings("unchecked")
  @Override
  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public Print generatePrint(Long objectId, PrintTemplate printTemplate)
      throws AxelorException, IOException, ClassNotFoundException {
    MetaModel metaModel = printTemplate.getMetaModel();
    if (metaModel == null) {
      return null;
    }
    String model = metaModel.getFullName();
    String simpleModel = metaModel.getName();
    // debug
    LOG.debug("");
    LOG.debug("model : {}", model);
    LOG.debug("simpleModel : {}", simpleModel);
    LOG.debug("object id : {}", objectId);
    LOG.debug("printTemplate : {}", printTemplate);
    LOG.debug("");

    Locale locale =
        Optional.ofNullable(printTemplate.getLanguage())
            .map(Language::getCode)
            .map(Locale::new)
            .orElseGet(AppFilter::getLocale);
    TemplateMaker maker = initMaker(objectId, model, simpleModel, locale);

    Print print = new Print();
    if (StringUtils.notEmpty(printTemplate.getDocumentName())) {
      maker.setTemplate(printTemplate.getDocumentName());
      print.setDocumentName(maker.make());
    }
    print.setMetaModel(metaModel);
    print.setObjectId(objectId);
    print.setCompany(printTemplate.getCompany());
    print.setLanguage(printTemplate.getLanguage());
    print.setHidePrintSettings(printTemplate.getHidePrintSettings());
    print.setFormatSelect(printTemplate.getFormatSelect());
    print.setDisplayTypeSelect(printTemplate.getDisplayTypeSelect());
    print.setIsEditable(printTemplate.getIsEditable());
    print.setAttach(printTemplate.getAttach());
    print.setMetaFileField(printTemplate.getMetaFileField());

    if (!printTemplate.getHidePrintSettings()) {
      print.setMarginTop(printTemplate.getMarginTop());
      print.setMarginRight(printTemplate.getMarginRight());
      print.setMarginBottom(printTemplate.getMarginBottom());
      print.setMarginLeft(printTemplate.getMarginLeft());

      if (StringUtils.notEmpty(printTemplate.getPrintTemplatePdfHeader())) {
        maker.setTemplate(printTemplate.getPrintTemplatePdfHeader());
        print.setPrintPdfHeader(maker.make());
      }

      if (StringUtils.notEmpty(printTemplate.getPrintTemplatePdfFooter())) {
        maker.setTemplate(printTemplate.getPrintTemplatePdfFooter());
        print.setPrintPdfFooter(maker.make());
        print.setFooterWidth(printTemplate.getFooterWidth());
        print.setFooterFontSize(printTemplate.getFooterFontSize());
        print.setFooterFontType(printTemplate.getFooterFontType());
        print.setFooterTextAlignment(printTemplate.getFooterTextAlignment());
        print.setIsFooterUnderLine(printTemplate.getIsFooterUnderLine());
        print.setFooterFontColor(printTemplate.getFooterFontColor());
      }

      print.setWithPagination(printTemplate.getWithPagination());
      if (printTemplate.getWithPagination()) {
        print.setPaginationWidth(printTemplate.getPaginationWidth());
        print.setPaginationFontSize(printTemplate.getPaginationFontSize());
        print.setPaginationFontType(printTemplate.getPaginationFontType());
        print.setPaginationFontColor(printTemplate.getPaginationFontColor());
        print.setPaginationTextAlignment(printTemplate.getPaginationTextAlignment());
        print.setIsPaginationUnderLine(printTemplate.getIsPaginationUnderLine());
        print.setPaginationTemplate(printTemplate.getPaginationTemplate());
      }

      print.setLogoPositionSelect(printTemplate.getLogoPositionSelect());
      print.setLogoWidth(printTemplate.getLogoWidth());
      print.setHeaderContentWidth(printTemplate.getHeaderContentWidth());
    }

    Context scriptContext = null;
    if (StringUtils.notEmpty(model)) {
      Class<? extends Model> modelClass = (Class<? extends Model>) Class.forName(model);
      Model modelObject = JPA.find(modelClass, objectId);
      if (ObjectUtils.notEmpty(modelObject)) {
        scriptContext = new Context(Mapper.toMap(modelObject), modelClass);
      }
    }

    rank = 0;
    int level = 1;

    if (CollectionUtils.isNotEmpty(printTemplate.getPrintTemplateLineList())) {
      processPrintTemplateLineList(
          printTemplate.getPrintTemplateLineList(), print, null, scriptContext, maker, level);
    }

    if (CollectionUtils.isNotEmpty(printTemplate.getPrintTemplateSet())) {
      for (PrintTemplate subPrintTemplate : printTemplate.getPrintTemplateSet()) {
        Long subObjectId = objectId;
        if (StringUtils.notEmpty(subPrintTemplate.getMetaModelDefaultPath())) {
          Object subMetaModelObject =
              templateContextService.computeTemplateContext(
                  subPrintTemplate.getMetaModelDefaultPath(), scriptContext);
          Class<? extends Model> subModelClass =
              (Class<? extends Model>) Class.forName(subPrintTemplate.getMetaModel().getFullName());
          subObjectId = (Long) Mapper.of(subModelClass).get(subMetaModelObject, "id");
        }

        Print subPrint = generatePrint(subObjectId, subPrintTemplate);
        print.addPrintSetItem(subPrint);
      }
    }
    print = printRepo.save(print);

    printService.attachMetaFiles(print, getMetaFiles(maker, printTemplate));

    return print;
  }

  private void processPrintTemplateLineList(
      List<PrintTemplateLine> templateLineList,
      Print print,
      PrintLine parent,
      Context scriptContext,
      TemplateMaker maker,
      int level)
      throws AxelorException {

    int seq = 1;
    if (ObjectUtils.notEmpty(templateLineList.get(0))
        && ObjectUtils.notEmpty(templateLineList.get(0).getSequence())) {
      seq = templateLineList.get(0).getSequence().intValue();
    }
    for (PrintTemplateLine printTemplateLine : templateLineList) {
      if (printTemplateLine.getIgnoreTheLine()) {
        continue;
      }
      try {
        boolean present = true;
        if (StringUtils.notEmpty(printTemplateLine.getConditions())) {
          Object evaluation =
              templateContextService.computeTemplateContext(
                  printTemplateLine.getConditions(), scriptContext);
          if (evaluation instanceof Boolean) {
            present = (Boolean) evaluation;
          } else {
            throw new AxelorException(
                TraceBackRepository.CATEGORY_INCONSISTENCY,
                I18n.get(IExceptionMessage.PRINT_TEMPLATE_CONDITION_MUST_BE_BOOLEAN));
          }
        }

        if (present) {
          ++rank;
          String title = null;
          String content = null;

          addSequencesInContext(maker, level, seq, parent);
          if (CollectionUtils.isNotEmpty(printTemplateLine.getTemplateContextList())) {
            for (TemplateContext templateContext : printTemplateLine.getTemplateContextList()) {
              Object result =
                  templateContextService.computeTemplateContext(templateContext, scriptContext);
              maker.addContext(templateContext.getName(), result);
            }
          }

          if (StringUtils.notEmpty(printTemplateLine.getTitle())) {
            maker.setTemplate(printTemplateLine.getTitle());
            title = maker.make();
          }

          if (StringUtils.notEmpty(printTemplateLine.getContent())) {
            maker.setTemplate(printTemplateLine.getContent());
            content = maker.make();
          }

          PrintLine printLine = new PrintLine();
          printLine.setRank(rank);
          printLine.setSequence(seq);
          printLine.setTitle(title);
          printLine.setContent(content);
          printLine.setIsEditable(printTemplateLine.getIsEditable());
          printLine.setParent(parent);
          printLine.setIsWithPageBreakAfter(printTemplateLine.getIsWithPageBreakAfter());

          if (CollectionUtils.isNotEmpty(printTemplateLine.getPrintTemplateLineList())) {
            processPrintTemplateLineList(
                printTemplateLine.getPrintTemplateLineList(),
                print,
                printLine,
                scriptContext,
                maker,
                level + 1);
          }

          print.addPrintLineListItem(printLine);
          seq++;
        }
      } catch (Exception e) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.PRINT_TEMPLATE_ERROR_ON_LINE_WITH_SEQUENCE_AND_TITLE),
            printTemplateLine.getSequence(),
            printTemplateLine.getTitle());
      }
    }
  }

  private void addSequencesInContext(TemplateMaker maker, int level, int seq, PrintLine parent) {
    maker.addInContext(TEMPLATE_CONTEXT_SEQUENCE_KEY_PREFIX + level, seq);
    if (ObjectUtils.notEmpty(parent)) {
      addSequencesInContext(maker, level - 1, parent.getSequence(), parent.getParent());
    }
  }

  @SuppressWarnings("unchecked")
  protected TemplateMaker initMaker(Long objectId, String model, String simpleModel, Locale locale)
      throws ClassNotFoundException {
    String timezone = null;
    Company activeCompany =
        Optional.ofNullable(AuthUtils.getUser()).map(User::getActiveCompany).orElse(null);
    if (activeCompany != null) {
      timezone = activeCompany.getTimezone();
    }
    TemplateMaker maker =
        new TemplateMaker(timezone, locale, TEMPLATE_DELIMITER, TEMPLATE_DELIMITER);

    Class<? extends Model> myClass = (Class<? extends Model>) Class.forName(model);
    Model modelObject = JPA.find(myClass, objectId);
    maker.setContext(modelObject, simpleModel);

    return maker;
  }

  public Set<MetaFile> getMetaFiles(TemplateMaker maker, PrintTemplate printTemplate)
      throws AxelorException, IOException {
    Set<MetaFile> metaFiles = new HashSet<>();
    if (printTemplate.getBirtTemplateSet() == null) {
      return metaFiles;
    }

    for (BirtTemplate birtTemplate : printTemplate.getBirtTemplateSet()) {
      metaFiles.add(
          templateMessageService.createMetaFileUsingBirtTemplate(maker, birtTemplate, null, null));
    }

    LOG.debug("MetaFile to attach: {}", metaFiles);

    return metaFiles;
  }

  @Override
  @Transactional
  public void addItemToReferenceSelection(MetaModel model) {
    MetaSelect metaSelect =
        Beans.get(MetaSelectRepository.class)
            .findByName("print.template.line.test.reference.select");
    List<MetaSelectItem> items = metaSelect.getItems();
    if (items != null && !items.stream().anyMatch(x -> x.getValue().equals(model.getFullName()))) {
      MetaSelectItem metaSelectItem = new MetaSelectItem();
      metaSelectItem.setTitle(model.getName());
      metaSelectItem.setValue(model.getFullName());
      metaSelectItem.setSelect(metaSelect);
      Beans.get(MetaSelectItemRepository.class).save(metaSelectItem);
    }
  }
}

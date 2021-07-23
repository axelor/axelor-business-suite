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
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Language;
import com.axelor.apps.base.db.PrintTemplateLine;
import com.axelor.apps.base.db.repo.PrintTemplateLineRepository;
import com.axelor.apps.message.service.TemplateContextService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaModel;
import com.axelor.tool.template.TemplateMaker;
import com.google.inject.Inject;
import java.util.Locale;
import java.util.Optional;

public class PrintTemplateLineServiceImpl implements PrintTemplateLineService {

  protected static final char TEMPLATE_DELIMITER = '$';

  protected TemplateContextService templateContextService;
  protected PrintTemplateLineRepository printTemplateLineRepo;

  @Inject
  public PrintTemplateLineServiceImpl(
      TemplateContextService templateContextService,
      PrintTemplateLineRepository printTemplateLineRepo) {
    this.templateContextService = templateContextService;
    this.printTemplateLineRepo = printTemplateLineRepo;
  }

  @Override
  public String checkExpression(
      Long objectId, MetaModel metaModel, PrintTemplateLine printTemplateLine)
      throws ClassNotFoundException, AxelorException {
    if (metaModel == null) {
      return null;
    }
    String model = metaModel.getFullName();
    String simpleModel = metaModel.getName();

    String resultOfTitle = null;
    String resultOfContent = null;

    Locale locale =
        Optional.ofNullable(printTemplateLine.getPrintTemplate().getLanguage())
            .map(Language::getCode)
            .map(Locale::new)
            .orElseGet(AppFilter::getLocale);
    TemplateMaker maker = initMaker(objectId, model, simpleModel, locale);

    try {
      if (StringUtils.notEmpty(printTemplateLine.getTitle())) {
        maker.setTemplate(printTemplateLine.getTitle());
        resultOfTitle = maker.make();
      }
    } catch (Exception e) {
      resultOfTitle = "Error in title";
    }

    try {
      if (StringUtils.notEmpty(printTemplateLine.getContent())) {
        maker.setTemplate(printTemplateLine.getContent());
        resultOfContent = maker.make();
      }
    } catch (Exception e) {
      resultOfContent = "Error in content";
    }

    return resultOfTitle + "<br>" + resultOfContent;
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

    Class<? extends Model> modelClass = (Class<? extends Model>) Class.forName(model);
    maker.setContext(JPA.find(modelClass, objectId), simpleModel);

    return maker;
  }
}

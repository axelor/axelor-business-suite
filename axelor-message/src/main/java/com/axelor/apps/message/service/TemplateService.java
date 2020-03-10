/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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
package com.axelor.apps.message.service;

import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.exception.IExceptionMessage;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaModel;
import com.axelor.tool.template.TemplateMaker;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.inject.Singleton;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Singleton
public class TemplateService {

  public void checkTargetReceptor(Template template) throws AxelorException {
    String target = template.getTarget();
    MetaModel metaModel = template.getMetaModel();

    if (Strings.isNullOrEmpty(target)) {
      return;
    }
    if (metaModel == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TEMPLATE_SERVICE_1));
    }

    try {
      this.validTarget(target, metaModel);
    } catch (Exception e) {
      throw new AxelorException(
          e.getCause(),
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.TEMPLATE_SERVICE_2));
    }
  }

  protected void validTarget(String target, MetaModel metaModel) throws ClassNotFoundException {
    Iterator<String> iter = Splitter.on(".").split(target).iterator();
    Property p = Mapper.of(Class.forName(metaModel.getFullName())).getProperty(iter.next());
    while (iter.hasNext() && p != null) {
      p = Mapper.of(p.getTarget()).getProperty(iter.next());
    }

    if (p == null) {
      throw new IllegalArgumentException();
    }
  }

  public String processSubject(
      Template template, Model bean, String beanName, Map<String, Object> context) {
    TemplateMaker maker = new TemplateMaker(new Locale("fr"), '$', '$');

    maker.setTemplate(template.getSubject());
    maker.setContext(bean, context, beanName);
    return maker.make();
  }

  public String processContent(
      Template template, Model bean, String beanName, Map<String, Object> context) {
    TemplateMaker maker = new TemplateMaker(new Locale("fr"), '$', '$');

    maker.setTemplate(template.getContent());
    maker.setContext(bean, context, beanName);
    return maker.make();
  }
}

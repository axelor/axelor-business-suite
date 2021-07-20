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

import com.axelor.apps.base.db.Print;
import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.exception.AxelorException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public interface PrintTemplateService {

  public Map<String, Object> generatePrintTemplate(Long objectId, PrintTemplate printTemplate)
      throws AxelorException, IOException, ClassNotFoundException, ScriptException,
          ParserConfigurationException, SAXException;

  public File generatePrintTemplateFile(Long objectId, PrintTemplate printTemplate)
      throws AxelorException, IOException, ClassNotFoundException, ScriptException,
          ParserConfigurationException, SAXException;

  public Print getTemplatePrint(Long objectId, PrintTemplate printTemplate)
      throws AxelorException, IOException, ClassNotFoundException;
}

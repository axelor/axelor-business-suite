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
package com.axelor.apps.base.service.excelreport;

import com.axelor.apps.base.db.PrintTemplate;
import com.axelor.exception.AxelorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.script.ScriptException;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public interface ExcelReportService {

  public File createReport(List<Long> objectIds, PrintTemplate printTemplate)
      throws IOException, ClassNotFoundException, AxelorException, ScriptException,
          ParserConfigurationException, SAXException;
}

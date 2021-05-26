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
package com.axelor.csv.script;

import com.axelor.apps.base.db.AdvancedImport;
import com.axelor.meta.MetaFiles;
import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportAdvancedImportConfiguration {

  protected static final String FILES_DIR = "files";

  private final Logger log = LoggerFactory.getLogger(ImportAdvancedImportConfiguration.class);
  @Inject private MetaFiles metaFiles;

  public Object importAdvancedImportFile(Object bean, Map<String, Object> values) {

    assert bean instanceof AdvancedImport;
    final Path path = (Path) values.get("__path__");
    AdvancedImport advancedImport = (AdvancedImport) bean;

    try {
      if (values.containsKey("importFile") && values.get("importFile") != null) {
        String fileName = values.get("importFile").toString();
        File file = path.resolve(FILES_DIR + File.separator + fileName).toFile();
        advancedImport.setImportFile(metaFiles.upload(file));
      }
    } catch (Exception e) {
      log.debug("Error importing advanced import config", e);
    }

    return advancedImport;
  }
}

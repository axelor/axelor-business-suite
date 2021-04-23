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
package com.axelor.apps.sale.service.configurator;

import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.db.ConfiguratorFormula;
import com.axelor.data.Listener;
import com.axelor.data.xml.XMLImporter;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaJsonField;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.impl.common.IOUtil;

public class ConfiguratorCreatorImportServiceImpl implements ConfiguratorCreatorImportService {

  protected ConfiguratorCreatorService configuratorCreatorService;

  @Inject
  public ConfiguratorCreatorImportServiceImpl(
      ConfiguratorCreatorService configuratorCreatorService) {
    this.configuratorCreatorService = configuratorCreatorService;
  }

  private static final String CONFIG_FILE_PATH =
      "/data-import/import-configurator-creator-config.xml";

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public String importConfiguratorCreators(String filePath) throws IOException {
    return importConfiguratorCreators(filePath, CONFIG_FILE_PATH);
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public String importConfiguratorCreators(String filePath, String configFilePath)
      throws IOException {
    Path path = MetaFiles.getPath(filePath);
    try (InputStream fileInPutStream = new FileInputStream(path.toFile())) {
      return importConfiguratorCreators(fileInPutStream, configFilePath);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public String importConfiguratorCreators(InputStream xmlInputStream) throws IOException {
    return importConfiguratorCreators(xmlInputStream, CONFIG_FILE_PATH);
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public String importConfiguratorCreators(InputStream xmlInputStream, String configFilePath)
      throws IOException {
    InputStream inputStream = this.getClass().getResourceAsStream(configFilePath);
    File configFile = File.createTempFile("config", ".xml");
    FileOutputStream fout = new FileOutputStream(configFile);
    IOUtil.copyCompletely(inputStream, fout);

    File tempDir = Files.createTempDir();
    File importFile = new File(tempDir, "configurator-creator.xml");
    FileUtils.copyInputStreamToFile(xmlInputStream, importFile);

    XMLImporter importer = new XMLImporter(configFile.getAbsolutePath(), tempDir.getAbsolutePath());
    final StringBuilder importLog = new StringBuilder();
    Listener listener =
        new Listener() {

          @Override
          public void imported(Integer imported, Integer total) {
            importLog.append("Total records: " + total + ", Total imported: " + total);
          }

          @Override
          public void imported(Model arg0) {
            try {
              completeAfterImport(arg0);
            } catch (AxelorException e) {
              importLog.append("Error in import: " + Arrays.toString(e.getStackTrace()));
            }
          }

          @Override
          public void handle(Model arg0, Exception err) {
            importLog.append("Error in import: " + Arrays.toString(err.getStackTrace()));
          }
        };

    importer.addListener(listener);

    importer.run();

    FileUtils.forceDelete(configFile);

    FileUtils.forceDelete(tempDir);

    return importLog.toString();
  }

  protected void completeAfterImport(Object arg0) throws AxelorException {
    if (arg0.getClass().equals(ConfiguratorCreator.class)) {
      completeAfterImport((ConfiguratorCreator) arg0);
    }
  }

  protected void completeAfterImport(ConfiguratorCreator creator) throws AxelorException {
    fixAttributesName(creator);
    configuratorCreatorService.updateAttributes(creator);
    configuratorCreatorService.updateIndicators(creator);
  }

  @Override
  public void fixAttributesName(ConfiguratorCreator creator) throws AxelorException {
    List<MetaJsonField> attributes = creator.getAttributes();
    if (attributes == null) {
      return;
    }
    for (MetaJsonField attribute : attributes) {
      String name = attribute.getName();
      if (name != null && name.contains("_")) {
        attribute.setName(name.substring(0, name.lastIndexOf('_')) + '_' + creator.getId());
      }
      updateOtherFieldsInAttribute(creator, attribute);
      updateAttributeNameInFormulas(creator, name, attribute.getName());
    }
  }

  /**
   * Update the configurator id in other fields of the attribute.
   *
   * @param creator
   * @param attribute attribute to update
   */
  protected void updateOtherFieldsInAttribute(
      ConfiguratorCreator creator, MetaJsonField attribute) {
    try {
      List<Field> fieldsToUpdate =
          Arrays.stream(attribute.getClass().getDeclaredFields())
              .filter(field -> field.getType().equals(String.class))
              .collect(Collectors.toList());
      for (Field field : fieldsToUpdate) {
        Mapper mapper = Mapper.of(attribute.getClass());
        Method getter = mapper.getGetter(field.getName());
        String fieldString = (String) getter.invoke(attribute);
        if (fieldString != null && fieldString.contains("_")) {
          Method setter = mapper.getSetter(field.getName());
          String updatedFieldString =
              fieldString.substring(0, fieldString.lastIndexOf('_')) + '_' + creator.getId();
          setter.invoke(attribute, updatedFieldString);
        }
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  /**
   * Update the changed attribute in all formula O2M.
   *
   * @param creator
   * @param oldName
   * @param newName
   */
  protected void updateAttributeNameInFormulas(
      ConfiguratorCreator creator, String oldName, String newName) throws AxelorException {
    if (creator.getConfiguratorFormulaList() != null) {
      updateAttributeNameInFormulas(creator.getConfiguratorFormulaList(), oldName, newName);
    }
  }

  /**
   * Update the changed attribute in formulas.
   *
   * @param formulas
   * @param oldAttributeName
   * @param newAttributeName
   */
  protected void updateAttributeNameInFormulas(
      List<ConfiguratorFormula> formulas, String oldAttributeName, String newAttributeName) {

    formulas.forEach(
        configuratorFormula ->
            configuratorFormula.setFormula(
                configuratorFormula.getFormula().replace(oldAttributeName, newAttributeName)));
  }
}

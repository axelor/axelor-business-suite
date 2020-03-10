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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.DataConfigLine;
import com.axelor.apps.base.db.ObjectDataConfig;
import com.axelor.apps.base.db.ObjectDataConfigExport;
import com.axelor.common.Inflector;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectDataExportServiceImpl implements ObjectDataExportService {

  private final Logger logger = LoggerFactory.getLogger(ObjectDataExportServiceImpl.class);

  private Inflector inflector = Inflector.getInstance();

  @Inject private MetaFiles metaFiles;

  @Override
  public MetaFile export(
      ObjectDataConfig objectDataConfig, ObjectDataConfigExport objDataConfigExport)
      throws AxelorException {

    Long recordId = objDataConfigExport.getModelSelectId();
    String language = objDataConfigExport.getLangSelect();
    String format = objDataConfigExport.getExportFormatSelect();

    try {
      logger.debug(
          "Exporting data for model: {}, id: {}, language: {}, format: {}",
          objectDataConfig.getModelSelect(),
          recordId,
          language,
          format);

      Map<String, List<String[]>> data = createData(objectDataConfig, recordId, language);

      if (format != null && format.equals("csv")) {
        return writeCSV(data);
      } else {
        return writeExcel(data);
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
      throw new AxelorException(TraceBackRepository.TYPE_TECHNICAL, e.getMessage());
    }
  }

  protected Map<String, List<String[]>> createData(
      ObjectDataConfig objectDataConfig, Long recordId, String language)
      throws ClassNotFoundException {

    Map<String, List<String[]>> data = new HashMap<>();

    for (DataConfigLine line : objectDataConfig.getDataConfigLineList()) {

      MetaModel metaModel = line.getMetaModel();

      logger.debug("Create data for: {}", metaModel.getName());

      Class<? extends Model> modelClass = ObjectDataCommonService.findModelClass(metaModel);

      Query<? extends Model> query =
          ObjectDataCommonService.createQuery(recordId, line, modelClass);

      ResourceBundle bundle = ObjectDataCommonService.getResourceBundle(language);

      String[][] fieldsData = createFieldsData(line.getToExportMetaFieldSet(), bundle);

      Map<String, String> selectMap = getSelectMap(Mapper.of(modelClass));

      List<String[]> dataList = fetchData(fieldsData, query, selectMap, bundle);

      data.put(line.getTabName(), dataList);
    }

    return data;
  }

  protected Map<String, String> getSelectMap(Mapper mapper) {

    Map<String, String> selectionMap = new HashMap<>();

    for (Property property : mapper.getProperties()) {
      String selection = property.getSelection();
      if (!Strings.isNullOrEmpty(selection)) {
        selectionMap.put(property.getName(), selection);
      }
    }

    return selectionMap;
  }

  protected String[][] createFieldsData(Set<MetaField> metaFields, ResourceBundle bundle)
      throws ClassNotFoundException {

    List<String> names = new ArrayList<>();
    List<String> labels = new ArrayList<>();

    for (MetaField field : metaFields) {
      String label = field.getLabel();
      String name = field.getName();

      if (Strings.isNullOrEmpty(label)) {
        label = inflector.humanize(name);
      }

      if (field.getRelationship() != null) {
        name = name + "." + ObjectDataCommonService.getNameColumn(field);
      }

      names.add(name);

      String translated = bundle.getString(label);

      if (!Strings.isNullOrEmpty(translated)) {
        labels.add(translated);
      } else {
        labels.add(label);
      }
    }

    logger.debug("Fields: {}", names);
    logger.debug("Labels: {}", labels);

    return new String[][] {
      names.toArray(new String[names.size()]), labels.toArray(new String[labels.size()])
    };
  }

  protected List<String[]> fetchData(
      String[][] fieldsData,
      Query<? extends Model> query,
      Map<String, String> selectMap,
      ResourceBundle bundle) {

    List<String[]> dataList = new ArrayList<>();
    dataList.add(fieldsData[1]);

    List<Map> records = query.select(fieldsData[0]).fetch(0, 0);

    for (Map<String, Object> record : records) {

      List<String> datas = new ArrayList<>();

      for (String field : fieldsData[0]) {
        Object object = record.get(field);
        if (object == null) {
          datas.add("");
          continue;
        }
        if (selectMap.containsKey(field)) {
          String selection = selectMap.get(field);
          Option option = MetaStore.getSelectionItem(selection, object.toString());
          if (option == null) {
            datas.add("");
            continue;
          }
          String optionTitle = option.getTitle();
          optionTitle = bundle.getString(optionTitle);
          if (optionTitle != null) {
            datas.add(optionTitle);
          } else {
            datas.add(option.getTitle());
          }
        } else {
          datas.add(object.toString());
        }
      }

      dataList.add(datas.toArray(new String[fieldsData[0].length]));
    }

    return dataList;
  }

  protected MetaFile writeCSV(Map<String, List<String[]>> data) throws IOException {

    File zipFile = MetaFiles.createTempFile("Data", ".zip").toFile();
    try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipFile))) {

      for (String model : data.keySet()) {
        File modelFile = MetaFiles.createTempFile(model, ".csv").toFile();
        CSVWriter writer = new CSVWriter(new FileWriter(modelFile), ';');
        writer.writeAll(data.get(model));
        writer.close();
        zout.putNextEntry(new ZipEntry(model + ".csv"));
        zout.write(IOUtils.toByteArray(new FileInputStream(modelFile)));
        zout.closeEntry();
      }
      zout.close();
    }
    return metaFiles.upload(zipFile);
  }

  protected MetaFile writeExcel(Map<String, List<String[]>> data) throws IOException {

    XSSFWorkbook workBook = new XSSFWorkbook();

    for (String model : data.keySet()) {
      XSSFSheet sheet = workBook.createSheet(model);
      int count = 0;
      for (String[] record : data.get(model)) {
        XSSFRow row = sheet.createRow(count);
        int cellCount = 0;
        for (String val : record) {
          XSSFCell cell = row.createCell(cellCount);
          cell.setCellValue(val);
          cellCount++;
        }
        count++;
      }
    }

    File excelFile = MetaFiles.createTempFile("Data", ".xls").toFile();
    FileOutputStream out = new FileOutputStream(excelFile);
    workBook.write(out);
    out.close();

    return metaFiles.upload(excelFile);
  }
}

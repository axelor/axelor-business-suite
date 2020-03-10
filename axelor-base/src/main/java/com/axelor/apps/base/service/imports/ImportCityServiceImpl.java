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
package com.axelor.apps.base.service.imports;

import com.axelor.apps.base.db.ImportConfiguration;
import com.axelor.apps.base.db.ImportHistory;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.imports.importer.FactoryImporter;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.io.Files;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class ImportCityServiceImpl implements ImportCityService {

  @Inject private FactoryImporter factoryImporter;

  @Inject private MetaFiles metaFiles;

  /** {@inheritDoc}} */
  @Override
  public ImportHistory importCity(String typeSelect, MetaFile dataFile) {

    ImportHistory importHistory = null;
    try {
      if (dataFile.getFileType().equals("application/zip")) {
        dataFile = this.extractCityZip(dataFile);
      }
      File configXmlFile = this.getConfigXmlFile(typeSelect);
      File dataCsvFile = this.getDataCsvFile(dataFile);

      importHistory = importCityData(configXmlFile, dataCsvFile);
      this.deleteTempFiles(configXmlFile, dataCsvFile);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return importHistory;
  }

  /**
   * create bind or config file according to typeSelect
   *
   * @param typeSelect
   * @return
   */
  protected File getConfigXmlFile(String typeSelect) {

    File configFile = null;
    try {
      configFile = File.createTempFile("input-config", ".xml");

      InputStream bindFileInputStream =
          this.getClass().getResourceAsStream("/import-configs/" + typeSelect + "-config.xml");

      if (bindFileInputStream == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.IMPORTER_3));
      }

      FileOutputStream outputStream = new FileOutputStream(configFile);

      IOUtils.copy(bindFileInputStream, outputStream);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return configFile;
  }

  /**
   * create geonames-city.csv data file from geonames .txt data file
   *
   * @param dataFile
   * @return
   */
  protected File getDataCsvFile(MetaFile dataFile) {

    File csvFile = null;
    try {
      File tempDir = Files.createTempDir();
      csvFile = new File(tempDir, "geonames_city.csv");

      Files.copy(MetaFiles.getPath(dataFile).toFile(), csvFile);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return csvFile;
  }

  /**
   * Import city data
   *
   * @param configXmlFile
   * @param dataCsvFile
   * @return
   */
  protected ImportHistory importCityData(File configXmlFile, File dataCsvFile) {

    ImportHistory importHistory = null;
    try {
      ImportConfiguration importConfiguration = new ImportConfiguration();
      importConfiguration.setBindMetaFile(metaFiles.upload(configXmlFile));
      importConfiguration.setDataMetaFile(metaFiles.upload(dataCsvFile));

      importHistory = factoryImporter.createImporter(importConfiguration).run();

    } catch (Exception e) {
      e.printStackTrace();
    }
    return importHistory;
  }

  /**
   * Delete temporary config & data file
   *
   * @param configXmlFile
   * @param dataCsvFile
   */
  protected void deleteTempFiles(File configXmlFile, File dataCsvFile) {

    try {
      if (configXmlFile.isDirectory() && dataCsvFile.isDirectory()) {
        FileUtils.deleteDirectory(configXmlFile);
        FileUtils.deleteDirectory(dataCsvFile);
      } else {
        configXmlFile.delete();
        dataCsvFile.delete();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected MetaFile extractCityZip(MetaFile dataFile) throws Exception {

    ZipEntry entry = null;
    MetaFile metaFile = null;
    File txtFile = File.createTempFile("city", ".txt");
    String requiredFileName = dataFile.getFileName().replace(".zip", ".txt");

    byte[] buffer = new byte[1024];
    int len;

    try (ZipInputStream zipFileInStream =
        new ZipInputStream(new FileInputStream(MetaFiles.getPath(dataFile).toFile())); ) {

      while ((entry = zipFileInStream.getNextEntry()) != null) {

        if (entry.getName().equals(requiredFileName)) {

          try (FileOutputStream fos = new FileOutputStream(txtFile); ) {

            while ((len = zipFileInStream.read(buffer)) > 0) {
              fos.write(buffer, 0, len);
            }
          }
          break;
        }
      }
    }

    metaFile = metaFiles.upload(txtFile);

    FileUtils.forceDelete(txtFile);

    return metaFile;
  }
}

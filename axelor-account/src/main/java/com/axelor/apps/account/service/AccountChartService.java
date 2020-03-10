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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.AccountChart;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.repo.AccountChartRepository;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.ImportConfiguration;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.service.imports.importer.FactoryImporter;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.meta.MetaFiles;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;

public class AccountChartService {

  @Inject private FactoryImporter factoryImporter;

  @Inject private MetaFiles metaFiles;

  protected AccountConfigRepository accountConfigRepo;
  protected CompanyRepository companyRepo;
  protected AccountChartRepository accountChartRepository;

  @Inject
  public AccountChartService(
      AccountConfigRepository accountConfigRepo,
      CompanyRepository companyRepo,
      AccountChartRepository accountChartRepository) {

    this.accountConfigRepo = accountConfigRepo;
    this.companyRepo = companyRepo;
    this.accountChartRepository = accountChartRepository;
  }

  public Boolean installAccountChart(AccountChart act, Company company, AccountConfig accountConfig)
      throws AxelorException {
    try {

      if (act.getMetaFile() == null) {
        return false;
      }

      InputStream inputStream = this.getClass().getResourceAsStream("/l10n/chart-config.xml");
      if (inputStream == null) {
        return false;
      }

      File configFile = createConfigFile(inputStream);

      Map<String, Object> importContext = new HashMap<String, Object>();
      importContext.put("_companyId", company.getId());

      importAccountChartData(act, configFile, importContext);

      updateChartCompany(act, company, accountConfig);

      FileUtils.forceDelete(configFile);

      return true;
    } catch (IOException e) {
      TraceBackService.trace(e);
      return false;
    }
  }

  protected File createConfigFile(InputStream inputStream)
      throws IOException, FileNotFoundException {

    File configFile = File.createTempFile("input-config", ".xml");

    try (FileOutputStream outputStream = new FileOutputStream(configFile)) {
      int read = 0;
      byte[] bytes = new byte[1024];
      while ((read = inputStream.read(bytes)) != -1) {
        outputStream.write(bytes, 0, read);
      }
      outputStream.close();
    }

    return configFile;
  }

  @Transactional
  public void updateChartCompany(AccountChart act, Company company, AccountConfig accountConfig) {

    accountConfig = accountConfigRepo.find(accountConfig.getId());
    accountConfig.setHasChartImported(true);
    accountConfigRepo.save(accountConfig);
    act = accountChartRepository.find(act.getId());
    company = companyRepo.find(company.getId());
    Set<Company> companySet = act.getCompanySet();
    companySet.add(company);
    act.setCompanySet(companySet);
    accountChartRepository.save(act);
  }

  public void importAccountChartData(
      AccountChart act, File configFile, Map<String, Object> importContext)
      throws IOException, AxelorException {

    ImportConfiguration importConfiguration = new ImportConfiguration();
    importConfiguration.setDataMetaFile(act.getMetaFile());
    importConfiguration.setBindMetaFile(metaFiles.upload(configFile));

    factoryImporter.createImporter(importConfiguration).run(importContext);
  }
}

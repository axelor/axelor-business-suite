/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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

import com.axelor.apps.account.db.AnalyticAxis;
import com.axelor.apps.account.db.AnalyticDistributionLine;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticJournal;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.AppAccount;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppAccountRepository;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticMoveLineServiceImpl implements AnalyticMoveLineService {

  protected AppAccountService appAccountService;
  protected AccountManagementServiceAccountImpl accountManagementServiceAccountImpl;

  @Inject
  public AnalyticMoveLineServiceImpl(
      AppAccountService appAccountService,
      AccountManagementServiceAccountImpl accountManagementServiceAccountImpl) {

    this.appAccountService = appAccountService;
    this.accountManagementServiceAccountImpl = accountManagementServiceAccountImpl;
  }

  @Override
  public BigDecimal computeAmount(AnalyticMoveLine analyticMoveLine) {

    return analyticMoveLine
        .getPercentage()
        .multiply(analyticMoveLine.getOriginalPieceAmount())
        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
  }

  @Override
  public List<AnalyticMoveLine> generateLines(
      AnalyticDistributionTemplate analyticDistributionTemplate,
      BigDecimal total,
      int typeSelect,
      LocalDate date) {

    List<AnalyticMoveLine> analyticMoveLineList = new ArrayList<AnalyticMoveLine>();
    if (analyticDistributionTemplate != null) {
      for (AnalyticDistributionLine analyticDistributionLine :
          analyticDistributionTemplate.getAnalyticDistributionLineList()) {
        analyticMoveLineList.add(
            this.createAnalyticMoveLine(analyticDistributionLine, total, typeSelect, date));
      }
    }

    return analyticMoveLineList;
  }

  @Override
  public AnalyticDistributionTemplate getAnalyticDistributionTemplate(
      Partner partner, Product product, Company company) {

    AppAccount appAccount = appAccountService.getAppAccount();

    if (appAccount.getAnalyticDistributionTypeSelect()
            == AppAccountRepository.DISTRIBUTION_TYPE_PARTNER
        && partner != null) {
      return partner.getAnalyticDistributionTemplate();
    } else if (appAccount.getAnalyticDistributionTypeSelect()
        == AppAccountRepository.DISTRIBUTION_TYPE_PRODUCT) {
      return accountManagementServiceAccountImpl.getAnalyticDistributionTemplate(product, company);
    }
    return null;
  }

  public AnalyticMoveLine createAnalyticMoveLine(
      AnalyticDistributionLine analyticDistributionLine,
      BigDecimal total,
      int typeSelect,
      LocalDate date) {

    AnalyticMoveLine analyticMoveLine = new AnalyticMoveLine();
    analyticMoveLine.setOriginalPieceAmount(total);
    analyticMoveLine.setAnalyticAccount(analyticDistributionLine.getAnalyticAccount());
    analyticMoveLine.setAnalyticAxis(analyticDistributionLine.getAnalyticAxis());
    analyticMoveLine.setAnalyticJournal(analyticDistributionLine.getAnalyticJournal());

    AnalyticJournal analyticJournal = analyticDistributionLine.getAnalyticJournal();
    Company company = analyticJournal == null ? null : analyticJournal.getCompany();
    if (company != null) {
      analyticMoveLine.setCurrency(company.getCurrency());
    }
    analyticMoveLine.setDate(date);
    analyticMoveLine.setPercentage(analyticDistributionLine.getPercentage());
    analyticMoveLine.setAmount(computeAmount(analyticMoveLine));
    analyticMoveLine.setTypeSelect(typeSelect);

    return analyticMoveLine;
  }

  @Override
  public void updateAnalyticMoveLine(
      AnalyticMoveLine analyticMoveLine, BigDecimal total, LocalDate date) {

    analyticMoveLine.setOriginalPieceAmount(total);
    analyticMoveLine.setAmount(computeAmount(analyticMoveLine));
    analyticMoveLine.setDate(date);
  }

  @Override
  public boolean validateLines(List<AnalyticDistributionLine> analyticDistributionLineList) {
    if (analyticDistributionLineList != null) {
      Map<AnalyticAxis, BigDecimal> map = new HashMap<AnalyticAxis, BigDecimal>();
      for (AnalyticDistributionLine analyticDistributionLine : analyticDistributionLineList) {
        if (map.containsKey(analyticDistributionLine.getAnalyticAxis())) {
          map.put(
              analyticDistributionLine.getAnalyticAxis(),
              map.get(analyticDistributionLine.getAnalyticAxis())
                  .add(analyticDistributionLine.getPercentage()));
        } else {
          map.put(
              analyticDistributionLine.getAnalyticAxis(), analyticDistributionLine.getPercentage());
        }
      }
      for (AnalyticAxis analyticAxis : map.keySet()) {
        if (map.get(analyticAxis).compareTo(new BigDecimal(100)) > 0) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public BigDecimal getOriginalPieceAmount(Context parentContext) {
    if (parentContext.getContextClass().equals(InvoiceLine.class)) {
      InvoiceLine invoiceLine = parentContext.asType(InvoiceLine.class);
      return invoiceLine.getCompanyExTaxTotal();
    } else if (parentContext.getContextClass().equals(MoveLine.class)) {
      MoveLine moveLine = parentContext.asType(MoveLine.class);
      return moveLine.getDebit();
    }
    return BigDecimal.ZERO;
  }
}

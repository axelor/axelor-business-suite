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
package com.axelor.apps.hr.service.lunch.voucher;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.hr.db.*;
import com.axelor.apps.hr.db.repo.*;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.auth.AuthUtils;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LunchVoucherMgtServiceImpl implements LunchVoucherMgtService {

  protected LunchVoucherMgtRepository lunchVoucherMgtRepository;

  protected LunchVoucherMgtLineService lunchVoucherMgtLineService;

  protected LunchVoucherAdvanceService lunchVoucherAdvanceService;

  protected HRConfigService hrConfigService;

  protected AppBaseService appBaseService;

  @Inject
  public LunchVoucherMgtServiceImpl(
      LunchVoucherMgtLineService lunchVoucherMgtLineService,
      LunchVoucherAdvanceService lunchVoucherAdvanceService,
      LunchVoucherMgtRepository lunchVoucherMgtRepository,
      HRConfigService hrConfigService,
      AppBaseService appBaseService) {

    this.lunchVoucherMgtLineService = lunchVoucherMgtLineService;
    this.lunchVoucherMgtRepository = lunchVoucherMgtRepository;
    this.lunchVoucherAdvanceService = lunchVoucherAdvanceService;
    this.hrConfigService = hrConfigService;
    this.appBaseService = appBaseService;
  }

  protected boolean isEmployeeFormerNewOrArchived(
      Employee employee, LunchVoucherMgt lunchVoucherMgt) {
    Objects.requireNonNull(employee);
    LocalDate today =
        appBaseService.getTodayDate(
            employee.getUser() != null
                ? employee.getUser().getActiveCompany()
                : AuthUtils.getUser().getActiveCompany());
    return (employee.getLeavingDate() != null
            && employee.getLeavingDate().compareTo(lunchVoucherMgt.getLeavePeriod().getFromDate())
                < 0)
        || (employee.getHireDate() != null && employee.getHireDate().compareTo(today) > 0)
        || (employee.getArchived() != null && employee.getArchived());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void calculate(LunchVoucherMgt lunchVoucherMgt) throws AxelorException {
    Company company = lunchVoucherMgt.getCompany();

    if (company == null) {
      throw new AxelorException(
          lunchVoucherMgt,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get("Please fill a company."));
    }
    if (lunchVoucherMgt.getLeavePeriod() == null) {
      throw new AxelorException(
          lunchVoucherMgt,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get("Please fill a leave period."));
    }

    HRConfig hrConfig = hrConfigService.getHRConfig(company);

    List<Employee> employeeList =
        Beans.get(EmployeeRepository.class)
            .all()
            .filter("self.mainEmploymentContract.payCompany = ?1", company)
            .fetch();
    for (Employee employee : employeeList) {
      if (employee != null) {
        if (isEmployeeFormerNewOrArchived(employee, lunchVoucherMgt)) {
          continue;
        }
        LunchVoucherMgtLine lunchVoucherMgtLine = obtainLineFromEmployee(employee, lunchVoucherMgt);
        // the employee doesn't have a line, create it
        if (lunchVoucherMgtLine == null) {
          lunchVoucherMgtLine = lunchVoucherMgtLineService.create(employee, lunchVoucherMgt);
          lunchVoucherMgt.addLunchVoucherMgtLineListItem(lunchVoucherMgtLine);
        }
        // the line exist and is not already calculated, update it
        else {
          if (lunchVoucherMgtLine.getStatusSelect()
              != LunchVoucherMgtLineRepository.STATUS_CALCULATED) {
            lunchVoucherMgtLineService.computeAllAttrs(
                employee, lunchVoucherMgt, lunchVoucherMgtLine);
          }
        }
      }
    }

    lunchVoucherMgt.setStatusSelect(LunchVoucherMgtRepository.STATUS_CALCULATED);

    lunchVoucherMgt.setStockQuantityStatus(hrConfig.getAvailableStockLunchVoucher());

    calculateTotal(lunchVoucherMgt);

    lunchVoucherMgtRepository.save(lunchVoucherMgt);
  }

  protected LunchVoucherMgtLine obtainLineFromEmployee(
      Employee employee, LunchVoucherMgt lunchVoucherMgt) {
    for (LunchVoucherMgtLine line : lunchVoucherMgt.getLunchVoucherMgtLineList()) {
      if (line.getEmployee() == employee) {
        return line;
      }
    }
    return null;
  }

  @Override
  public void calculateTotal(LunchVoucherMgt lunchVoucherMgt) {
    List<LunchVoucherMgtLine> lunchVoucherMgtLineList =
        lunchVoucherMgt.getLunchVoucherMgtLineList();
    int total = 0;
    int totalInAdvance = 0;

    int totalGiven = 0;

    if (!ObjectUtils.isEmpty(lunchVoucherMgtLineList)) {
      for (LunchVoucherMgtLine lunchVoucherMgtLine : lunchVoucherMgtLineList) {
        total += lunchVoucherMgtLine.getLunchVoucherNumber();
        totalInAdvance += lunchVoucherMgtLine.getInAdvanceNbr();
        totalGiven += lunchVoucherMgtLine.getGivenToEmployee();
      }
    }

    lunchVoucherMgt.setTotalLunchVouchers(
        total + totalInAdvance + lunchVoucherMgt.getStockLineQuantity());
    lunchVoucherMgt.setRequestedLunchVouchers(total + lunchVoucherMgt.getStockLineQuantity());
    lunchVoucherMgt.setGivenLunchVouchers(totalGiven);
  }

  @Override
  public int checkStock(Company company, int numberToUse) throws AxelorException {

    HRConfig hrConfig = hrConfigService.getHRConfig(company);
    int minStoclLV = hrConfig.getMinStockLunchVoucher();
    int availableStoclLV = hrConfig.getAvailableStockLunchVoucher();

    return availableStoclLV - numberToUse - minStoclLV;
  }

  /**
   * Update the stock in the config from lunch voucher management
   *
   * @param newLunchVoucherMgtLines the new mgt lines
   * @param oldLunchVoucherMgtLines the previous mgt lines
   * @param company the company of the HR config
   * @return the stock quantity status of the lunch voucher mgt
   * @throws AxelorException
   */
  @Transactional(rollbackOn = {Exception.class})
  @Override
  public int updateStock(
      List<LunchVoucherMgtLine> newLunchVoucherMgtLines,
      List<LunchVoucherMgtLine> oldLunchVoucherMgtLines,
      Company company)
      throws AxelorException {
    HRConfig hrConfig = hrConfigService.getHRConfig(company);

    int newLunchVoucherQty = hrConfig.getAvailableStockLunchVoucher();
    int i = 0;
    for (LunchVoucherMgtLine line : newLunchVoucherMgtLines) {
      int oldQty = oldLunchVoucherMgtLines.get(i).getGivenToEmployee();
      int newQty = line.getGivenToEmployee();
      newLunchVoucherQty = newLunchVoucherQty - newQty + oldQty;
      i++;
    }
    hrConfig.setAvailableStockLunchVoucher(newLunchVoucherQty);
    Beans.get(HRConfigRepository.class).save(hrConfig);
    return hrConfig.getAvailableStockLunchVoucher();
  }

  @Transactional(rollbackOn = {Exception.class})
  public void export(LunchVoucherMgt lunchVoucherMgt) throws IOException {
    MetaFile metaFile = new MetaFile();
    metaFile.setFileName(
        I18n.get("LunchVoucherCommand")
            + " - "
            + appBaseService
                .getTodayDate(lunchVoucherMgt.getCompany())
                .format(DateTimeFormatter.ISO_DATE)
            + ".csv");

    Path tempFile = MetaFiles.createTempFile(null, ".csv");
    final OutputStream os = new FileOutputStream(tempFile.toFile());

    try (final Writer writer = new OutputStreamWriter(os)) {

      List<String> header = new ArrayList<>();
      header.add(escapeCsv(I18n.get("Company code")));
      header.add(escapeCsv(I18n.get("Lunch Voucher's number")));
      header.add(escapeCsv(I18n.get("Employee")));
      header.add(escapeCsv(I18n.get("Lunch Voucher format")));

      writer.write(Joiner.on(";").join(header));

      for (LunchVoucherMgtLine lunchVoucherMgtLine : lunchVoucherMgt.getLunchVoucherMgtLineList()) {

        List<String> line = new ArrayList<>();
        line.add(escapeCsv(lunchVoucherMgt.getCompany().getCode()));
        line.add(escapeCsv(lunchVoucherMgtLine.getLunchVoucherNumber().toString()));
        line.add(escapeCsv(lunchVoucherMgtLine.getEmployee().getName()));
        line.add(
            escapeCsv(lunchVoucherMgtLine.getEmployee().getLunchVoucherFormatSelect().toString()));

        writer.write("\n");
        writer.write(Joiner.on(";").join(line));
      }

      Beans.get(MetaFiles.class).upload(tempFile.toFile(), metaFile);

    } catch (Exception e) {
      Throwables.propagate(e);
    } finally {
      Files.deleteIfExists(tempFile);
    }
    /*
     */
    // lunchVoucherMgt.setExported(true);
    lunchVoucherMgt.setCsvFile(metaFile);
    lunchVoucherMgt.setExportDate(appBaseService.getTodayDate(lunchVoucherMgt.getCompany()));

    lunchVoucherMgtRepository.save(lunchVoucherMgt);
  }

  protected String escapeCsv(String value) {
    if (value == null) return "";
    if (value.indexOf('"') > -1) value = value.replaceAll("\"", "\"\"");
    return '"' + value + '"';
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void validate(LunchVoucherMgt lunchVoucherMgt) throws AxelorException {
    Company company = lunchVoucherMgt.getCompany();
    HRConfig hrConfig = hrConfigService.getHRConfig(company);

    LunchVoucherAdvanceRepository advanceRepo = Beans.get(LunchVoucherAdvanceRepository.class);

    for (LunchVoucherMgtLine item : lunchVoucherMgt.getLunchVoucherMgtLineList()) {
      if (item.getInAdvanceNbr() > 0) {

        int qtyToUse = item.getInAdvanceNbr();
        List<LunchVoucherAdvance> list =
            advanceRepo
                .all()
                .filter(
                    "self.employee.id = ?1 AND self.nbrLunchVouchersUsed < self.nbrLunchVouchers",
                    item.getEmployee().getId())
                .order("distributionDate")
                .fetch();

        for (LunchVoucherAdvance subItem : list) {
          qtyToUse = lunchVoucherAdvanceService.useAdvance(subItem, qtyToUse);
          advanceRepo.save(subItem);

          if (qtyToUse <= 0) {
            break;
          }
        }
      }
    }

    hrConfig.setAvailableStockLunchVoucher(
        hrConfig.getAvailableStockLunchVoucher() + lunchVoucherMgt.getStockLineQuantity());
    lunchVoucherMgt.setStatusSelect(LunchVoucherMgtRepository.STATUS_VALIDATED);

    Beans.get(HRConfigRepository.class).save(hrConfig);
    lunchVoucherMgtRepository.save(lunchVoucherMgt);
  }
}

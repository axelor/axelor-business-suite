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
package com.axelor.apps.base.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.account.db.repo.TaxRepository;
import com.axelor.apps.base.db.PartnerAddress;
import com.axelor.apps.base.db.repo.ABCAnalysisBaseRepository;
import com.axelor.apps.base.db.repo.ABCAnalysisRepository;
import com.axelor.apps.base.db.repo.AddressBaseRepository;
import com.axelor.apps.base.db.repo.AddressRepository;
import com.axelor.apps.base.db.repo.AlarmEngineBatchBaseRepository;
import com.axelor.apps.base.db.repo.AlarmEngineBatchRepository;
import com.axelor.apps.base.db.repo.BankAddressBaseRepository;
import com.axelor.apps.base.db.repo.BankAddressRepository;
import com.axelor.apps.base.db.repo.BankBaseRepository;
import com.axelor.apps.base.db.repo.BankRepository;
import com.axelor.apps.base.db.repo.BaseBatchBaseRepository;
import com.axelor.apps.base.db.repo.BaseBatchRepository;
import com.axelor.apps.base.db.repo.BirtTemplateBaseRepository;
import com.axelor.apps.base.db.repo.BirtTemplateRepository;
import com.axelor.apps.base.db.repo.DurationBaseRepository;
import com.axelor.apps.base.db.repo.DurationRepository;
import com.axelor.apps.base.db.repo.ICalendarEventManagementRepository;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.MailBatchBaseRepository;
import com.axelor.apps.base.db.repo.MailBatchRepository;
import com.axelor.apps.base.db.repo.MailingListMessageBaseRepository;
import com.axelor.apps.base.db.repo.MailingListMessageRepository;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.PartnerBaseRepository;
import com.axelor.apps.base.db.repo.PartnerRepository;
import com.axelor.apps.base.db.repo.ProductBaseRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.SequenceBaseRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.db.repo.TaxBaseRepository;
import com.axelor.apps.base.db.repo.TeamTaskBaseRepository;
import com.axelor.apps.base.db.repo.UserBaseRepository;
import com.axelor.apps.base.db.repo.YearBaseRepository;
import com.axelor.apps.base.db.repo.YearRepository;
import com.axelor.apps.base.service.ABCAnalysisService;
import com.axelor.apps.base.service.ABCAnalysisServiceImpl;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.AddressServiceImpl;
import com.axelor.apps.base.service.BankDetailsService;
import com.axelor.apps.base.service.BankDetailsServiceImpl;
import com.axelor.apps.base.service.BankService;
import com.axelor.apps.base.service.BankServiceImpl;
import com.axelor.apps.base.service.BarcodeGeneratorService;
import com.axelor.apps.base.service.BarcodeGeneratorServiceImpl;
import com.axelor.apps.base.service.CompanyService;
import com.axelor.apps.base.service.CompanyServiceImpl;
import com.axelor.apps.base.service.DMSImportWizardService;
import com.axelor.apps.base.service.DMSImportWizardServiceImpl;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.DurationServiceImpl;
import com.axelor.apps.base.service.FrequencyService;
import com.axelor.apps.base.service.FrequencyServiceImpl;
import com.axelor.apps.base.service.MailServiceBaseImpl;
import com.axelor.apps.base.service.MapRestService;
import com.axelor.apps.base.service.MapRestServiceImpl;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PartnerPriceListServiceImpl;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.PartnerServiceImpl;
import com.axelor.apps.base.service.PeriodService;
import com.axelor.apps.base.service.PeriodServiceImpl;
import com.axelor.apps.base.service.ProductMultipleQtyService;
import com.axelor.apps.base.service.ProductMultipleQtyServiceImpl;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.ProductServiceImpl;
import com.axelor.apps.base.service.TeamTaskService;
import com.axelor.apps.base.service.TeamTaskServiceImpl;
import com.axelor.apps.base.service.TradingNameService;
import com.axelor.apps.base.service.TradingNameServiceImpl;
import com.axelor.apps.base.service.YearService;
import com.axelor.apps.base.service.YearServiceImpl;
import com.axelor.apps.base.service.advanced.imports.AdvancedImportService;
import com.axelor.apps.base.service.advanced.imports.AdvancedImportServiceImpl;
import com.axelor.apps.base.service.advanced.imports.DataImportService;
import com.axelor.apps.base.service.advanced.imports.DataImportServiceImpl;
import com.axelor.apps.base.service.advanced.imports.FileFieldService;
import com.axelor.apps.base.service.advanced.imports.FileFieldServiceImpl;
import com.axelor.apps.base.service.advanced.imports.FileTabService;
import com.axelor.apps.base.service.advanced.imports.FileTabServiceImpl;
import com.axelor.apps.base.service.advancedExport.AdvancedExportService;
import com.axelor.apps.base.service.advancedExport.AdvancedExportServiceImpl;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.base.service.app.AppServiceImpl;
import com.axelor.apps.base.service.imports.ConvertDemoDataFileService;
import com.axelor.apps.base.service.imports.ConvertDemoDataFileServiceImpl;
import com.axelor.apps.base.service.imports.ImportCityService;
import com.axelor.apps.base.service.imports.ImportCityServiceImpl;
import com.axelor.apps.base.service.imports.ImportDemoDataService;
import com.axelor.apps.base.service.imports.ImportDemoDataServiceImpl;
import com.axelor.apps.base.service.message.MailAccountServiceBaseImpl;
import com.axelor.apps.base.service.message.MessageServiceBaseImpl;
import com.axelor.apps.base.service.message.TemplateMessageServiceBaseImpl;
import com.axelor.apps.base.service.tax.AccountManagementService;
import com.axelor.apps.base.service.tax.AccountManagementServiceImpl;
import com.axelor.apps.base.service.tax.FiscalPositionService;
import com.axelor.apps.base.service.tax.FiscalPositionServiceImpl;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.base.service.user.UserServiceImpl;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningServiceImp;
import com.axelor.apps.message.service.MailAccountServiceImpl;
import com.axelor.apps.message.service.MailServiceMessageImpl;
import com.axelor.apps.message.service.MessageServiceImpl;
import com.axelor.apps.message.service.TemplateMessageServiceImpl;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.base.service.ical.ICalendarEventService;
import com.axelor.base.service.ical.ICalendarEventServiceImpl;
import com.axelor.team.db.repo.TeamTaskRepository;

public class BaseModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AddressService.class).to(AddressServiceImpl.class);
    bind(AdvancedExportService.class).to(AdvancedExportServiceImpl.class);
    bind(UserService.class).to(UserServiceImpl.class);
    bind(MessageServiceImpl.class).to(MessageServiceBaseImpl.class);
    bind(MailAccountServiceImpl.class).to(MailAccountServiceBaseImpl.class);
    bind(AccountManagementService.class).to(AccountManagementServiceImpl.class);
    bind(FiscalPositionService.class).to(FiscalPositionServiceImpl.class);
    bind(ProductService.class).to(ProductServiceImpl.class);
    bind(TemplateMessageServiceImpl.class).to(TemplateMessageServiceBaseImpl.class);
    bind(PartnerRepository.class).to(PartnerBaseRepository.class);
    bind(DurationRepository.class).to(DurationBaseRepository.class);
    bind(DurationService.class).to(DurationServiceImpl.class);
    bind(AppBaseService.class).to(AppBaseServiceImpl.class);
    bind(SequenceRepository.class).to(SequenceBaseRepository.class);
    bind(ProductRepository.class).to(ProductBaseRepository.class);
    bind(WeeklyPlanningService.class).to(WeeklyPlanningServiceImp.class);
    bind(MailServiceMessageImpl.class).to(MailServiceBaseImpl.class);
    bind(AddressRepository.class).to(AddressBaseRepository.class);
    bind(YearRepository.class).to(YearBaseRepository.class);
    bind(YearService.class).to(YearServiceImpl.class);
    bind(AppServiceImpl.class).to(AppBaseServiceImpl.class);
    bind(AppService.class).to(AppServiceImpl.class);
    bind(BankService.class).to(BankServiceImpl.class);
    bind(BankRepository.class).to(BankBaseRepository.class);
    bind(CompanyService.class).to(CompanyServiceImpl.class);
    bind(BankAddressRepository.class).to(BankAddressBaseRepository.class);
    bind(UserRepository.class).to(UserBaseRepository.class);
    bind(BankDetailsService.class).to(BankDetailsServiceImpl.class);
    bind(ImportCityService.class).to(ImportCityServiceImpl.class);
    bind(BaseBatchRepository.class).to(BaseBatchBaseRepository.class);
    bind(MailBatchRepository.class).to(MailBatchBaseRepository.class);
    bind(AlarmEngineBatchRepository.class).to(AlarmEngineBatchBaseRepository.class);
    bind(TradingNameService.class).to(TradingNameServiceImpl.class);
    bind(PartnerPriceListService.class).to(PartnerPriceListServiceImpl.class);
    bind(ICalendarEventService.class).to(ICalendarEventServiceImpl.class);
    bind(ICalendarEventRepository.class).to(ICalendarEventManagementRepository.class);
    bind(ProductMultipleQtyService.class).to(ProductMultipleQtyServiceImpl.class);
    bind(BarcodeGeneratorService.class).to(BarcodeGeneratorServiceImpl.class);
    PartnerAddressRepository.modelPartnerFieldMap.put(PartnerAddress.class.getName(), "_parent");
    bind(PeriodService.class).to(PeriodServiceImpl.class);
    bind(ConvertDemoDataFileService.class).to(ConvertDemoDataFileServiceImpl.class);
    bind(ImportDemoDataService.class).to(ImportDemoDataServiceImpl.class);
    bind(MapRestService.class).to(MapRestServiceImpl.class);
    bind(TaxRepository.class).to(TaxBaseRepository.class);
    bind(TeamTaskRepository.class).to(TeamTaskBaseRepository.class);
    bind(TeamTaskService.class).to(TeamTaskServiceImpl.class);
    bind(FrequencyService.class).to(FrequencyServiceImpl.class);
    bind(MailingListMessageRepository.class).to(MailingListMessageBaseRepository.class);
    bind(ABCAnalysisService.class).to(ABCAnalysisServiceImpl.class);
    bind(ABCAnalysisRepository.class).to(ABCAnalysisBaseRepository.class);
    bind(DMSImportWizardService.class).to(DMSImportWizardServiceImpl.class);
    bind(AdvancedImportService.class).to(AdvancedImportServiceImpl.class);
    bind(DataImportService.class).to(DataImportServiceImpl.class);
    bind(FileTabService.class).to(FileTabServiceImpl.class);
    bind(FileFieldService.class).to(FileFieldServiceImpl.class);
    bind(PartnerService.class).to(PartnerServiceImpl.class);
    bind(BirtTemplateRepository.class).to(BirtTemplateBaseRepository.class);
  }
}

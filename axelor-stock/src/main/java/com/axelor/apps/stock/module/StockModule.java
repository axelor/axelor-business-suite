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
package com.axelor.apps.stock.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.db.repo.PartnerAddressRepository;
import com.axelor.apps.base.db.repo.ProductBaseRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.InventoryManagementRepository;
import com.axelor.apps.stock.db.repo.InventoryRepository;
import com.axelor.apps.stock.db.repo.LogisticalFormRepository;
import com.axelor.apps.stock.db.repo.LogisticalFormStockRepository;
import com.axelor.apps.stock.db.repo.ProductStockRepository;
import com.axelor.apps.stock.db.repo.StockCorrectionRepository;
import com.axelor.apps.stock.db.repo.StockCorrectionStockRepository;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationLineStockRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockLocationStockRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineStockRepository;
import com.axelor.apps.stock.db.repo.StockMoveManagementRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.db.repo.TrackingNumberManagementRepository;
import com.axelor.apps.stock.db.repo.TrackingNumberRepository;
import com.axelor.apps.stock.service.AddressServiceStockImpl;
import com.axelor.apps.stock.service.LogisticalFormLineService;
import com.axelor.apps.stock.service.LogisticalFormLineServiceImpl;
import com.axelor.apps.stock.service.LogisticalFormService;
import com.axelor.apps.stock.service.LogisticalFormServiceImpl;
import com.axelor.apps.stock.service.PartnerProductQualityRatingService;
import com.axelor.apps.stock.service.PartnerProductQualityRatingServiceImpl;
import com.axelor.apps.stock.service.PartnerStockSettingsService;
import com.axelor.apps.stock.service.PartnerStockSettingsServiceImpl;
import com.axelor.apps.stock.service.StockCorrectionService;
import com.axelor.apps.stock.service.StockCorrectionServiceImpl;
import com.axelor.apps.stock.service.StockHistoryService;
import com.axelor.apps.stock.service.StockHistoryServiceImpl;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.stock.service.StockLocationLineServiceImpl;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.stock.service.StockLocationServiceImpl;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveLineServiceImpl;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.StockMoveServiceImpl;
import com.axelor.apps.stock.service.StockMoveToolService;
import com.axelor.apps.stock.service.StockMoveToolServiceImpl;
import com.axelor.apps.stock.service.StockRulesService;
import com.axelor.apps.stock.service.StockRulesServiceImpl;
import com.axelor.apps.stock.service.TrackingNumberConfigurationService;
import com.axelor.apps.stock.service.TrackingNumberConfigurationServiceImpl;
import com.axelor.apps.stock.service.UserServiceStock;
import com.axelor.apps.stock.service.UserServiceStockImpl;
import com.axelor.apps.stock.service.WeightedAveragePriceService;
import com.axelor.apps.stock.service.WeightedAveragePriceServiceImpl;
import com.axelor.apps.stock.service.app.AppStockService;
import com.axelor.apps.stock.service.app.AppStockServiceImpl;
import com.axelor.apps.stock.service.stockmove.print.ConformityCertificatePrintService;
import com.axelor.apps.stock.service.stockmove.print.ConformityCertificatePrintServiceImpl;
import com.axelor.apps.stock.service.stockmove.print.PickingStockMovePrintService;
import com.axelor.apps.stock.service.stockmove.print.PickingStockMovePrintServiceimpl;
import com.axelor.apps.stock.service.stockmove.print.StockMovePrintService;
import com.axelor.apps.stock.service.stockmove.print.StockMovePrintServiceImpl;
import com.axelor.events.StockLoginObserver;

public class StockModule extends AxelorModule {

  @Override
  protected void configure() {
    bind(AddressServiceStockImpl.class);
    bind(StockRulesService.class).to(StockRulesServiceImpl.class);
    bind(InventoryRepository.class).to(InventoryManagementRepository.class);
    bind(StockMoveRepository.class).to(StockMoveManagementRepository.class);
    bind(StockLocationLineService.class).to(StockLocationLineServiceImpl.class);
    bind(StockMoveLineService.class).to(StockMoveLineServiceImpl.class);
    bind(StockMoveService.class).to(StockMoveServiceImpl.class);
    bind(StockLocationService.class).to(StockLocationServiceImpl.class);
    bind(ProductBaseRepository.class).to(ProductStockRepository.class);
    bind(PartnerProductQualityRatingService.class).to(PartnerProductQualityRatingServiceImpl.class);
    bind(LogisticalFormService.class).to(LogisticalFormServiceImpl.class);
    bind(LogisticalFormLineService.class).to(LogisticalFormLineServiceImpl.class);
    bind(LogisticalFormRepository.class).to(LogisticalFormStockRepository.class);
    bind(StockLocationRepository.class).to(StockLocationStockRepository.class);
    bind(PartnerStockSettingsService.class).to(PartnerStockSettingsServiceImpl.class);
    bind(AppStockService.class).to(AppStockServiceImpl.class);
    bind(StockMoveLineRepository.class).to(StockMoveLineStockRepository.class);
    PartnerAddressRepository.modelPartnerFieldMap.put(StockMove.class.getName(), "partner");
    bind(TrackingNumberRepository.class).to(TrackingNumberManagementRepository.class);
    bind(StockMovePrintService.class).to(StockMovePrintServiceImpl.class);
    bind(StockMoveToolService.class).to(StockMoveToolServiceImpl.class);
    bind(PickingStockMovePrintService.class).to(PickingStockMovePrintServiceimpl.class);
    bind(ConformityCertificatePrintService.class).to(ConformityCertificatePrintServiceImpl.class);
    bind(StockLocationLineRepository.class).to(StockLocationLineStockRepository.class);
    bind(StockCorrectionService.class).to(StockCorrectionServiceImpl.class);
    bind(WeightedAveragePriceService.class).to(WeightedAveragePriceServiceImpl.class);
    bind(StockHistoryService.class).to(StockHistoryServiceImpl.class);
    bind(StockCorrectionRepository.class).to(StockCorrectionStockRepository.class);
    bind(TrackingNumberConfigurationService.class).to(TrackingNumberConfigurationServiceImpl.class);
    bind(TrackingNumberConfigurationService.class).to(TrackingNumberConfigurationServiceImpl.class);
    bind(StockLoginObserver.class);
    bind(UserServiceStock.class).to(UserServiceStockImpl.class);
  }
}

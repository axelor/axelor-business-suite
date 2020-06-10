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
package com.axelor.apps.supplychain.service;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.db.MrpLine;
import com.axelor.apps.supplychain.db.repo.MrpLineRepository;
import com.axelor.apps.supplychain.db.repo.MrpRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectedStockServiceImpl implements ProjectedStockService {

  @Inject StockLocationRepository stockLocationRepository;

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public List<MrpLine> createProjectedStock(Long productId, Long companyId, Long stockLocationId)
      throws AxelorException {
    Product product = Beans.get(ProductRepository.class).find(productId);
    Company company = Beans.get(CompanyRepository.class).find(companyId);
    StockLocation stockLocation = stockLocationRepository.find(stockLocationId);
    Mrp mrp = new Mrp();
    stockLocation = findStockLocation(company, stockLocation);
    mrp.addStockLocationSetItem(stockLocation);
    // If a company has no stockLocation
    if (mrp.getStockLocationSet() == null) {
      return Collections.emptyList();
    }
    mrp.addProductSetItem(product);
    mrp = Beans.get(MrpRepository.class).save(mrp);
    mrp = Beans.get(MrpService.class).createProjectedStock(mrp, product, company, stockLocation);

    List<MrpLine> mrpLineList =
        Beans.get(MrpLineRepository.class)
            .all()
            .filter("self.mrp = ?1 AND self.product = ?2 AND self.qty != 0", mrp, product)
            .order("maturityDate")
            .order("mrpLineType.typeSelect")
            .order("mrpLineType.sequence")
            .order("id")
            .fetch();

    if (mrpLineList.isEmpty()) {
      List<MrpLine> mrpLineListToDelete =
          Beans.get(MrpLineRepository.class).all().filter("self.mrp = ?1", mrp).fetch();
      removeMrpAndMrpLine(mrpLineListToDelete);
      return Collections.emptyList();
    }

    for (MrpLine mrpLine : mrpLineList) {
      mrpLine.setCompany(mrpLine.getStockLocation().getCompany());
      mrpLine.setUnit(mrpLine.getProduct().getUnit());
    }
    return mrpLineList;
  }

  protected StockLocation findStockLocation(Company company, StockLocation stockLocation) {
    if (stockLocation != null) {
      return stockLocation;
    } else if (company != null) {
      return stockLocationRepository
          .all()
          .filter("self.company.id = ?1", company.getId())
          .fetchOne();
    }
    return stockLocationRepository.all().fetchOne();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Long> getProductIdCompanyIdStockLocationIdFromContext(Context context) {
    Long productId = 0L;
    Long companyId = 0L;
    Long stockLocationId = 0L;
    Map<String, Long> mapId = new HashMap<>();

    LinkedHashMap<String, Object> productHashMap =
        (LinkedHashMap<String, Object>) context.get("product");
    if (productHashMap != null) {
      productId = Long.valueOf(productHashMap.get("id").toString());
    } else {
      productHashMap = (LinkedHashMap<String, Object>) context.get("$product");
      if (productHashMap != null) {
        productId = Long.valueOf(productHashMap.get("id").toString());
      } else {
        return null;
      }
    }
    LinkedHashMap<String, Object> companyHashMap =
        (LinkedHashMap<String, Object>) context.get("company");
    if (companyHashMap != null) {
      companyId = Long.valueOf(companyHashMap.get("id").toString());
    } else {
      companyHashMap = (LinkedHashMap<String, Object>) context.get("$company");
      if (companyHashMap != null) {
        companyId = Long.valueOf(companyHashMap.get("id").toString());
      }
    }
    LinkedHashMap<String, Object> stockLocationHashMap =
        (LinkedHashMap<String, Object>) context.get("stockLocation");
    if (stockLocationHashMap != null) {
      stockLocationId = Long.valueOf(stockLocationHashMap.get("id").toString());
    } else {
      stockLocationHashMap = (LinkedHashMap<String, Object>) context.get("$stockLocation");
      if (stockLocationHashMap != null) {
        stockLocationId = Long.valueOf(stockLocationHashMap.get("id").toString());
      }
    }

    mapId.put("productId", productId);
    mapId.put("companyId", companyId);
    mapId.put("stockLocationId", stockLocationId);
    return mapId;
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public void removeMrpAndMrpLine(List<MrpLine> mrpLineList) {
    if (mrpLineList != null && !mrpLineList.isEmpty()) {
      Long mrpId = mrpLineList.get(0).getMrp().getId();
      Beans.get(MrpLineRepository.class).all().filter("self.mrp.id = ?1", mrpId).remove();
      Beans.get(MrpRepository.class).all().filter("self.id = ?1", mrpId).remove();
    }
  }
}

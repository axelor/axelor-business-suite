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
package com.axelor.apps.production.service.app;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.production.db.ConfiguratorBOM;
import com.axelor.apps.production.service.configurator.ConfiguratorBomService;
import com.axelor.apps.sale.db.Configurator;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.configurator.ConfiguratorServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.JsonContext;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ConfiguratorServiceProductionImpl extends ConfiguratorServiceImpl {

  @Inject
  public ConfiguratorServiceProductionImpl(AppBaseService appBaseService) {
    super(appBaseService);
  }

  /**
   * In this implementation, we also create a bill of materials.
   *
   * @param configurator
   * @param jsonAttributes
   * @param jsonIndicators
   * @throws AxelorException
   */
  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generate(
      Configurator configurator, JsonContext jsonAttributes, JsonContext jsonIndicators)
      throws AxelorException {
    super.generate(configurator, jsonAttributes, jsonIndicators);
    ConfiguratorBOM configuratorBOM = configurator.getConfiguratorCreator().getConfiguratorBom();
    if (configuratorBOM != null) {
      Product generatedProduct = configurator.getProduct();
      Beans.get(ConfiguratorBomService.class)
          .generateBillOfMaterial(configuratorBOM, jsonAttributes, 0, generatedProduct)
          .ifPresent(generatedProduct::setDefaultBillOfMaterial);
    }
  }

  /**
   * In this implementation, we also create a bill of material.
   *
   * @throws ClassNotFoundException
   */
  @Override
  protected SaleOrderLine generateSaleOrderLine(
      Configurator configurator,
      JsonContext jsonAttributes,
      JsonContext jsonIndicators,
      SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException {

    SaleOrderLine saleOrderLine =
        super.generateSaleOrderLine(configurator, jsonAttributes, jsonIndicators, saleOrder);
    ConfiguratorBOM configuratorBOM = configurator.getConfiguratorCreator().getConfiguratorBom();
    if (configuratorBOM != null) {
      Beans.get(ConfiguratorBomService.class)
          .generateBillOfMaterial(configuratorBOM, jsonAttributes, 0, null)
          .ifPresent(saleOrderLine::setBillOfMaterial);
    }
    return saleOrderLine;
  }
}

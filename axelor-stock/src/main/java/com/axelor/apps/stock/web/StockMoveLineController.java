/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.stock.web;

import com.axelor.apps.base.db.Wizard;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class StockMoveLineController {

  @Inject protected StockMoveLineService stockMoveLineService;

  @Inject protected StockMoveLineRepository stockMoveLineRepo;

  @Inject protected StockLocationRepository stockLocationRepo;

  public void compute(ActionRequest request, ActionResponse response) throws AxelorException {
    StockMoveLine stockMoveLine = request.getContext().asType(StockMoveLine.class);
    StockMove stockMove = stockMoveLine.getStockMove();
    if (stockMove == null) {
      Context parentContext = request.getContext().getParent();
      Context superParentContext = parentContext.getParent();
      if (parentContext.getContextClass().equals(StockMove.class)) {
        stockMove = parentContext.asType(StockMove.class);
      } else if (superParentContext.getContextClass().equals(StockMove.class)) {
        stockMove = superParentContext.asType(StockMove.class);
      } else {
        return;
      }
    }
    stockMoveLine = stockMoveLineService.compute(stockMoveLine, stockMove);
    response.setValue("unitPriceUntaxed", stockMoveLine.getUnitPriceUntaxed());
    response.setValue("unitPriceTaxed", stockMoveLine.getUnitPriceTaxed());
  }

  public void setProductInfo(ActionRequest request, ActionResponse response) {

    StockMoveLine stockMoveLine;

    try {
      stockMoveLine = request.getContext().asType(StockMoveLine.class);
      StockMove stockMove = stockMoveLine.getStockMove();

      if (stockMove == null) {
        stockMove = request.getContext().getParent().asType(StockMove.class);
      }

      stockMoveLineService.setProductInfo(stockMove, stockMoveLine, stockMove.getCompany());
      response.setValues(stockMoveLine);
    } catch (Exception e) {
      stockMoveLine = new StockMoveLine();
      response.setValues(Mapper.toMap(stockMoveLine));
      TraceBackService.trace(response, e, ResponseMessageType.INFORMATION);
    }
  }

  public void emptyLine(ActionRequest request, ActionResponse response) {
    StockMoveLine stockMoveLine = request.getContext().asType(StockMoveLine.class);
    if (stockMoveLine.getLineTypeSelect() != StockMoveLineRepository.TYPE_NORMAL) {
      Map<String, Object> newStockMoveLine = Mapper.toMap(new StockMoveLine());
      newStockMoveLine.put("qty", BigDecimal.ZERO);
      newStockMoveLine.put("id", stockMoveLine.getId());
      newStockMoveLine.put("version", stockMoveLine.getVersion());
      newStockMoveLine.put("lineTypeSelect", stockMoveLine.getLineTypeSelect());
      response.setValues(newStockMoveLine);
    }
  }

  public void splitStockMoveLineByTrackingNumber(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();

    if (context.get("trackingNumbers") == null) {
      response.setAlert(I18n.get(IExceptionMessage.TRACK_NUMBER_WIZARD_NO_RECORD_ADDED_ERROR));
    } else {
      @SuppressWarnings("unchecked")
      LinkedHashMap<String, Object> stockMoveLineMap =
          (LinkedHashMap<String, Object>) context.get("_stockMoveLine");
      Integer stockMoveLineId = (Integer) stockMoveLineMap.get("id");
      StockMoveLine stockMoveLine =
          Beans.get(StockMoveLineRepository.class).find(new Long(stockMoveLineId));

      @SuppressWarnings("unchecked")
      ArrayList<LinkedHashMap<String, Object>> trackingNumbers =
          (ArrayList<LinkedHashMap<String, Object>>) context.get("trackingNumbers");

      stockMoveLineService.splitStockMoveLineByTrackingNumber(stockMoveLine, trackingNumbers);
      response.setCanClose(true);
    }
  }

  public void openTrackNumberWizard(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    StockMoveLine stockMoveLine = context.asType(StockMoveLine.class);
    StockMove stockMove = null;
    if (context.getParent() != null
        && context.getParent().get("_model").equals("com.axelor.apps.stock.db.StockMove")) {
      stockMove = context.getParent().asType(StockMove.class);
    } else if (stockMoveLine.getStockMove() != null
        && stockMoveLine.getStockMove().getId() != null) {
      stockMove = Beans.get(StockMoveRepository.class).find(stockMoveLine.getStockMove().getId());
    }

    boolean _hasWarranty = false, _isPerishable = false;
    if (stockMoveLine.getProduct() != null) {
      _hasWarranty = stockMoveLine.getProduct().getHasWarranty();
      _isPerishable = stockMoveLine.getProduct().getIsPerishable();
    }
    response.setView(
        ActionView.define(I18n.get(IExceptionMessage.TRACK_NUMBER_WIZARD_TITLE))
            .model(Wizard.class.getName())
            .add("form", "stock-move-line-track-number-wizard-form")
            .param("popup", "reload")
            .param("show-toolbar", "false")
            .param("show-confirm", "false")
            .param("width", "500")
            .param("popup-save", "false")
            .context("_stockMove", stockMove)
            .context("_stockMoveLine", stockMoveLine)
            .context("_hasWarranty", _hasWarranty)
            .context("_isPerishable", _isPerishable)
            .map());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void computeAvailableQty(ActionRequest request, ActionResponse response) {

    Context context = request.getContext();
    StockMoveLine stockMoveLineContext = context.asType(StockMoveLine.class);
    StockMoveLine stockMoveLine = null;
    if (stockMoveLineContext.getId() != null) {
      stockMoveLine = stockMoveLineRepo.find(stockMoveLineContext.getId());
      if (stockMoveLineContext.getProduct() != null
          && !stockMoveLineContext.getProduct().equals(stockMoveLine.getProduct())) {
        stockMoveLine = stockMoveLineContext;
      }
    } else {
      stockMoveLine = stockMoveLineContext;
    }

    StockLocation stockLocation = null;
    if (context.get("_parent") != null
        && ((Map) context.get("_parent")).get("fromStockLocation") != null) {

      Map<String, Object> _parent = (Map<String, Object>) context.get("_parent");

      stockLocation =
          stockLocationRepo.find(
              Long.parseLong(((Map) _parent.get("fromStockLocation")).get("id").toString()));

    } else if (stockMoveLine.getStockMove() != null) {
      stockLocation = stockMoveLine.getStockMove().getFromStockLocation();
    }

    if (stockLocation != null) {
      stockMoveLineService.updateAvailableQty(stockMoveLine, stockLocation);
      response.setValue("$availableQty", stockMoveLine.getAvailableQty());
      response.setValue("$availableQtyForProduct", stockMoveLine.getAvailableQtyForProduct());
    }
  }

  public void setProductDomain(ActionRequest request, ActionResponse response) {
    Context context = request.getContext();
    StockMoveLine stockMoveLine = context.asType(StockMoveLine.class);
    StockMove stockMove =
        context.getParent() != null
            ? context.getParent().asType(StockMove.class)
            : stockMoveLine.getStockMove();
    String domain = stockMoveLineService.createDomainForProduct(stockMoveLine, stockMove);
    response.setAttr("product", "domain", domain);
  }
}

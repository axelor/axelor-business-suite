/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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

import java.util.Map;

import com.axelor.apps.stock.db.LogisticalForm;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.LogisticalFormLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.InconsistentLogisticalFormLines;
import com.axelor.apps.stock.service.LogisticalFormService;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class LogisticalFormController {

	public void addStockMove(ActionRequest request, ActionResponse response) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> stockMoveMap = (Map<String, Object>) request.getContext().get("stockMove");
			if (stockMoveMap != null) {
				StockMove stockMove = Mapper.toBean(StockMove.class, stockMoveMap);
				stockMove = Beans.get(StockMoveRepository.class).find(stockMove.getId());

				if (stockMove.getStockMoveLineList() != null) {
					LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
					Beans.get(LogisticalFormService.class).addDetailLines(logisticalForm, stockMove);
					response.setValue("logisticalFormLineList", logisticalForm.getLogisticalFormLineList());
					response.setValue("stockMove", null);
				}
			}
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void compute(ActionRequest request, ActionResponse response) {
		try {
			LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
			Beans.get(LogisticalFormService.class).compute(logisticalForm);
			response.setValue("totalNetWeight", logisticalForm.getTotalNetWeight());
			response.setValue("totalGrossWeight", logisticalForm.getTotalGrossWeight());
			response.setValue("totalVolume", logisticalForm.getTotalVolume());
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void addPallet(ActionRequest request, ActionResponse response) {
		try {
			LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
			Beans.get(LogisticalFormService.class).addParcelPalletLine(logisticalForm,
					LogisticalFormLineRepository.TYPE_PALLET);
			response.setValue("logisticalFormLineList", logisticalForm.getLogisticalFormLineList());
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void addParcel(ActionRequest request, ActionResponse response) {
		try {
			LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
			Beans.get(LogisticalFormService.class).addParcelPalletLine(logisticalForm,
					LogisticalFormLineRepository.TYPE_PARCEL);
			response.setValue("logisticalFormLineList", logisticalForm.getLogisticalFormLineList());
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void checkLines(ActionRequest request, ActionResponse response) {
		try {
			LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
			Beans.get(LogisticalFormService.class).checkLines(logisticalForm);
		} catch (InconsistentLogisticalFormLines e) {
			response.setAlert(e.getLocalizedMessage());
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}
	
	public void setStockMoveDomain(ActionRequest request, ActionResponse response) {
		try {
			LogisticalForm logisticalForm = request.getContext().asType(LogisticalForm.class);
			String domain = Beans.get(LogisticalFormService.class).getStockMoveDomain(logisticalForm);
			response.setAttr("stockMove", "domain", domain);
		} catch (Exception e) {
			TraceBackService.trace(response, e);
		}
	}

}

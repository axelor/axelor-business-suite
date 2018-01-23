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
package com.axelor.apps.supplychain.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockMoveLineController {

	@Inject
	private StockMoveLineRepository stockMoveLineRepo;
	
	public void getProductPrice(ActionRequest request, ActionResponse response) {
		
		StockMoveLine stockMoveLine = request.getContext().asType(StockMoveLine.class);
		Context context = request.getContext();
		Integer parentPackPriceSelect = (Integer) context.getParent().get("packPriceSelect");
		
		if(stockMoveLine.getPackPriceSelect() != null && stockMoveLine.getPackPriceSelect() == StockMoveLineRepository.SUBLINE_PRICE_ONLY && stockMoveLine.getLineTypeSelect() == StockMoveLineRepository.TYPE_PACK) {
				response.setValue("unitPriceUntaxed", 0.00);
				response.setValue("unitPriceTaxed", 0.00);
				
		} else if (parentPackPriceSelect != null){
			if(stockMoveLine.getIsSubLine() != null) {
				if (parentPackPriceSelect == StockMoveLineRepository.PACK_PRICE_ONLY) {
					response.setValue("unitPriceUntaxed", 0.00);
					response.setValue("unitPriceTaxed", 0.00);
				}
			}
		}
	}
	
	public List<StockMoveLine> updateQty(List<StockMoveLine> moveLines, BigDecimal oldKitQty, BigDecimal newKitQty, boolean isRealQty) {
		
		BigDecimal qty = BigDecimal.ZERO;
		
		if(moveLines != null) {
			if(newKitQty.compareTo(BigDecimal.ZERO) != 0) {
				for(StockMoveLine line : moveLines) {
					qty = (line.getQty().divide(oldKitQty, 2, RoundingMode.HALF_EVEN)).multiply(newKitQty);
					line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
					line.setRealQty(qty.setScale(2, RoundingMode.HALF_EVEN));
				}
			} else {
				for(StockMoveLine line : moveLines) {
					line.setQty(qty.setScale(2, RoundingMode.HALF_EVEN));
					line.setRealQty(qty.setScale(2, RoundingMode.HALF_EVEN));
				}
			}
		}
		
		return moveLines;
	}
	
	@Transactional
	public List<StockMoveLine> updateRealQty(List<StockMoveLine> moveLines, BigDecimal oldKitQty, BigDecimal newKitQty, boolean isRealQty) {
		
		BigDecimal qty = BigDecimal.ZERO;
		
		if(moveLines != null) {
			if(newKitQty.compareTo(BigDecimal.ZERO) != 0) {
				for(StockMoveLine line : moveLines) {
					qty = (line.getRealQty().divide(oldKitQty, 2, RoundingMode.HALF_EVEN)).multiply(newKitQty);
					line.setRealQty(qty.setScale(2, RoundingMode.HALF_EVEN));
				}
			} else {
				for(StockMoveLine line : moveLines) {
					line.setRealQty(qty.setScale(2, RoundingMode.HALF_EVEN));
				}
			}
		}
		
		return moveLines;
	}
	
	public void updateSubLineQty(ActionRequest request, ActionResponse response) {
		
		StockMoveLine titleMoveLine = request.getContext().asType(StockMoveLine.class);
		BigDecimal oldKitQty = BigDecimal.ONE;
		BigDecimal newKitQty = BigDecimal.ZERO;
		List<StockMoveLine> subLines = null;
		
		if(titleMoveLine.getId() !=null) {
			StockMoveLine line = stockMoveLineRepo.find(titleMoveLine.getId());
			titleMoveLine.setSubLineList(line.getSubLineList());
			if(line.getQty().compareTo(BigDecimal.ZERO) != 0) {
				oldKitQty = line.getQty();
			}
		} else {	
			oldKitQty = titleMoveLine.getOldQty();
		}
			
		if(titleMoveLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
			newKitQty = titleMoveLine.getQty();
		} 
		
		if(!titleMoveLine.getIsSubLine()) {
			subLines = this.updateQty(titleMoveLine.getSubLineList(), oldKitQty, newKitQty, false);
		}
		
		response.setValue("oldQty", newKitQty);
		response.setValue("subLineList", subLines);
	}
	
	public void updateSubLineRealQty(ActionRequest request, ActionResponse response) {
		
		StockMoveLine titleMoveLine = request.getContext().asType(StockMoveLine.class);
		BigDecimal oldKitQty = BigDecimal.ONE;
		BigDecimal newKitQty = BigDecimal.ZERO;
		List<StockMoveLine> subLines = null;
		
		if(titleMoveLine.getId() !=null) {
			StockMoveLine line = stockMoveLineRepo.find(titleMoveLine.getId());
			titleMoveLine.setSubLineList(line.getSubLineList());
			if(line.getQty().compareTo(BigDecimal.ZERO) != 0) {
				oldKitQty = line.getQty();
			}
		} else {	
			oldKitQty = titleMoveLine.getOldQty();
		}
			
		if(titleMoveLine.getRealQty().compareTo(BigDecimal.ZERO) != 0) {
			newKitQty = titleMoveLine.getRealQty();
		} 
		
		if(!titleMoveLine.getIsSubLine()) {
			subLines = this.updateRealQty(titleMoveLine.getSubLineList(), oldKitQty, newKitQty, true);
		}
		
		response.setValue("oldQty", newKitQty);
		response.setValue("subLineList", subLines);
	}
	
	public void resetSubLines(ActionRequest request, ActionResponse response) {
		
		StockMoveLine packLine = request.getContext().asType(StockMoveLine.class);
		List<StockMoveLine> subLines = packLine.getSubLineList();
		
		if(subLines != null) {
			for(StockMoveLine line : subLines) {
				line.setUnitPriceTaxed(BigDecimal.ZERO);
				line.setUnitPriceUntaxed(BigDecimal.ZERO);
			}
		}
		response.setValue("subLineList", subLines);
	}
}

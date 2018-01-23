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
package com.axelor.apps.supplychain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.SaleOrderService;
import com.axelor.apps.stock.db.PartnerDefaultLocation;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockMoveLineService;
import com.axelor.apps.stock.service.StockMoveService;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.supplychain.db.SupplyChainConfig;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderStockServiceImpl implements SaleOrderStockService  {

	protected StockMoveService stockMoveService;
	protected StockMoveLineService stockMoveLineService;
	protected StockConfigService stockConfigService;
	protected StockLocationRepository stockLocationRepo;
	protected StockMoveRepository stockMoveRepo;
	protected UnitConversionService unitConversionService;
	protected SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain;

    @Inject
    public SaleOrderStockServiceImpl(StockMoveService stockMoveService, StockMoveLineService stockMoveLineService,
            StockConfigService stockConfigService, StockLocationRepository stockLocationRepo, StockMoveRepository stockMoveRepo,
            UnitConversionService unitConversionService,
            SaleOrderLineServiceSupplyChain saleOrderLineServiceSupplyChain) {

        this.stockMoveService = stockMoveService;
        this.stockMoveLineService = stockMoveLineService;
        this.stockConfigService = stockConfigService;
        this.stockLocationRepo = stockLocationRepo;
        this.stockMoveRepo = stockMoveRepo;
        this.unitConversionService = unitConversionService;
        this.saleOrderLineServiceSupplyChain = saleOrderLineServiceSupplyChain;
    }

	@Override
	public StockMove createStocksMovesFromSaleOrder(SaleOrder saleOrder) throws AxelorException {

	    Optional<StockMove> activeStockMove = findActiveStockMoveForSaleOrder(saleOrder);

        if (activeStockMove.isPresent()) {
            throw new AxelorException(activeStockMove.get(), IException.CONFIGURATION_ERROR,
                    I18n.get(IExceptionMessage.SO_ACTIVE_DELIVERY_STOCK_MOVE_ALREADY_EXISTS),
                    activeStockMove.get().getName(), saleOrder.getSaleOrderSeq());
        }

		Company company = saleOrder.getCompany();

		if(saleOrder.getSaleOrderLineList() != null && company != null) {
		    Beans.get(SaleOrderService.class).sortSaleOrderLineList(saleOrder);
			StockMove stockMove = this.createStockMove(saleOrder, company);

			for(SaleOrderLine saleOrderLine: saleOrder.getSaleOrderLineList()) {
				if(saleOrderLine.getProduct() != null || saleOrderLine.getTypeSelect().equals(SaleOrderLineRepository.TYPE_PACK)) {
				    BigDecimal qty = saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine);

				    if (qty.signum() > 0) {
	                    createStockMoveLine(stockMove, saleOrderLine, qty);
				    }
				}
			}

			if (stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()) {
				if (!stockMove.getStockMoveLineList().stream()
						.anyMatch(stockMoveLine -> stockMoveLine.getSaleOrderLine() != null && stockMoveLine
								.getSaleOrderLine().getTypeSelect() == SaleOrderLineRepository.TYPE_NORMAL)) {
					stockMove.setFullySpreadOverLogisticalFormsFlag(true);
				}

				stockMoveService.plan(stockMove);
				return stockMove;
			}
		}
		
		return null;
		
	}

	@Override
	public StockMove createStockMove(SaleOrder saleOrder, Company company) throws AxelorException  {
		StockLocation toLocation = findSaleOrderToLocation(saleOrder);

		StockMove stockMove = stockMoveService.createStockMove(null, saleOrder.getDeliveryAddress(), company,
				saleOrder.getClientPartner(), saleOrder.getStockLocation(), toLocation, null, saleOrder.getShipmentDate(),
				saleOrder.getDescription(), saleOrder.getShipmentMode(), saleOrder.getFreightCarrierMode());

		stockMove.setToAddressStr(saleOrder.getDeliveryAddressStr());
		stockMove.setSaleOrder(saleOrder);
		stockMove.setStockMoveLineList(new ArrayList<>());
		return stockMove;
	}

	/**
	 * @param saleOrder
	 * @return  the first default location corresponding to the partner
	 * 			and the company. Choose first the external location, else virtual.
	 *
	 * 			null if there is no default location
	 */
	protected StockLocation findSaleOrderToLocation(SaleOrder saleOrder) throws AxelorException {
		Partner partner = saleOrder.getClientPartner();
		Company company = saleOrder.getCompany();
	    if (partner == null || company == null) {
	    	return null;
		}
		List<PartnerDefaultLocation> defaultLocations = partner.getPartnerDefaultLocationList();
	    if (defaultLocations == null) {
	    	return null;
		}
		List<StockLocation> candidateLocations = defaultLocations
				.stream()
				.filter(Objects::nonNull)
				.filter(partnerDefaultLocation1 -> partnerDefaultLocation1.getCompany().equals(company))
				.map(PartnerDefaultLocation::getDefaultStockLocation)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

	    //check external or internal location
	    Optional<StockLocation> candidateNonVirtualLocation = candidateLocations
				.stream()
				.filter(stockLocation -> stockLocation.getTypeSelect() == StockLocationRepository.TYPE_EXTERNAL
						|| stockLocation.getTypeSelect() == StockLocationRepository.TYPE_INTERNAL)
				.findAny();
	    if (candidateNonVirtualLocation.isPresent()) {
	    	return candidateNonVirtualLocation.get();
		} else {
	    	//no external location found, search for virtual
	    	return candidateLocations
					.stream()
					.filter(stockLocation -> stockLocation.getTypeSelect() == StockLocationRepository.TYPE_VIRTUAL)
					.findAny()
					.orElse(stockConfigService.getCustomerVirtualLocation(stockConfigService.getStockConfig(company)));
		}
	}

	@Override
    public StockMoveLine createStockMoveLine(StockMove stockMove, SaleOrderLine saleOrderLine) throws AxelorException  {
        return createStockMoveLine(stockMove, saleOrderLine, saleOrderLineServiceSupplyChain.computeUndeliveredQty(saleOrderLine));
    }

    @Override
	public StockMoveLine createStockMoveLine(StockMove stockMove, SaleOrderLine saleOrderLine, BigDecimal qty) throws AxelorException  {

		Product product = saleOrderLine.getProduct();

		if(product != null && this.isStockMoveProduct(saleOrderLine)
				&& !ProductRepository.PRODUCT_TYPE_SUBSCRIPTABLE.equals(product.getProductTypeSelect())) {
			
			Unit unit = saleOrderLine.getProduct().getUnit();
			BigDecimal priceDiscounted = saleOrderLine.getPriceDiscounted();
			if(unit != null && !unit.equals(saleOrderLine.getUnit())){
				qty = unitConversionService.convertWithProduct(saleOrderLine.getUnit(), unit, qty, saleOrderLine.getProduct());
				priceDiscounted = unitConversionService.convertWithProduct(saleOrderLine.getUnit(), unit, priceDiscounted, saleOrderLine.getProduct());
			}
			
			BigDecimal taxRate = BigDecimal.ZERO;
			TaxLine taxLine = saleOrderLine.getTaxLine();
			if(taxLine != null)  {
				taxRate = taxLine.getValue();
			}
			
			StockMoveLine stockMoveLine = stockMoveLineService.createStockMoveLine(
					product,
					saleOrderLine.getProductName(),
					saleOrderLine.getDescription(),
					qty,
					priceDiscounted,
					unit,
					stockMove,
					StockMoveLineService.TYPE_SALES, saleOrderLine.getSaleOrder().getInAti(), taxRate);

			if (saleOrderLine.getDeliveryState() == 0) {
	            saleOrderLine.setDeliveryState(SaleOrderRepository.STATE_NOT_DELIVERED);
			}

			if (stockMoveLine != null) {
	            stockMoveLine.setSaleOrderLine(saleOrderLine);
	            stockMoveLine.setReservedQty(saleOrderLine.getReservedQty());
	            stockMove.addStockMoveLineListItem(stockMoveLine);
			}
			
			stockMoveLine.setLineTypeSelect(saleOrderLine.getTypeSelect());
			stockMoveLine.setPackPriceSelect(saleOrderLine.getPackPriceSelect());
			stockMoveLine.setIsSubLine(saleOrderLine.getIsSubLine());
			
			return stockMoveLine;
		}
		else if(saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK){
			StockMoveLine stockMoveLine = stockMoveLineService.createStockMoveLine(
					null,
					saleOrderLine.getProductName(),
					saleOrderLine.getDescription(),
					BigDecimal.ZERO,
					BigDecimal.ZERO,
					null,
					stockMove,
					StockMoveLineService.TYPE_SALES, saleOrderLine.getSaleOrder().getInAti(), null);

			saleOrderLine.setDeliveryState(SaleOrderRepository.STATE_NOT_DELIVERED);
			stockMoveLine.setSaleOrderLine(saleOrderLine);

			if(stockMoveLine != null) {
				stockMove.addStockMoveLineListItem(stockMoveLine);
			}
			return stockMoveLine;
		}
		return null;
	}



	public boolean isStockMoveProduct(SaleOrderLine saleOrderLine) throws AxelorException  {

		Company company = saleOrderLine.getSaleOrder().getCompany();
		
		SupplyChainConfig supplyChainConfig = Beans.get(SupplyChainConfigService.class).getSupplyChainConfig(company);

		Product product = saleOrderLine.getProduct();

		return (product != null
			&& ((ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect()) && supplyChainConfig.getHasOutSmForNonStorableProduct() && !product.getIsShippingCostsProduct())
				|| (ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect()) && supplyChainConfig.getHasOutSmForStorableProduct())) );
	}

    @Override
    public boolean activeStockMoveForSaleOrderExists(SaleOrder saleOrder) {
        return saleOrder.getStockMoveList() != null ? saleOrder.getStockMoveList().stream()
                .anyMatch(stockMove -> stockMove.getStatusSelect() <= StockMoveRepository.STATUS_PLANNED) : false;
    }

    @Override
    public Optional<StockMove> findActiveStockMoveForSaleOrder(SaleOrder saleOrder) {
        return saleOrder.getStockMoveList() != null ? saleOrder.getStockMoveList().stream()
                .filter(stockMove -> stockMove.getStatusSelect() <= StockMoveRepository.STATUS_PLANNED).findFirst() : Optional.empty();
    }

    @Override
    @Transactional
    public void updateDeliveryState(SaleOrder saleOrder) {
        saleOrder.setDeliveryState(computeDeliveryState(saleOrder));
    }

    private int computeDeliveryState(SaleOrder saleOrder) {
        if (saleOrder.getSaleOrderLineList() == null) {
            return 0;
        }

        int deliveryState = SaleOrderRepository.STATE_DELIVERED;
        int deliveredCount = 0;

        for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
            if (saleOrderLine.getDeliveryState() != SaleOrderRepository.STATE_DELIVERED) {
                if (saleOrderLine.getDeliveryState() == SaleOrderRepository.STATE_PARTIALLY_DELIVERED) {
                    return SaleOrderRepository.STATE_PARTIALLY_DELIVERED;
                }

                deliveryState = SaleOrderRepository.STATE_NOT_DELIVERED;
            } else {
                ++deliveredCount;
            }
        }

        if (deliveryState == SaleOrderRepository.STATE_NOT_DELIVERED && deliveredCount > 0) {
            return SaleOrderRepository.STATE_PARTIALLY_DELIVERED;
        }

        return deliveryState;
    }

}




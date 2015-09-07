/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.production.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.production.db.IManufOrder;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.ProductVariantService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ManufOrder;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.ProdProduct;
import com.axelor.apps.production.db.ProdRemains;
import com.axelor.apps.production.db.repo.ManufOrderRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ManufOrderServiceImpl extends ManufOrderRepository implements  ManufOrderService  {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Inject
	protected SequenceService sequenceService;

	@Inject
	protected OperationOrderService operationOrderService;

	@Inject
	protected ManufOrderWorkflowService manufOrderWorkflowService;

	@Inject
	protected ProductVariantService productVariantService;

	@Inject
	protected GeneralService generalService;


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public ManufOrder generateManufOrder(Product product, BigDecimal qty, int priority, boolean isToInvoice, Company company,
			BillOfMaterial billOfMaterial, LocalDateTime plannedStartDateT) throws AxelorException  {

		ManufOrder manufOrder = this.createManufOrder(product, qty, priority, IS_TO_INVOICE, company, billOfMaterial, plannedStartDateT);

		manufOrder = manufOrderWorkflowService.plan(manufOrder);

		return save(manufOrder);

	}


	@Override
	public void createToConsumeProdProductList(ManufOrder manufOrder)  {

		BigDecimal manufOrderQty = manufOrder.getQty();

		BillOfMaterial billOfMaterial = manufOrder.getBillOfMaterial();

		if(billOfMaterial.getBillOfMaterialList() != null)  {

			for(BillOfMaterial billOfMaterialLine : billOfMaterial.getBillOfMaterialList())  {

				if(!billOfMaterialLine.getHasNoManageStock())  {

					Product product = productVariantService.getProductVariant(manufOrder.getProduct(), billOfMaterialLine.getProduct());

					manufOrder.addToConsumeProdProductListItem(
							new ProdProduct(product, billOfMaterialLine.getQty().multiply(manufOrderQty), billOfMaterialLine.getUnit()));
				}
			}

		}

	}


	@Override
	public void createToProduceProdProductList(ManufOrder manufOrder)  {

		BigDecimal manufOrderQty = manufOrder.getQty();

		BillOfMaterial billOfMaterial = manufOrder.getBillOfMaterial();

		// Ajout du produit final
		manufOrder.addToProduceProdProductListItem(
				new ProdProduct(manufOrder.getProduct(), billOfMaterial.getQty().multiply(manufOrderQty), billOfMaterial.getUnit()));

		// Ajout des restes
		if(billOfMaterial.getProdRemainsList() != null)  {

			for(ProdRemains prodRemains : billOfMaterial.getProdRemainsList())  {

				Product product = productVariantService.getProductVariant(manufOrder.getProduct(), prodRemains.getProduct());

				manufOrder.addToProduceProdProductListItem(
						new ProdProduct(product, prodRemains.getQty().multiply(manufOrderQty), prodRemains.getUnit()));

			}

		}

	}


	@Override
	public ManufOrder createManufOrder(Product product, BigDecimal qty, int priority, boolean isToInvoice, Company company,
			BillOfMaterial billOfMaterial, LocalDateTime plannedStartDateT) throws AxelorException  {

		logger.debug("Création d'un OF {}", priority);

		ProdProcess prodProcess = billOfMaterial.getProdProcess();

		ManufOrder manufOrder = new ManufOrder(
				qty,
				company,
				null,
				priority,
				this.isManagedConsumedProduct(billOfMaterial),
				billOfMaterial,
				product,
				prodProcess,
				plannedStartDateT,
				IManufOrder.STATUS_DRAFT);

		if(prodProcess != null && prodProcess.getProdProcessLineList() != null)  {
			for(ProdProcessLine prodProcessLine : this._sortProdProcessLineByPriority(prodProcess.getProdProcessLineList()))  {

				manufOrder.addOperationOrderListItem(
						operationOrderService.createOperationOrder(manufOrder, prodProcessLine));

			}
		}

		if(!manufOrder.getIsConsProOnOperation())  {
			this.createToConsumeProdProductList(manufOrder);
		}

		this.createToProduceProdProductList(manufOrder);

		return manufOrder;

	}

	@Override
	@Transactional
	public void preFillOperations(ManufOrder manufOrder) throws AxelorException{

		BillOfMaterial billOfMaterial = manufOrder.getBillOfMaterial();

		manufOrder.setIsConsProOnOperation(this.isManagedConsumedProduct(billOfMaterial));

		if(manufOrder.getProdProcess() == null){
			manufOrder.setProdProcess(billOfMaterial.getProdProcess());
		}
		ProdProcess prodProcess = manufOrder.getProdProcess();

		if(manufOrder.getPlannedStartDateT() == null){
			manufOrder.setPlannedStartDateT(generalService.getTodayDateTime().toLocalDateTime());
		}

		if(prodProcess != null && prodProcess.getProdProcessLineList() != null)  {

			for(ProdProcessLine prodProcessLine : this._sortProdProcessLineByPriority(prodProcess.getProdProcessLineList()))  {
				manufOrder.addOperationOrderListItem(
						operationOrderService.createOperationOrder(manufOrder, prodProcessLine));
			}

		}

		save(manufOrder);

		manufOrder.setPlannedEndDateT(manufOrderWorkflowService.computePlannedEndDateT(manufOrder));

		if(!manufOrder.getIsConsProOnOperation())  {
			this.createToConsumeProdProductList(manufOrder);
		}

		this.createToProduceProdProductList(manufOrder);

		save(manufOrder);
	}


	/**
	 * Trier une liste de ligne de règle de template
	 *
	 * @param templateRuleLine
	 */
	public List<ProdProcessLine> _sortProdProcessLineByPriority(List<ProdProcessLine> prodProcessLineList){

		Collections.sort(prodProcessLineList, new Comparator<ProdProcessLine>() {

			@Override
			public int compare(ProdProcessLine ppl1, ProdProcessLine ppl2) {
				return ppl1.getPriority().compareTo(ppl2.getPriority());
			}
		});

		return prodProcessLineList;
	}


	@Override
	public String getManufOrderSeq() throws AxelorException  {

		String seq = sequenceService.getSequenceNumber(IAdministration.MANUF_ORDER);

		if(seq == null)  {
			throw new AxelorException(I18n.get(IExceptionMessage.MANUF_ORDER_SEQ), IException.CONFIGURATION_ERROR);
		}

		return seq;
	}


	@Override
	public boolean isManagedConsumedProduct(BillOfMaterial billOfMaterial)  {

		if(billOfMaterial != null && billOfMaterial.getProdProcess() != null && billOfMaterial.getProdProcess().getProdProcessLineList() != null)  {
			for(ProdProcessLine prodProcessLine : billOfMaterial.getProdProcess().getProdProcessLineList())  {

				if((prodProcessLine.getToConsumeProdProductList() != null && !prodProcessLine.getToConsumeProdProductList().isEmpty()))  {

					return true;

				}

			}
		}

		return false;

	}

}

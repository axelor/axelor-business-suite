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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.production.db.IProdResource;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.ProductService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.production.db.BillOfMaterial;
import com.axelor.apps.production.db.ProdProcess;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.ProdResource;
import com.axelor.apps.production.db.repo.BillOfMaterialRepository;
import com.axelor.apps.production.exceptions.IExceptionMessage;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class BillOfMaterialServiceImpl extends BillOfMaterialRepository implements BillOfMaterialService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Inject
	protected UnitConversionService unitConversionService;

	@Inject
	private ProductService productService;

	@Inject
	protected GeneralService generalService;

	@Override
	public List<BillOfMaterial> getBillOfMaterialList(Product product)  {

		return all().filter("self.product = ?1", product).fetch();


	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void computeCostPrice(BillOfMaterial billOfMaterial) throws AxelorException  {

		billOfMaterial.setCostPrice(this._computeCostPrice(billOfMaterial).setScale(generalService.getNbDecimalDigitForUnitPrice(), BigDecimal.ROUND_HALF_EVEN));

		save(billOfMaterial);
	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void updateProductCostPrice(BillOfMaterial billOfMaterial) throws AxelorException  {

		Product product = billOfMaterial.getProduct();

		product.setCostPrice(billOfMaterial.getCostPrice());

		productService.updateSalePrice(product);

		save(billOfMaterial);
	}


	protected BigDecimal _computeCostPrice(BillOfMaterial billOfMaterial) throws AxelorException  {

		BigDecimal costPrice = BigDecimal.ZERO;

		// Cout des composants
		costPrice = costPrice.add(this._computeToConsumeProduct(billOfMaterial));

		// Cout des operations
		costPrice = costPrice.add(this._computeProcess(billOfMaterial.getProdProcess()));

		return costPrice;

	}


	protected BigDecimal _computeToConsumeProduct(BillOfMaterial billOfMaterial) throws AxelorException  {

		BigDecimal costPrice = BigDecimal.ZERO;

		if(billOfMaterial.getBillOfMaterialList() != null)  {

			for(BillOfMaterial billOfMaterialLine : billOfMaterial.getBillOfMaterialList())  {

				Product product = billOfMaterialLine.getProduct();

				if(product != null)  {
					if(billOfMaterialLine.getIsRawMaterial())  {
						BigDecimal unitPrice = unitConversionService.convert(product.getUnit(), billOfMaterialLine.getUnit(), product.getCostPrice());
						costPrice = costPrice.add(unitPrice.multiply(billOfMaterialLine.getQty()));
					}
					else  {
						costPrice = costPrice.add(this._computeCostPrice(billOfMaterialLine));
					}
				}
			}
		}

		return costPrice;
	}



	protected BigDecimal _computeProcess(ProdProcess prodProcess) throws AxelorException  {

		BigDecimal costPrice = BigDecimal.ZERO;

		if(prodProcess != null && prodProcess.getProdProcessLineList() != null)  {

			for(ProdProcessLine prodProcessLine : prodProcess.getProdProcessLineList())  {

				ProdResource prodResource = prodProcessLine.getProdResource();

				if(prodResource != null)  {

					int resourceType = prodResource.getResourceTypeSelect();

					if(resourceType == IProdResource.RESOURCE_MACHINE || resourceType == IProdResource.RESOURCE_BOTH)  {

						costPrice = costPrice.add(this._computeMachineCost(prodResource));

					}

				}
			}
		}

		return costPrice;
	}


	protected BigDecimal _computeMachineCost(ProdResource prodResource)  {

		BigDecimal costPrice = BigDecimal.ZERO;

		int costType = prodResource.getCostTypeSelect();

		if(costType == IProdResource.COST_PER_CYCLE)  {

			costPrice = prodResource.getCostAmount();
		}
		else if(costType == IProdResource.COST_PER_HOUR)  {

			costPrice = (prodResource.getCostAmount().multiply(new BigDecimal(prodResource.getDurationPerCycle())).divide(new BigDecimal(3600), BigDecimal.ROUND_HALF_EVEN));

		}

		logger.debug("Machine cost : {} (Resource : {})",costPrice, prodResource.getName());

		return costPrice;
	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public BillOfMaterial customizeBillOfMaterial(SaleOrderLine saleOrderLine)  {

		BillOfMaterial billOfMaterial = saleOrderLine.getBillOfMaterial();

		if(billOfMaterial != null)  {
			BillOfMaterial personalizedBOM = JPA.copy(billOfMaterial, true);
			save(personalizedBOM);
			personalizedBOM.setName(personalizedBOM.getName() + " ("+I18n.get(IExceptionMessage.BOM_1)+" " + personalizedBOM.getId() + ")");
			personalizedBOM.setPersonalized(true);
			return personalizedBOM;
		}

		return null;

	}


}

/**
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
package com.axelor.apps.prestashop.batch;


import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.crm.db.CrmBatch;
import com.axelor.apps.crm.db.ICrmBatch;
import com.axelor.apps.crm.db.repo.CrmBatchRepository;
import com.axelor.apps.db.IPrestaShopBatch;
import com.axelor.apps.prestashop.db.PrestaShopBatch;
import com.axelor.apps.prestashop.db.repo.PrestaShopBatchRepository;
import com.axelor.apps.prestashop.service.exports.batch.ExportPrestaShop;
import com.axelor.apps.prestashop.service.imports.batch.ImportPrestaShop;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

/**
 * InvoiceBatchService est une classe implémentant l'ensemble des batchs de
 * comptabilité et assimilé.
 * 
 * @author Geoffrey DUBAUX
 * 
 * @version 0.1
 */
public class PrestaShopBatchService {

	
	@Inject
	protected PrestaShopBatchRepository prestaShopBatchRepo;
	
// Appel 	
	
	/**
	 * Lancer un batch à partir de son code.
	 * 
	 * @param batchCode
	 * 		Le code du batch souhaité.
	 * 
	 * @throws AxelorException
	 */
	public Batch run(String batchCode) throws AxelorException {
				
		Batch batch;
		PrestaShopBatch prestaShopBatch = prestaShopBatchRepo.findByCode(batchCode);
		
		if (prestaShopBatch != null){
			switch (prestaShopBatch.getActionSelect()) {
			
			case IPrestaShopBatch.BATCH_IMPORT:
				batch = importPrestaShop(prestaShopBatch);
				break;
				
			case IPrestaShopBatch.BATCH_EXPORT:
				batch = exportPrestaShop(prestaShopBatch);
				break;
				
			default:
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.BASE_BATCH_1), prestaShopBatch.getActionSelect(), batchCode), IException.INCONSISTENCY);
			}
		}
		else {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.BASE_BATCH_2), batchCode), IException.INCONSISTENCY);
		}
		return batch;
	}
	
	
	public Batch importPrestaShop(PrestaShopBatch prestaShopBatch) {
		
		return Beans.get(ImportPrestaShop.class).run(prestaShopBatch);
		
	}
	
	public Batch exportPrestaShop(PrestaShopBatch prestaShopBatch) {
		
		return Beans.get(ExportPrestaShop.class).run(prestaShopBatch);
		
	}
}

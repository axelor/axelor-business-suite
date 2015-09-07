package com.axelor.apps.business.project.service;

import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderProjectService extends SaleOrderRepository{

	@Inject
	protected GeneralService generalService;

	@Inject
	protected ProjectTaskBusinessService projectTaskBusinessService;

	@Transactional
	public ProjectTask generateProject(SaleOrder saleOrder){
		ProjectTask project = projectTaskBusinessService.generateProject(saleOrder);
		save(saleOrder);
		return project;
	}

	@Transactional
	public List<Long> generateTasks(SaleOrder saleOrder){
		List<Long> listId = new ArrayList<Long>();
		List<SaleOrderLine> saleOrderLineList = saleOrder.getSaleOrderLineList();
		for (SaleOrderLine saleOrderLine : saleOrderLineList) {
			Product product = saleOrderLine.getProduct();
			if(ProductRepository.PRODUCT_TYPE_SERVICE.equals(product.getProductTypeSelect()) && saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE){
				ProjectTask task = projectTaskBusinessService.generateTask(saleOrderLine, saleOrder.getProject());
				Beans.get(SaleOrderLineRepository.class).save(saleOrderLine);
				listId.add(task.getId());
			}
		}
		return listId;
	}

}

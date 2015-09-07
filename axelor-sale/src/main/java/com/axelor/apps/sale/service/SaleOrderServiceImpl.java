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
package com.axelor.apps.sale.service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;

import javax.persistence.Query;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.AppSettings;
import com.axelor.apps.ReportSettings;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Team;
import com.axelor.apps.base.service.DurationService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.sale.db.ISaleOrder;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.exception.IExceptionMessage;
import com.axelor.apps.sale.report.IReport;
import com.axelor.apps.tool.net.URLService;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class SaleOrderServiceImpl extends SaleOrderRepository  implements SaleOrderService {

	private static final Logger LOG = LoggerFactory.getLogger(SaleOrderServiceImpl.class);

	@Inject
	private SaleOrderLineService saleOrderLineService;

	@Inject
	private SaleOrderLineTaxService saleOrderLineTaxService;

	@Inject
	private SequenceService sequenceService;

	@Inject
	private PartnerService partnerService;

	@Inject
	protected SaleOrderRepository saleOrderRepo;

	@Inject
	protected GeneralService generalService;


	@Override
	public SaleOrder _computeSaleOrderLineList(SaleOrder saleOrder) throws AxelorException  {

		if(saleOrder.getSaleOrderLineList() != null)  {
			for(SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList())  {
				saleOrderLine.setCompanyExTaxTotal(saleOrderLineService.getAmountInCompanyCurrency(saleOrderLine.getExTaxTotal(), saleOrder));
			}
		}

		return saleOrder;
	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public SaleOrder computeSaleOrder(SaleOrder saleOrder) throws AxelorException  {

		this.initSaleOrderLineTaxList(saleOrder);

		this._computeSaleOrderLineList(saleOrder);

		this._populateSaleOrder(saleOrder);

		this._computeSaleOrder(saleOrder);

		return saleOrder;
	}


	/**
	 * Peupler un devis.
	 * <p>
	 * Cette fonction permet de déterminer les tva d'un devis.
	 * </p>
	 *
	 * @param saleOrder
	 *
	 * @throws AxelorException
	 */
	@Override
	public void _populateSaleOrder(SaleOrder saleOrder) throws AxelorException {

		LOG.debug("Peupler un devis => lignes de devis: {} ", new Object[] { saleOrder.getSaleOrderLineList().size() });

		// create Tva lines
		saleOrder.getSaleOrderLineTaxList().addAll(saleOrderLineTaxService.createsSaleOrderLineTax(saleOrder, saleOrder.getSaleOrderLineList()));

	}

	/**
	 * Calculer le montant d'une facture.
	 * <p>
	 * Le calcul est basé sur les lignes de TVA préalablement créées.
	 * </p>
	 *
	 * @param invoice
	 * @param vatLines
	 * @throws AxelorException
	 */
	@Override
	public void _computeSaleOrder(SaleOrder saleOrder) throws AxelorException {

		saleOrder.setExTaxTotal(BigDecimal.ZERO);
		saleOrder.setTaxTotal(BigDecimal.ZERO);
		saleOrder.setInTaxTotal(BigDecimal.ZERO);

		for (SaleOrderLineTax saleOrderLineVat : saleOrder.getSaleOrderLineTaxList()) {

			// Dans la devise de la comptabilité du tiers
			saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add( saleOrderLineVat.getExTaxBase() ));
			saleOrder.setTaxTotal(saleOrder.getTaxTotal().add( saleOrderLineVat.getTaxTotal() ));
			saleOrder.setInTaxTotal(saleOrder.getInTaxTotal().add( saleOrderLineVat.getInTaxTotal() ));

		}

		for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
			//Into company currency
			saleOrder.setCompanyExTaxTotal(saleOrder.getCompanyExTaxTotal().add( saleOrderLine.getCompanyExTaxTotal() ));
		}

		LOG.debug("Montant de la facture: HTT = {},  HT = {}, Taxe = {}, TTC = {}",
				new Object[] { saleOrder.getExTaxTotal(), saleOrder.getTaxTotal(), saleOrder.getInTaxTotal() });

	}


	/**
	 * Permet de réinitialiser la liste des lignes de TVA
	 * @param saleOrder
	 * 			Un devis
	 */
	@Override
	public void initSaleOrderLineTaxList(SaleOrder saleOrder) {

		if (saleOrder.getSaleOrderLineTaxList() == null) { saleOrder.setSaleOrderLineTaxList(new ArrayList<SaleOrderLineTax>()); }

		else { saleOrder.getSaleOrderLineTaxList().clear(); }

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Partner validateCustomer(SaleOrder saleOrder)  {

		Partner clientPartner = partnerService.find(saleOrder.getClientPartner().getId());
		clientPartner.setIsCustomer(true);
		clientPartner.setHasOrdered(true);

		return partnerService.save(clientPartner);
	}



	@Override
	public String getSequence(Company company) throws AxelorException  {
		String seq = sequenceService.getSequenceNumber(IAdministration.SALES_ORDER, company);
		if (seq == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.SALES_ORDER_1),company.getName()),
							IException.CONFIGURATION_ERROR);
		}
		return seq;
	}

	@Override
	public SaleOrder createSaleOrder(Company company) throws AxelorException{
		SaleOrder saleOrder = new SaleOrder();
		saleOrder.setCreationDate(generalService.getTodayDate());
		if(company != null){
			saleOrder.setCompany(company);
			saleOrder.setSaleOrderSeq(this.getSequence(company));
			saleOrder.setCurrency(company.getCurrency());
		}
		saleOrder.setSalemanUser(AuthUtils.getUser());
		saleOrder.setTeam(saleOrder.getSalemanUser().getActiveTeam());
		saleOrder.setStatusSelect(ISaleOrder.STATUS_DRAFT);
		this.computeEndOfValidityDate(saleOrder);
		return saleOrder;
	}

	@Override
	public SaleOrder createSaleOrder(User salemanUser, Company company, Partner contactPartner, Currency currency,
			LocalDate deliveryDate, String internalReference, String externalReference, LocalDate orderDate,
			PriceList priceList, Partner clientPartner, Team team) throws AxelorException  {

		LOG.debug("Création d'un devis client : Société = {},  Reference externe = {}, Client = {}",
				new Object[] { company, externalReference, clientPartner.getFullName() });

		SaleOrder saleOrder = new SaleOrder();
		saleOrder.setClientPartner(clientPartner);
		saleOrder.setCreationDate(generalService.getTodayDate());
		saleOrder.setContactPartner(contactPartner);
		saleOrder.setCurrency(currency);
		saleOrder.setExternalReference(externalReference);
		saleOrder.setOrderDate(orderDate);

		if(salemanUser == null)  {
			salemanUser = AuthUtils.getUser();
		}
		saleOrder.setSalemanUser(salemanUser);

		if(team == null)  {
			team = salemanUser.getActiveTeam();
		}

		if(company == null)  {
			company = salemanUser.getActiveCompany();
		}

		saleOrder.setCompany(company);

		saleOrder.setMainInvoicingAddress(clientPartner.getMainInvoicingAddress());
		saleOrder.setDeliveryAddress(clientPartner.getDeliveryAddress());

		if(priceList == null)  {
			priceList = clientPartner.getSalePriceList();
		}

		saleOrder.setPriceList(priceList);

		saleOrder.setSaleOrderLineList(new ArrayList<SaleOrderLine>());

		saleOrder.setSaleOrderSeq(this.getSequence(company));
		saleOrder.setStatusSelect(ISaleOrder.STATUS_DRAFT);

		this.computeEndOfValidityDate(saleOrder);

		return saleOrder;
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancelSaleOrder(SaleOrder saleOrder){
		Query q = JPA.em().createQuery("select count(*) FROM SaleOrder as self WHERE self.statusSelect = ?1 AND self.clientPartner = ?2 ");
		q.setParameter(1, ISaleOrder.STATUS_ORDER_CONFIRMED);
		q.setParameter(2, saleOrder.getClientPartner());
		if((long) q.getSingleResult() == 1)  {
			saleOrder.getClientPartner().setHasOrdered(false);
		}
		saleOrder.setStatusSelect(ISaleOrder.STATUS_CANCELED);
		this.save(saleOrder);
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void finalizeSaleOrder(SaleOrder saleOrder) throws AxelorException, IOException {
		saleOrder.setStatusSelect(ISaleOrder.STATUS_FINALIZE);
		if (saleOrder.getVersionNumber() == 1){
			saleOrder.setSaleOrderSeq(this.getSequence(saleOrder.getCompany()));
		}
		this.save(saleOrder);
		if (generalService.getGeneral().getManageSaleOrderVersion()){
			this.saveSaleOrderPDFAsAttachment(saleOrder);
		}
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void saveSaleOrderPDFAsAttachment(SaleOrder saleOrder) throws IOException{
		String
	    		filePath = AppSettings.get().get("file.upload.dir") + "/tmp",
	    		fileName = saleOrder.getSaleOrderSeq() + ((saleOrder.getVersionNumber() > 1) ? "-V" + saleOrder.getVersionNumber() : "") + "." + ReportSettings.FORMAT_PDF,
	    		birtReportURL = this.getURLSaleOrderPDF(saleOrder);

	    File file = URLService.fileDownload(birtReportURL, filePath, fileName);

		if (file != null){
			MetaFiles metaFiles = Beans.get(MetaFiles.class);
			MetaFile metaFile = metaFiles.upload(file);
			String relatedModel = generalService.getPersistentClass(saleOrder).getCanonicalName();
			//Search if there is a parent directory
			DMSFile dmsDirectory = Beans.get(DMSFileRepository.class).all().filter("self.relatedId = ?1 AND self.relatedModel = ?2 and self.isDirectory = true",
														saleOrder.getId(), relatedModel).fetchOne();
			DMSFile dmsFile = new DMSFile();
			if (dmsDirectory != null){
				dmsFile.setParent(dmsDirectory);
			}
			dmsFile.setFileName(fileName);
			dmsFile.setRelatedModel(relatedModel);
			dmsFile.setRelatedId(saleOrder.getId());
			dmsFile.setMetaFile(metaFile);
			Beans.get(DMSFileRepository.class).save(dmsFile);
		}
	}

	@Override
	public String getURLSaleOrderPDF(SaleOrder saleOrder){
		String language="";
		try{
			language = saleOrder.getClientPartner().getLanguageSelect() != null? saleOrder.getClientPartner().getLanguageSelect() : saleOrder.getCompany().getPrintingSettings().getLanguageSelect() != null ? saleOrder.getCompany().getPrintingSettings().getLanguageSelect() : "en" ;
		}catch (NullPointerException e) {
			language = "en";
		}
		language = language.equals("")? "en": language;


		return new ReportSettings(IReport.SALES_ORDER, ReportSettings.FORMAT_PDF)
							.addParam("Locale", language)
							.addParam("__locale", "fr_FR")
							.addParam("SaleOrderId", saleOrder.getId().toString())
							.getUrl();
	}

	@Override
	@Transactional
	public SaleOrder createSaleOrder(SaleOrder context){
		SaleOrder copy = saleOrderRepo.copy(context, true);
		copy.setTemplate(false);
		copy.setTemplateUser(null);
		return copy;
	}

	@Override
	@Transactional
	public SaleOrder createTemplate(SaleOrder context){
		SaleOrder copy = saleOrderRepo.copy(context, true);
		copy.setTemplate(true);
		copy.setTemplateUser(AuthUtils.getUser());
		return copy;
	}


	@Override
	public SaleOrder computeEndOfValidityDate(SaleOrder saleOrder)  {

		saleOrder.setEndOfValidityDate(
				Beans.get(DurationService.class).computeDuration(saleOrder.getDuration(), saleOrder.getCreationDate()));

		return saleOrder;

	}

}




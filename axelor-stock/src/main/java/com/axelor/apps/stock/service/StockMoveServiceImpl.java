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
package com.axelor.apps.stock.service;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.stock.report.IReport;
import com.axelor.meta.schema.actions.ActionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Country;
import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.FreightCarrierMode;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.Location;
import com.axelor.apps.stock.db.ShipmentMode;
import com.axelor.apps.stock.db.StockConfig;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.InventoryLineRepository;
import com.axelor.apps.stock.db.repo.InventoryRepository;
import com.axelor.apps.stock.db.repo.LocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveManagementRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class StockMoveServiceImpl implements StockMoveService {

	private static final Logger LOG = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );

	@Inject
	protected StockMoveLineService stockMoveLineService;

	@Inject
	private SequenceService sequenceService;

	@Inject
	private  StockMoveLineRepository stockMoveLineRepo;

	@Inject
	protected AppBaseService appBaseService;
	
	@Inject
	protected StockMoveRepository stockMoveRepo;
	
	@Inject
	protected PartnerProductQualityRatingServiceImpl partnerProductQualityRatingService;

	
	@Override
	public BigDecimal compute(StockMove stockMove){
		BigDecimal exTaxTotal = BigDecimal.ZERO;
		if(stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()){
			for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {
				exTaxTotal = exTaxTotal.add(stockMoveLine.getRealQty().multiply(stockMoveLine.getUnitPriceUntaxed()));
			}
		}
		return exTaxTotal.setScale(2, RoundingMode.HALF_UP);
	}
	
	
	
	/**
	 * Méthode permettant d'obtenir la séquence du StockMove.
	 * @param stockMoveType Type de mouvement de stock
	 * @param company la société
	 * @return la chaine contenant la séquence du StockMove
	 * @throws AxelorException Aucune séquence de StockMove n'a été configurée
	 */
	@Override
	public String getSequenceStockMove(int stockMoveType, Company company) throws AxelorException {

		String ref = "";

		switch(stockMoveType)  {
			case StockMoveRepository.TYPE_INTERNAL:
				ref = sequenceService.getSequenceNumber(IAdministration.INTERNAL, company);
				if (ref == null)  {
					throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_1),
							company.getName()), IException.CONFIGURATION_ERROR);
				}
				break;

			case StockMoveRepository.TYPE_INCOMING:
				ref = sequenceService.getSequenceNumber(IAdministration.INCOMING, company);
				if (ref == null)  {
					throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_2),
							company.getName()), IException.CONFIGURATION_ERROR);
				}
				break;

			case StockMoveRepository.TYPE_OUTGOING:
				ref = sequenceService.getSequenceNumber(IAdministration.OUTGOING, company);
				if (ref == null)  {
					throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_3),
							company.getName()), IException.CONFIGURATION_ERROR);
				}
				break;

			default:
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_4),
						company.getName()), IException.CONFIGURATION_ERROR);

		}

		return ref;
	}

	/**
	 * Méthode générique permettant de créer un StockMove.
	 * @param fromAddress l'adresse destination
	 * @param toAddress l'adresse destination
	 * @param company la société
	 * @param clientPartner le tier client
	 * @return l'objet StockMove
	 * @throws AxelorException Aucune séquence de StockMove (Livraison) n'a été configurée
	 */
	@Override
	public StockMove createStockMove(Address fromAddress, Address toAddress, Company company, Partner clientPartner, Location fromLocation, Location toLocation, LocalDate estimatedDate, String description, ShipmentMode shipmentMode, FreightCarrierMode freightCarrierMode) throws AxelorException {

		return this.createStockMove(fromAddress, toAddress, company, clientPartner, fromLocation, toLocation, null, estimatedDate, description, shipmentMode, freightCarrierMode);
	}


	/**
	 * Méthode générique permettant de créer un StockMove.
	 * @param toAddress l'adresse destination
	 * @param company la société
	 * @param clientPartner le tier client
	 * @param refSequence la séquence du StockMove
	 * @return l'objet StockMove
	 * @throws AxelorException Aucune séquence de StockMove (Livraison) n'a été configurée
	 */
	@Override
	public StockMove createStockMove(Address fromAddress, Address toAddress, Company company, Partner clientPartner, Location fromLocation, Location toLocation, LocalDate realDate, LocalDate estimatedDate, String description, ShipmentMode shipmentMode, FreightCarrierMode freightCarrierMode) throws AxelorException {

		StockMove stockMove = new StockMove();
		stockMove.setFromAddress(fromAddress);
		stockMove.setToAddress(toAddress);
		this.computeAddressStr(stockMove);
		stockMove.setCompany(company);
		stockMove.setStatusSelect(StockMoveRepository.STATUS_DRAFT);
		stockMove.setRealDate(realDate);
		stockMove.setEstimatedDate(estimatedDate);
		stockMove.setPartner(clientPartner);
		stockMove.setFromLocation(fromLocation);
		stockMove.setToLocation(toLocation);
		stockMove.setDescription(description);
		stockMove.setShipmentMode(shipmentMode);
		stockMove.setFreightCarrierMode(freightCarrierMode);

		return stockMove;
	}


	@Override
	public int getStockMoveType(Location fromLocation, Location toLocation)  {

		if(fromLocation.getTypeSelect() == LocationRepository.TYPE_INTERNAL && toLocation.getTypeSelect() == LocationRepository.TYPE_INTERNAL) {
			return StockMoveRepository.TYPE_INTERNAL;
		}
		else if(fromLocation.getTypeSelect() != LocationRepository.TYPE_INTERNAL && toLocation.getTypeSelect() == LocationRepository.TYPE_INTERNAL) {
			return StockMoveRepository.TYPE_INCOMING;
		}
		else if(fromLocation.getTypeSelect() == LocationRepository.TYPE_INTERNAL && toLocation.getTypeSelect() != LocationRepository.TYPE_INTERNAL) {
			return StockMoveRepository.TYPE_OUTGOING;
		}
		return 0;
	}


	@Override
	public void validate(StockMove stockMove) throws AxelorException  {

		this.plan(stockMove);
		this.realize(stockMove);

	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void plan(StockMove stockMove) throws AxelorException  {

		LOG.debug("Planification du mouvement de stock : {} ", new Object[] { stockMove.getStockMoveSeq() });

		if (stockMove.getExTaxTotal().compareTo(BigDecimal.ZERO) == 0) {
			stockMove.setExTaxTotal(compute(stockMove));
		}

		Location fromLocation = stockMove.getFromLocation();
		Location toLocation = stockMove.getToLocation();

		if(fromLocation == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_5),
					stockMove.getName()), IException.CONFIGURATION_ERROR);
		}
		if(toLocation == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_6),
					stockMove.getName()), IException.CONFIGURATION_ERROR);
		}

		// Set the type select
		if(stockMove.getTypeSelect() == null || stockMove.getTypeSelect() == 0)  {
			stockMove.setTypeSelect(this.getStockMoveType(fromLocation, toLocation));
		}


		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING)  {

		}

		// Set the sequence
		if(stockMove.getStockMoveSeq() == null || stockMove.getStockMoveSeq().isEmpty())  {
			stockMove.setStockMoveSeq(
					this.getSequenceStockMove(stockMove.getTypeSelect(), stockMove.getCompany()));
		}

		if(stockMove.getName() == null || stockMove.getName().isEmpty())  {
			stockMove.setName(stockMove.getStockMoveSeq());
		}

		stockMoveLineService.updateLocations(
				fromLocation,
				toLocation,
				stockMove.getStatusSelect(),
				StockMoveRepository.STATUS_PLANNED,
				stockMove.getStockMoveLineList(),
				stockMove.getEstimatedDate(),
				false);

		if(stockMove.getEstimatedDate() == null)  {
			stockMove.setEstimatedDate(appBaseService.getTodayDate());
		}

		stockMove.setStatusSelect(StockMoveRepository.STATUS_PLANNED);

		stockMoveRepo.save(stockMove);

	}

	@Override
	public String realize(StockMove stockMove) throws AxelorException {
		return realize(stockMove, true);
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public String realize(StockMove stockMove, boolean check) throws AxelorException {
		LOG.debug("Réalisation du mouvement de stock : {} ", new Object[] { stockMove.getStockMoveSeq() });

		if (check) {
			checkOngoingInventory(stockMove);
		}

		String newStockSeq = null;
		stockMoveLineService.checkConformitySelection(stockMove);
		stockMoveLineService.checkExpirationDates(stockMove);

		stockMoveLineService.updateLocations(
				stockMove.getFromLocation(),
				stockMove.getToLocation(),
				stockMove.getStatusSelect(),
				StockMoveRepository.STATUS_REALIZED,
				stockMove.getStockMoveLineList(),
				stockMove.getEstimatedDate(),
				true);
		
		stockMoveLineService.storeCustomsCodes(stockMove.getStockMoveLineList());

		
		stockMove.setStatusSelect(StockMoveRepository.STATUS_REALIZED);
		stockMove.setRealDate(appBaseService.getTodayDate());
		resetWeights(stockMove);

		try {
			if (stockMove.getIsWithBackorder() || stockMove.getIsWithReturnSurplus()) {
				if (stockMove.getIsWithBackorder() && this.mustBeSplit(stockMove.getStockMoveLineList())) {
					StockMove newStockMove = this.copyAndSplitStockMove(stockMove);
					newStockSeq = newStockMove.getStockMoveSeq();
				}
				if (stockMove.getIsWithReturnSurplus() && this.mustBeSplit(stockMove.getStockMoveLineList())) {
					StockMove newStockMove = this.copyAndSplitStockMoveReverse(stockMove, true);
					if (newStockSeq != null)
						newStockSeq = newStockSeq + " " + newStockMove.getStockMoveSeq();
					else
						newStockSeq = newStockMove.getStockMoveSeq();
				}
			}
		} finally {
			computeWeights(stockMove);
			stockMoveRepo.save(stockMove);
		}
		
		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING) {
			partnerProductQualityRatingService.calculate(stockMove);
		}

		return newStockSeq;
	}


	/**
	 * Check and raise an exception if the provided stock move is involved in an
	 * ongoing inventory.
	 * 
	 * @param stockMove
	 * @throws AxelorException
	 */
	private void checkOngoingInventory(StockMove stockMove) throws AxelorException {
		List<Location> locationList = new ArrayList<>();

		if (stockMove.getFromLocation().getTypeSelect() != LocationRepository.TYPE_VIRTUAL) {
			locationList.add(stockMove.getFromLocation());
		}

		if (stockMove.getToLocation().getTypeSelect() != LocationRepository.TYPE_VIRTUAL) {
			locationList.add(stockMove.getToLocation());
		}

		if (locationList.isEmpty()) {
			return;
		}

		List<Product> productList = stockMove.getStockMoveLineList().stream().map(StockMoveLine::getProduct)
				.collect(Collectors.toList());

		InventoryLineRepository inventoryLineRepo = Beans.get(InventoryLineRepository.class);

		InventoryLine inventoryLine = inventoryLineRepo.all()
				.filter("self.inventory.statusSelect BETWEEN :startStatus AND :endStatus\n"
						+ "AND self.inventory.location IN (:locationList)\n" + "AND self.product IN (:productList)")
				.bind("startStatus", InventoryRepository.STATUS_IN_PROGRESS)
				.bind("endStatus", InventoryRepository.STATUS_COMPLETED)
				.bind("locationList", locationList)
				.bind("productList", productList).fetchOne();

		if (inventoryLine != null) {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.STOCK_MOVE_19),
					inventoryLine.getInventory().getInventorySeq()), IException.INCONSISTENCY);
		}
	}

	private void resetWeights(StockMove stockMove) {
		List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
		
		if (stockMoveLineList == null) {
			return;
		}

		for (StockMoveLine stockMoveLine : stockMoveLineList) {
			stockMoveLine.setNetWeight(null);
			stockMoveLine.setTotalNetWeight(null);
		}
	}

	private void computeWeights(StockMove stockMove) throws AxelorException {
		boolean weightsRequired = checkWeightsRequired(stockMove);
		StockConfig stockConfig = stockMove.getCompany().getStockConfig();
		Unit endUnit = stockConfig != null ? stockConfig.getCustomsWeightUnit() : null;

		if (weightsRequired && endUnit == null) {
			throw new AxelorException(I18n.get(IExceptionMessage.STOCK_MOVE_17), IException.NO_VALUE);
		}
		
		List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();
		
		if (stockMoveLineList == null) {
			return;
		}

		for (StockMoveLine stockMoveLine : stockMoveLineList) {
			Product product = stockMoveLine.getProduct();

			if (!ProductRepository.PRODUCT_TYPE_STORABLE.equals(product.getProductTypeSelect())) {
				continue;
			}

			Unit startUnit = product.getWeightUnit();
			BigDecimal netWeight = product.getNetWeight();

			if (startUnit != null && !netWeight.equals(BigDecimal.ZERO)) {
				UnitConversionService unitConversionService = Beans.get(UnitConversionService.class);
				netWeight = unitConversionService.convert(startUnit, endUnit, netWeight);
				BigDecimal totalNetWeight = netWeight.multiply(stockMoveLine.getRealQty());

				stockMoveLine.setNetWeight(netWeight);
				stockMoveLine.setTotalNetWeight(totalNetWeight);
			} else if (weightsRequired) {
				throw new AxelorException(I18n.get(IExceptionMessage.STOCK_MOVE_18), IException.NO_VALUE);
			}
		}
	}

	private boolean checkWeightsRequired(StockMove stockMove) {
		Address fromAddress = stockMove.getFromAddress();
		if (fromAddress == null && stockMove.getFromLocation() != null) {
			fromAddress = stockMove.getFromLocation().getAddress();
		}

		Address toAddress = stockMove.getToAddress();
		if (toAddress == null && stockMove.getToLocation() != null) {
			toAddress = stockMove.getToLocation().getAddress();
		}

		Country fromCountry = fromAddress != null ? fromAddress.getAddressL7Country() : null;
		Country toCountry = toAddress != null ? toAddress.getAddressL7Country() : null;

		return fromCountry != null && toCountry != null && !fromCountry.equals(toCountry);
	}

	@Override
	public boolean mustBeSplit(List<StockMoveLine> stockMoveLineList)  {

		for(StockMoveLine stockMoveLine : stockMoveLineList)  {

			if(stockMoveLine.getRealQty().compareTo(stockMoveLine.getQty()) != 0)  {

				return true;

			}

		}

		return false;

	}


	@Override
	public StockMove copyAndSplitStockMove(StockMove stockMove) throws AxelorException  {

		StockMove newStockMove = JPA.copy(stockMove, false);

		for(StockMoveLine stockMoveLine : stockMove.getStockMoveLineList())  {

			if(stockMoveLine.getQty().compareTo(stockMoveLine.getRealQty()) > 0)   {
				StockMoveLine newStockMoveLine = JPA.copy(stockMoveLine, false);

				newStockMoveLine.setQty(stockMoveLine.getQty().subtract(stockMoveLine.getRealQty()));
				newStockMoveLine.setRealQty(newStockMoveLine.getQty());

				newStockMove.addStockMoveLineListItem(newStockMoveLine);
			}
		}

		newStockMove.setStatusSelect(StockMoveRepository.STATUS_PLANNED);
		newStockMove.setRealDate(null);
		newStockMove.setStockMoveSeq(this.getSequenceStockMove(newStockMove.getTypeSelect(), newStockMove.getCompany()));
		newStockMove.setName(newStockMove.getStockMoveSeq() + " " + I18n.get(IExceptionMessage.STOCK_MOVE_7) + " " + stockMove.getStockMoveSeq() + " )" );

		return stockMoveRepo.save(newStockMove);

	}


	@Override
	public StockMove copyAndSplitStockMoveReverse(StockMove stockMove, boolean split) throws AxelorException  {

		StockMove newStockMove = new StockMove();

		newStockMove.setCompany(stockMove.getCompany());
		newStockMove.setPartner(stockMove.getPartner());
		newStockMove.setFromLocation(stockMove.getToLocation());
		newStockMove.setToLocation(stockMove.getFromLocation());
		newStockMove.setEstimatedDate(stockMove.getEstimatedDate());
		newStockMove.setFromAddress(stockMove.getFromAddress());
		if(stockMove.getToAddress() != null)
			newStockMove.setFromAddress(stockMove.getToAddress());
		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING)
			newStockMove.setTypeSelect(StockMoveRepository.TYPE_OUTGOING);
		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_OUTGOING)
			newStockMove.setTypeSelect(StockMoveRepository.TYPE_INCOMING);
		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_INTERNAL)
			newStockMove.setTypeSelect(StockMoveRepository.TYPE_INTERNAL);
		newStockMove.setStatusSelect(StockMoveRepository.STATUS_DRAFT);
		newStockMove.setStockMoveSeq(getSequenceStockMove(newStockMove.getTypeSelect(),newStockMove.getCompany()));

		for (StockMoveLine stockMoveLine : stockMove.getStockMoveLineList()) {

			if (!split || stockMoveLine.getRealQty().compareTo(stockMoveLine.getQty()) > 0) {
				StockMoveLine newStockMoveLine = JPA.copy(stockMoveLine, false);

				if (split) {
					newStockMoveLine.setQty(stockMoveLine.getRealQty().subtract(stockMoveLine.getQty()));
					newStockMoveLine.setRealQty(newStockMoveLine.getQty());
				}

				newStockMove.addStockMoveLineListItem(newStockMoveLine);
			}
		}

		newStockMove.setStatusSelect(StockMoveRepository.STATUS_PLANNED);
		newStockMove.setRealDate(null);
		newStockMove.setStockMoveSeq(this.getSequenceStockMove(newStockMove.getTypeSelect(), newStockMove.getCompany()));
		newStockMove.setName(newStockMove.getStockMoveSeq() + " " + I18n.get(IExceptionMessage.STOCK_MOVE_8) + " " + stockMove.getStockMoveSeq() + " )" );

		return stockMoveRepo.save(newStockMove);

	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancel(StockMove stockMove) throws AxelorException  {

		LOG.debug("Annulation du mouvement de stock : {} ", new Object[] { stockMove.getStockMoveSeq() });

		stockMoveLineService.updateLocations(
				stockMove.getFromLocation(),
				stockMove.getToLocation(),
				stockMove.getStatusSelect(),
				StockMoveRepository.STATUS_CANCELED,
				stockMove.getStockMoveLineList(),
				stockMove.getEstimatedDate(),
				false);

		stockMove.setStatusSelect(StockMoveRepository.STATUS_CANCELED);
		stockMove.setRealDate(appBaseService.getTodayDate());

		if(stockMove.getTypeSelect() == StockMoveRepository.TYPE_INCOMING) {
			partnerProductQualityRatingService.undoCalculation(stockMove);
		}
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Boolean splitStockMoveLinesUnit(List<StockMoveLine> stockMoveLines, BigDecimal splitQty){

		Boolean selected = false;

		for(StockMoveLine moveLine : stockMoveLines){
			if(moveLine.isSelected()){
				selected = true;
				StockMoveLine line = stockMoveLineRepo.find(moveLine.getId());
				BigDecimal totalQty = line.getQty();
				LOG.debug("Move Line selected: {}, Qty: {}",new Object[]{line,totalQty});
				while(splitQty.compareTo(totalQty) < 0){
					totalQty = totalQty.subtract(splitQty);
					StockMoveLine newLine = JPA.copy(line, false);
					newLine.setQty(splitQty);
					newLine.setRealQty(splitQty);
					stockMoveLineRepo.save(newLine);
				}
				LOG.debug("Qty remains: {}",totalQty);
				if(totalQty.compareTo(BigDecimal.ZERO) > 0){
					StockMoveLine newLine = JPA.copy(line, false);
					newLine.setQty(totalQty);
					newLine.setRealQty(totalQty);
					stockMoveLineRepo.save(newLine);
					LOG.debug("New line created: {}",newLine);
				}
				stockMoveLineRepo.remove(line);
			}
		}

		return selected;
	}

	@SuppressWarnings("rawtypes")
	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Boolean splitStockMoveLinesSpecial(List<HashMap> stockMoveLines, BigDecimal splitQty){

		Boolean selected = false;
		LOG.debug("SplitQty: {}",new Object[] {splitQty});

		for(HashMap moveLine : stockMoveLines){
			LOG.debug("Move line: {}",new Object[]{moveLine});
			if((Boolean)(moveLine.get("selected"))){
				selected = true;
				StockMoveLine line = stockMoveLineRepo.find(Long.parseLong(moveLine.get("id").toString()));
				BigDecimal totalQty = line.getQty();
				LOG.debug("Move Line selected: {}, Qty: {}",new Object[]{line,totalQty});
				while(splitQty.compareTo(totalQty) < 0){
					totalQty = totalQty.subtract(splitQty);
					StockMoveLine newLine = JPA.copy(line, false);
					newLine.setQty(splitQty);
					newLine.setRealQty(splitQty);
					stockMoveLineRepo.save(newLine);
				}
				LOG.debug("Qty remains: {}",totalQty);
				if(totalQty.compareTo(BigDecimal.ZERO) > 0){
					StockMoveLine newLine = JPA.copy(line, false);
					newLine.setQty(totalQty);
					newLine.setRealQty(totalQty);
					stockMoveLineRepo.save(newLine);
					LOG.debug("New line created: {}",newLine);
				}
				stockMoveLineRepo.remove(line);
			}
		}

		return selected;
	}

	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	@Override
	public Long splitInto2(Long originalStockMoveId, List<StockMoveLine> stockMoveLines){

		//Get original stock move
		StockMove originalStockMove = stockMoveRepo.find(originalStockMoveId);

		//Copy this stock move
		StockMove newStockMove = Beans.get(StockMoveManagementRepository.class).copy(originalStockMove, true);

		List<StockMoveLine> newStockMoveLineToRemove = new ArrayList<StockMoveLine>();
		List<StockMoveLine> originalStockMoveLineToRemove = new ArrayList<StockMoveLine>();
		int lineNumber = 0;
		for(StockMoveLine moveLine : stockMoveLines){
			if (BigDecimal.ZERO.compareTo(moveLine.getQty()) == 0){
				//Remove stock move line from new stock move
				newStockMoveLineToRemove.add(newStockMove.getStockMoveLineList().get(lineNumber));
			}else{
				//Set quantity in new stock move
				newStockMove.getStockMoveLineList().get(lineNumber).setQty(moveLine.getQty());
				newStockMove.getStockMoveLineList().get(lineNumber).setRealQty(moveLine.getQty());

				//Update quantity in original stock move.
				//If the remaining quantity is 0, remove the stock move line
				StockMoveLine currentOriginalStockMoveLine = originalStockMove.getStockMoveLineList().get(lineNumber);
				BigDecimal remainingQty = currentOriginalStockMoveLine.getQty().subtract(moveLine.getQty());
				if (BigDecimal.ZERO.compareTo(remainingQty) == 0){
					//Remove the stock move line
					originalStockMoveLineToRemove.add(currentOriginalStockMoveLine);
				}else{
					currentOriginalStockMoveLine.setQty(remainingQty);
					currentOriginalStockMoveLine.setRealQty(remainingQty);
				}
			}

			lineNumber++;
		}

		for (StockMoveLine stockMoveLineToRemove : newStockMoveLineToRemove) {
			newStockMove.getStockMoveLineList().remove(stockMoveLineToRemove);
		}

		if (!newStockMove.getStockMoveLineList().isEmpty()){
			//Update original stock move
			for (StockMoveLine stockMoveLineToRemove : originalStockMoveLineToRemove) {
				originalStockMove.getStockMoveLineList().remove(stockMoveLineToRemove);
			}
			stockMoveRepo.save(originalStockMove);

			//Save new stock move
			return stockMoveRepo.save(newStockMove).getId();
		}else{
			return null;
		}
	}

	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void copyQtyToRealQty(StockMove stockMove){
		for(StockMoveLine line : stockMove.getStockMoveLineList())
			line.setRealQty(line.getQty());
		stockMoveRepo.save(stockMove);
	}


	@Override
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public StockMove generateReversion(StockMove stockMove) throws AxelorException  {

		LOG.debug("Creation d'un mouvement de stock inverse pour le mouvement de stock: {} ", new Object[] { stockMove.getStockMoveSeq() });

		return copyAndSplitStockMoveReverse(stockMove, false);

	}
	
	@Override
	public List<Map<String,Object>> getStockPerDate(Long locationId, Long productId, LocalDate fromDate, LocalDate toDate) {
		
		List<Map<String,Object>> stock = new ArrayList<Map<String,Object>>();
		
		while(!fromDate.isAfter(toDate)) {
			Double qty = getStock(locationId, productId, fromDate);
			Map<String,Object> dateStock = new HashMap<String, Object>();
			dateStock.put("$date",fromDate);
			dateStock.put("$qty",new BigDecimal(qty));
			stock.add(dateStock);
			fromDate = fromDate.plusDays(1);
		}
		
		return stock;
	}
	
    private Double getStock(Long locationId, Long productId, LocalDate date) {
		
		List<StockMoveLine> inLines = stockMoveLineRepo.all()
			.filter("self.product.id = ?1 AND self.stockMove.toLocation.id = ?2 AND self.stockMove.statusSelect != ?3 AND (self.stockMove.estimatedDate <= ?4 OR self.stockMove.realDate <= ?4)"
			,productId, locationId, StockMoveRepository.STATUS_CANCELED, date).fetch();
		
		List<StockMoveLine> outLines = stockMoveLineRepo.all()
				.filter("self.product.id = ?1 AND self.stockMove.fromLocation.id = ?2 AND self.stockMove.statusSelect != ?3 AND (self.stockMove.estimatedDate <= ?4 OR self.stockMove.realDate <= ?4)"
				,productId, locationId, StockMoveRepository.STATUS_CANCELED, date).fetch();
		
		Double inQty = inLines.stream().mapToDouble(inl->Double.parseDouble(inl.getQty().toString())).sum();
		
		Double outQty = outLines.stream().mapToDouble(out->Double.parseDouble(out.getQty().toString())).sum();
		
		Double qty = inQty-outQty;
		
		return qty;
	}


	@Override
	public List<StockMoveLine> changeConformityStockMove(StockMove stockMove) {
		List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

		if (stockMoveLineList != null) {
			for (StockMoveLine stockMoveLine : stockMoveLineList) {
				stockMoveLine.setConformitySelect(stockMove.getConformitySelect());
			}
		}

		return stockMoveLineList;
	}


	@Override
	public Integer changeConformityStockMoveLine(StockMove stockMove) {
		Integer stockMoveConformitySelect;
		List<StockMoveLine> stockMoveLineList = stockMove.getStockMoveLineList();

		if (stockMoveLineList != null) {
			stockMoveConformitySelect = StockMoveRepository.CONFORMITY_COMPLIANT;

			for (StockMoveLine stockMoveLine : stockMoveLineList) {
				Integer conformitySelect = stockMoveLine.getConformitySelect();

				if (!conformitySelect.equals(StockMoveRepository.CONFORMITY_COMPLIANT)) {
					stockMoveConformitySelect = conformitySelect;
					if (conformitySelect.equals(StockMoveRepository.CONFORMITY_NON_COMPLIANT)) {
						break;
					}
				}
			}
		} else {
			stockMoveConformitySelect = StockMoveRepository.CONFORMITY_NONE;
		}

		stockMove.setConformitySelect(stockMoveConformitySelect);
		return stockMoveConformitySelect;
	}

	@Override
	public void computeAddressStr(StockMove stockMove) {
		AddressService addressService = Beans.get(AddressService.class);
	    stockMove.setFromAddressStr(
	    		addressService.computeAddressStr(stockMove.getFromAddress())
		);
		stockMove.setToAddressStr(
				addressService.computeAddressStr(stockMove.getToAddress())
		);
	}

	@Override
	public String printStockMove(StockMove stockMove,
								 List<Integer> lstSelectedMove,
								 boolean isPicking) throws AxelorException {
		String stockMoveIds = "";

		if(lstSelectedMove != null){
		    StringBuilder bld = new StringBuilder();
			for(Integer it : lstSelectedMove) {
				bld.append(it.toString()).append(",");
			}
			stockMoveIds = bld.toString();
		}

		if(!stockMoveIds.equals("")){
			stockMoveIds = stockMoveIds.substring(0, stockMoveIds.length()-1);
			stockMove = stockMoveRepo.find(Long.valueOf(lstSelectedMove.get(0)));
		}else if(stockMove.getId() != null){
			stockMoveIds = stockMove.getId().toString();
		}

		if(!stockMoveIds.equals("")){

			String language;
			try{
				language = stockMove.getPartner().getLanguageSelect() != null? stockMove.getPartner().getLanguageSelect() : stockMove.getCompany().getPrintingSettings().getLanguageSelect() != null ? stockMove.getCompany().getPrintingSettings().getLanguageSelect() : "en" ;
			}catch (NullPointerException e) {
				language = "en";
			}
			language = language.equals("")? "en": language;

			String title = I18n.get("Stock move");
			if(stockMove.getStockMoveSeq() != null)  {
				title = lstSelectedMove == null ? I18n.get("StockMove") + " " + stockMove.getStockMoveSeq() : I18n.get("StockMove(s)");
			}

			String report = isPicking ? IReport.PICKING_STOCK_MOVE : IReport.STOCK_MOVE;

			LOG.debug("Printing "+title);

			return ReportFactory.createReport(report, title+"-${date}")
					.addParam("StockMoveId", stockMoveIds)
					.addParam("Locale", language)
					.generate()
					.getFileLink();
		}else{
			throw new AxelorException(I18n.get(IExceptionMessage.STOCK_MOVE_10),
					IException.INCONSISTENCY);
		}
	}
}

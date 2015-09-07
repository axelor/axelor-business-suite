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
package com.axelor.apps.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.Journal;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Reconcile;
import com.axelor.apps.account.db.repo.ReconcileRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.apps.base.service.administration.GeneralServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class ReconcileService extends ReconcileRepository {

	private static final Logger LOG = LoggerFactory.getLogger(ReconcileService.class);

	@Inject
	private MoveService moveService;

	@Inject
	private MoveLineService moveLineService;

	@Inject
	private AccountCustomerService accountCustomerService;

	@Inject
	private AccountConfigService accountConfigService;

	private LocalDate today;

	@Inject
	public ReconcileService() {

		this.today = Beans.get(GeneralService.class).getTodayDate();

	}


	/**
	 * Permet de créer une réconciliation en passant les paramètres qu'il faut
	 * @param lineDebit
	 * 			Une ligne d'écriture au débit
	 * @param lineCredit
	 * 			Une ligne d'écriture au crédit
	 * @param amount
	 * 			Le montant à reconciler
	 * @param canBeZeroBalanceOk
	 * 			Peut être soldé?
	 * @return
	 * 			Une reconciliation
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Reconcile createGenericReconcile(MoveLine debitMoveLine, MoveLine creditMoveLine, BigDecimal amount,
			boolean canBeZeroBalanceOk, boolean mustBeZeroBalanceOk, boolean inverse){

		LOG.debug("Create Reconcile (Debit MoveLine : {}, Credit MoveLine : {}, Amount : {}, Can be zero balance ? {}, Must be zero balance ? {}, Inverse debit/credit ? {} ",
				new Object[]{debitMoveLine.getName(), creditMoveLine.getName(), amount, canBeZeroBalanceOk, mustBeZeroBalanceOk, inverse});

		Reconcile reconcile =  new Reconcile(
				amount.setScale(2, RoundingMode.HALF_EVEN),
				debitMoveLine, creditMoveLine,
				STATUS_DRAFT,
				canBeZeroBalanceOk, mustBeZeroBalanceOk);

		if(inverse)  {

			reconcile.setDebitMoveLine(creditMoveLine);
			reconcile.setCreditMoveLine(debitMoveLine);
		}

		return save(reconcile);

	}


	/**
	 * Permet de créer une réconciliation
	 * @param lineDebit
	 * 			Une ligne d'écriture au débit
	 * @param lineCredit
	 * 			Une ligne d'écriture au crédit
	 * @param amount
	 * 			Le montant à reconciler
	 * @return
	 * 			Une reconciliation
	 */
	public Reconcile createReconcile(MoveLine lineDebit, MoveLine lineCredit, BigDecimal amount){
		return createGenericReconcile(lineDebit, lineCredit, amount, false, false, false);
	}


	/**
	 * Permet de créer une réconciliation
	 * @param lineDebit
	 * 			Une ligne d'écriture au débit
	 * @param lineCredit
	 * 			Une ligne d'écriture au crédit
	 * @param amount
	 * 			Le montant à reconciler
	 * @return
	 * 			Une reconciliation
	 */
	public Reconcile createReconcile(MoveLine lineDebit, MoveLine lineCredit, BigDecimal amount, boolean inverse){

		return createGenericReconcile(lineCredit, lineDebit, amount, false, false, inverse);

	}




	/**
	 * Permet de confirmer une  réconciliation
	 * On ne peut réconcilier que des moveLine ayant le même compte
	 * @param reconcile
	 * 			Une reconciliation
	 * @return
	 * 			L'etat de la reconciliation
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public int confirmReconcile(Reconcile reconcile) throws AxelorException  {

		return this.confirmReconcile(reconcile, true);

	}



	/**
	 * Permet de confirmer une  réconciliation
	 * On ne peut réconcilier que des moveLine ayant le même compte
	 * @param reconcile
	 * 			Une reconciliation
	 * @return
	 * 			L'etat de la reconciliation
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public int confirmReconcile(Reconcile reconcile, boolean updateCustomerAccount) throws AxelorException  {

		this.reconcilePreconditions(reconcile);

		MoveLine debitMoveLine = reconcile.getDebitMoveLine();
		MoveLine creditMoveLine = reconcile.getCreditMoveLine();

		//Add the reconciled amount to the reconciled amount in the move line
		creditMoveLine.setAmountPaid(creditMoveLine.getAmountPaid().add(reconcile.getAmount()));
		debitMoveLine.setAmountPaid(debitMoveLine.getAmountPaid().add(reconcile.getAmount()));

		this.updatePartnerAccountingSituation(reconcile, updateCustomerAccount);
		this.updateInvoiceRemainingAmount(reconcile);

		reconcile.setStatusSelect(STATUS_CONFIRMED);

		if(reconcile.getCanBeZeroBalanceOk())  {
			// Alors nous utilisons la règle de gestion consitant à imputer l'écart sur un compte transitoire si le seuil est respecté
			canBeZeroBalance(reconcile);
		}

		save(reconcile);

		return reconcile.getStatusSelect();
	}



	public void reconcilePreconditions(Reconcile reconcile) throws AxelorException  {

		MoveLine debitMoveLine = reconcile.getDebitMoveLine();
		MoveLine creditMoveLine = reconcile.getCreditMoveLine();

		if (debitMoveLine == null || creditMoveLine == null)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.RECONCILE_1),
					GeneralServiceImpl.EXCEPTION), IException.CONFIGURATION_ERROR);
		}

		// Check if move lines accounts are the same (debit and credit)
		if (!creditMoveLine.getAccount().equals(debitMoveLine.getAccount())){
			LOG.debug("Compte ligne de credit : {} , Compte ligne de debit : {}", creditMoveLine.getAccount(), debitMoveLine.getAccount());
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.RECONCILE_2)+" " +
					I18n.get(IExceptionMessage.RECONCILE_3),
					GeneralServiceImpl.EXCEPTION, debitMoveLine.getName(), debitMoveLine.getAccount().getLabel(),
					creditMoveLine.getName(), creditMoveLine.getAccount().getLabel()), IException.CONFIGURATION_ERROR);
		}

		// Check if the amount to reconcile is != zero
		if (reconcile.getAmount() == null || reconcile.getAmount().compareTo(BigDecimal.ZERO) == 0)  {
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.RECONCILE_4),
					GeneralServiceImpl.EXCEPTION, debitMoveLine.getName(), debitMoveLine.getAccount().getLabel(),
					creditMoveLine.getName(), creditMoveLine.getAccount().getLabel()), IException.INCONSISTENCY);

		}

		if ((reconcile.getAmount().compareTo(creditMoveLine.getCredit().subtract(creditMoveLine.getAmountPaid())) > 0
				|| (reconcile.getAmount().compareTo(debitMoveLine.getDebit().subtract(debitMoveLine.getAmountPaid())) > 0))){
			throw new AxelorException(
					String.format(I18n.get(IExceptionMessage.RECONCILE_5)+" " +
							I18n.get(IExceptionMessage.RECONCILE_3),
							GeneralServiceImpl.EXCEPTION, debitMoveLine.getName(), debitMoveLine.getAccount().getLabel(),
					creditMoveLine.getName(), creditMoveLine.getAccount().getLabel()), IException.INCONSISTENCY);

		}

	}




	public void updatePartnerAccountingSituation(Reconcile reconcile, boolean updateCustomerAccount)  {
		Company company = null;
		List<Partner> partnerList = new ArrayList<Partner>();

		MoveLine debitMoveLine = reconcile.getDebitMoveLine();
		MoveLine creditMoveLine = reconcile.getCreditMoveLine();
		Partner debitPartner = debitMoveLine.getPartner();
		Partner creditPartner = creditMoveLine.getPartner();

		if(debitPartner != null)  {
			Move move = debitMoveLine.getMove();
			if(move != null && move.getCompany() != null)  {
				partnerList.add(debitPartner);
				company = move.getCompany();
			}
		}
		if(creditPartner != null)  {
			Move move = creditMoveLine.getMove();
			if(move != null && move.getCompany() != null)  {
				partnerList.add(creditPartner);
				company = move.getCompany();
			}
		}

		if(partnerList != null && !partnerList.isEmpty() && company != null)  {
			if(updateCustomerAccount)  {
				accountCustomerService.updatePartnerAccountingSituation(partnerList, company, true, true, false);
			}
			else  {
				accountCustomerService.flagPartners(partnerList, company);
			}
		}
	}


	public void updateInvoiceRemainingAmount(Reconcile reconcile) throws AxelorException  {

		Invoice debitInvoice = reconcile.getDebitMoveLine().getMove().getInvoice();
		Invoice creditInvoice = reconcile.getCreditMoveLine().getMove().getInvoice();

		// Update amount remaining on invoice or refund
		if(debitInvoice != null)  {
			debitInvoice.setCompanyInTaxTotalRemaining(  moveService.getInTaxTotalRemaining(debitInvoice)  );
		}
		if(creditInvoice != null)  {
			creditInvoice.setCompanyInTaxTotalRemaining(  moveService.getInTaxTotalRemaining(creditInvoice)  );
		}

	}


	/**
	 * Méthode permettant de lettrer une écriture au débit avec une écriture au crédit
	 * @param debitMoveLine
	 * @param creditMoveLine
	 * @throws AxelorException
	 */
	public void reconcile(MoveLine debitMoveLine, MoveLine creditMoveLine, boolean updateCustomerAccount) throws AxelorException  {

		BigDecimal amount = debitMoveLine.getAmountRemaining().min(creditMoveLine.getAmountRemaining());
		Reconcile reconcile = this.createReconcile(debitMoveLine, creditMoveLine, amount);
		this.confirmReconcile(reconcile, updateCustomerAccount);

	}



	/**
	 * Permet de déréconcilier
	 * @param reconcile
	 * 			Une reconciliation
	 * @return
	 * 			L'etat de la réconciliation
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public int unreconcile(Reconcile reconcile) throws AxelorException  {

		MoveLine debitMoveLine = reconcile.getDebitMoveLine();
		MoveLine creditMoveLine = reconcile.getCreditMoveLine();

		// Change the state
		reconcile.setStatusSelect(STATUS_CANCELED);
		//Add the reconciled amount to the reconciled amount in the move line
		creditMoveLine.setAmountPaid(creditMoveLine.getAmountPaid().subtract(reconcile.getAmount()));
		debitMoveLine.setAmountPaid(debitMoveLine.getAmountPaid().subtract(reconcile.getAmount()));

		// Update amount remaining on invoice or refund
		this.updatePartnerAccountingSituation(reconcile, true);
		this.updateInvoiceRemainingAmount(reconcile);

		save(reconcile);

		return reconcile.getStatusSelect();

	}



	/**
	 * Procédure permettant de gérer les écarts de règlement, check sur la case à cocher 'Peut être soldé'
	 *  Alors nous utilisons la règle de gestion consitant à imputer l'écart sur un compte transitoire si le seuil est respecté
	 * @param reconcile
	 * 			Une reconciliation
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void canBeZeroBalance(Reconcile reconcile) throws AxelorException  {

		MoveLine debitMoveLine = reconcile.getDebitMoveLine();

		BigDecimal debitAmountRemaining = debitMoveLine.getAmountRemaining();
		LOG.debug("Montant à payer / à lettrer au débit : {}", debitAmountRemaining);
		if(debitAmountRemaining.compareTo(BigDecimal.ZERO) > 0)  {
			Company company = reconcile.getDebitMoveLine().getMove().getCompany();

			AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

			if(debitAmountRemaining.plus().compareTo(accountConfig.getThresholdDistanceFromRegulation()) < 0 || reconcile.getMustBeZeroBalanceOk())  {

				LOG.debug("Seuil respecté");

				Partner partner = debitMoveLine.getPartner();
				Account account = debitMoveLine.getAccount();

				Journal miscOperationJournal = accountConfigService.getMiscOperationJournal(accountConfig);

				Move newMove = moveService.createMove(miscOperationJournal, company, null, partner, null);

				// Création de la ligne au crédit
				MoveLine newCreditMoveLine = moveLineService.createMoveLine(newMove, partner, account, debitAmountRemaining, false, today, 1, null);

				// Création de la ligne au debit
				MoveLine newDebitMoveLine = moveLineService.createMoveLine(
						newMove, partner, accountConfigService.getCashPositionVariationAccount(accountConfig), debitAmountRemaining, true, today, 2, null);

				newMove.getMoveLineList().add(newCreditMoveLine);
				newMove.getMoveLineList().add(newDebitMoveLine);
				moveService.validateMove(newMove);
				moveService.save(newMove);

				//Création de la réconciliation
				Reconcile newReconcile = this.createReconcile(debitMoveLine, newCreditMoveLine, debitAmountRemaining);
				this.confirmReconcile(newReconcile);
				save(newReconcile);
			}
		}

		reconcile.setCanBeZeroBalanceOk(false);
		LOG.debug("Fin de la gestion des écarts de règlement");
	}


	/**
	 * Solder le trop-perçu si il respect les règles de seuil
	 * @param creditMoveLine
	 * @param company
	 * @throws AxelorException
	 */
	public void balanceCredit(MoveLine creditMoveLine, Company company, boolean updateCustomerAccount) throws AxelorException  {
		if(creditMoveLine != null)  {
			BigDecimal creditAmountRemaining = creditMoveLine.getAmountRemaining();
			LOG.debug("Montant à payer / à lettrer au crédit : {}", creditAmountRemaining);

			if(creditAmountRemaining.compareTo(BigDecimal.ZERO) > 0)  {
				AccountConfig accountConfig = accountConfigService.getAccountConfig(company);

				if(creditAmountRemaining.plus().compareTo(accountConfig.getThresholdDistanceFromRegulation()) < 0)  {

					LOG.debug("Seuil respecté");

					Partner partner = creditMoveLine.getPartner();
					Account account = creditMoveLine.getAccount();

					Journal miscOperationJournal = accountConfigService.getMiscOperationJournal(accountConfig);

					Move newMove = moveService.createMove(miscOperationJournal, company, null, partner, null);


					// Création de la ligne au crédit
					MoveLine newCreditMoveLine = moveLineService.createMoveLine(
							newMove, partner, accountConfigService.getCashPositionVariationAccount(accountConfig), creditAmountRemaining, false, today, 2, null);

					// Création de la ligne au débit
					MoveLine newDebitMoveLine = moveLineService.createMoveLine(newMove, partner, account, creditAmountRemaining, true, today, 1, null);

					newMove.getMoveLineList().add(newCreditMoveLine);
					newMove.getMoveLineList().add(newDebitMoveLine);
					moveService.validateMove(newMove, updateCustomerAccount);
					moveService.save(newMove);

					//Création de la réconciliation
					Reconcile newReconcile = this.createReconcile(newDebitMoveLine, creditMoveLine, creditAmountRemaining);
					this.confirmReconcile(newReconcile, updateCustomerAccount);
					save(newReconcile);
				}
			}
		}
	}

}

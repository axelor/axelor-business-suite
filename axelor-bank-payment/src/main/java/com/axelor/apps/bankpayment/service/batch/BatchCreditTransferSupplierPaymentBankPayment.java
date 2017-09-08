package com.axelor.apps.bankpayment.service.batch;

import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.batch.BatchCreditTransferSupplierPayment;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentCreateService;
import com.axelor.apps.bankpayment.db.BankOrder;
import com.axelor.apps.bankpayment.service.bankorder.BankOrderMergeService;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class BatchCreditTransferSupplierPaymentBankPayment extends BatchCreditTransferSupplierPayment {

	protected final BankOrderMergeService bankOrderMergeService;

	@Inject
	public BatchCreditTransferSupplierPaymentBankPayment(GeneralService generalService, InvoiceRepository invoiceRepo,
			InvoicePaymentCreateService invoicePaymentCreateService, InvoicePaymentRepository invoicePaymentRepository,
			BankOrderMergeService bankOrderMergeService) {
		super(generalService, invoiceRepo, invoicePaymentCreateService, invoicePaymentRepository);
		this.bankOrderMergeService = bankOrderMergeService;
	}

	@Override
	protected void process() {
		List<InvoicePayment> doneList = processInvoices(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE);

		try {
			mergeBankOrders(doneList);
		} catch (Exception ex) {
			TraceBackService.trace(ex);
			ex.printStackTrace();
			log.error("Credit transfer batch for invoices: mergeBankOrders");
		}
	}

	/**
	 * Merge bank orders.
	 * 
	 * @param doneList
	 * @throws AxelorException
	 */
	@Transactional(rollbackOn = { AxelorException.class, Exception.class })
	protected void mergeBankOrders(List<InvoicePayment> doneList) throws AxelorException {
		List<InvoicePayment> invoicePaymentList = new ArrayList<>();
		List<BankOrder> bankOrderList = new ArrayList<>();

		for (InvoicePayment invoicePayment : doneList) {
			BankOrder bankOrder = invoicePayment.getBankOrder();
			if (bankOrder != null) {
				invoicePaymentList.add(invoicePayment);
				bankOrderList.add(bankOrder);
			}
		}

		if (bankOrderList.size() > 1) {
			BankOrder mergedBankOrder = bankOrderMergeService.mergeBankOrderList(bankOrderList);
			for (InvoicePayment invoicePayment : invoicePaymentList) {
				invoicePayment.setBankOrder(mergedBankOrder);
			}
		}
	}

}

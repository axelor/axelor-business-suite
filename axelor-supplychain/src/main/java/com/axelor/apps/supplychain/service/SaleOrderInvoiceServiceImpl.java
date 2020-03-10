/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
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

import static com.axelor.apps.tool.StringTool.getIdListString;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.FiscalPositionAccountService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.app.AppSaleService;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderWorkflowServiceImpl;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.db.Timetable;
import com.axelor.apps.supplychain.db.repo.TimetableRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleOrderInvoiceServiceImpl implements SaleOrderInvoiceService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected AppSupplychainService appSupplychainService;

  protected SaleOrderRepository saleOrderRepo;

  protected InvoiceRepository invoiceRepo;

  protected InvoiceService invoiceService;

  protected SaleOrderLineService saleOrderLineService;

  protected StockMoveRepository stockMoveRepository;

  protected SaleOrderWorkflowServiceImpl saleOrderWorkflowServiceImpl;

  @Inject
  public SaleOrderInvoiceServiceImpl(
      AppSupplychainService appSupplychainService,
      SaleOrderRepository saleOrderRepo,
      InvoiceRepository invoiceRepo,
      InvoiceService invoiceService,
      SaleOrderLineService saleOrderLineService,
      StockMoveRepository stockMoveRepository,
      SaleOrderWorkflowServiceImpl saleOrderWorkflowServiceImpl) {

    this.appSupplychainService = appSupplychainService;
    this.saleOrderRepo = saleOrderRepo;
    this.invoiceRepo = invoiceRepo;
    this.invoiceService = invoiceService;
    this.stockMoveRepository = stockMoveRepository;
    this.saleOrderLineService = saleOrderLineService;
    this.saleOrderWorkflowServiceImpl = saleOrderWorkflowServiceImpl;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(
      SaleOrder saleOrder,
      int operationSelect,
      BigDecimal amount,
      boolean isPercent,
      Map<Long, BigDecimal> qtyToInvoiceMap,
      List<Long> timetableIdList)
      throws AxelorException {

    Invoice invoice;
    switch (operationSelect) {
      case SaleOrderRepository.INVOICE_ALL:
        invoice = generateInvoice(saleOrder);
        break;

      case SaleOrderRepository.INVOICE_LINES:
        invoice = generateInvoiceFromLines(saleOrder, qtyToInvoiceMap, isPercent);
        break;

      case SaleOrderRepository.INVOICE_ADVANCE_PAYMENT:
        invoice = generateAdvancePayment(saleOrder, amount, isPercent);
        break;

      case SaleOrderRepository.INVOICE_TIMETABLES:
        BigDecimal percentSum = BigDecimal.ZERO;
        TimetableRepository timetableRepo = Beans.get(TimetableRepository.class);
        List<Timetable> timetableList = new ArrayList<>();
        if (timetableIdList == null || timetableIdList.isEmpty()) {
          throw new AxelorException(
              saleOrder,
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.SO_INVOICE_NO_TIMETABLES_SELECTED));
        }
        for (Long timetableId : timetableIdList) {
          Timetable timetable = timetableRepo.find(timetableId);
          timetableList.add(timetable);
          percentSum = percentSum.add(timetable.getPercentage());
        }
        invoice =
            generateInvoiceFromLines(
                saleOrder, this.generateQtyToInvoiceMap(saleOrder, percentSum), true);

        if (!timetableList.isEmpty()) {
          for (Timetable timetable : timetableList) {
            timetable.setInvoice(invoice);
            timetableRepo.save(timetable);
          }
        }

        break;

      default:
        return null;
    }
    invoice.setSaleOrder(saleOrder);

    if (!Strings.isNullOrEmpty(saleOrder.getInvoiceComments())) {
      invoice.setNote(saleOrder.getInvoiceComments());
    }

    if (ObjectUtils.isEmpty(invoice.getProformaComments())
        && !Strings.isNullOrEmpty(saleOrder.getProformaComments())) {
      invoice.setProformaComments(saleOrder.getProformaComments());
    }

    // fill default advance payment invoice
    if (invoice.getOperationSubTypeSelect() != InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE) {
      invoice.setAdvancePaymentInvoiceSet(invoiceService.getDefaultAdvancePaymentInvoice(invoice));
    }

    invoice.setPartnerTaxNbr(saleOrder.getClientPartner().getTaxNbr());

    invoice = invoiceRepo.save(invoice);

    return invoice;
  }

  protected Map<Long, BigDecimal> generateQtyToInvoiceMap(
      SaleOrder saleOrder, BigDecimal percentage) {
    Map<Long, BigDecimal> map = new HashMap<>();

    for (SaleOrderLine soLine : saleOrder.getSaleOrderLineList()) {
      this.addSubLineQty(map, percentage, soLine.getId());
      map.put(soLine.getId(), percentage);
    }
    return map;
  }

  @Override
  public void addSubLineQty(
      Map<Long, BigDecimal> qtyToInvoiceMap, BigDecimal qtyToInvoiceItem, Long soLineId) {

    SaleOrderLine saleOrderLine = Beans.get(SaleOrderLineRepository.class).find(soLineId);

    if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
      for (SaleOrderLine subLine : saleOrderLine.getSubLineList()) {
        BigDecimal qty = BigDecimal.ZERO;
        if (saleOrderLine.getQty().compareTo(BigDecimal.ZERO) != 0) {
          qty =
              qtyToInvoiceItem
                  .multiply(subLine.getQty())
                  .divide(saleOrderLine.getQty(), 2, RoundingMode.HALF_EVEN);
        }
        qty = qty.setScale(2, RoundingMode.HALF_EVEN);
        qtyToInvoiceMap.put(subLine.getId(), qty);
      }
    }
  }

  @Override
  public BigDecimal computeAmountToInvoicePercent(
      SaleOrder saleOrder, BigDecimal amount, boolean isPercent) throws AxelorException {
    BigDecimal total = Beans.get(SaleOrderComputeService.class).getTotalSaleOrderPrice(saleOrder);
    if (total.compareTo(BigDecimal.ZERO) == 0) {
      if (amount.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO;
      } else {
        throw new AxelorException(
            saleOrder,
            TraceBackRepository.CATEGORY_INCONSISTENCY,
            I18n.get(IExceptionMessage.SO_INVOICE_AMOUNT_MAX));
      }
    }
    if (!isPercent) {
      amount = amount.multiply(new BigDecimal("100")).divide(total, 4, RoundingMode.HALF_EVEN);
    }
    if (amount.compareTo(new BigDecimal("100")) > 0) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_AMOUNT_MAX));
    }

    return amount;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateAdvancePayment(
      SaleOrder saleOrder, BigDecimal amountToInvoice, boolean isPercent) throws AxelorException {
    List<SaleOrderLineTax> taxLineList = saleOrder.getSaleOrderLineTaxList();
    AccountConfigService accountConfigService = Beans.get(AccountConfigService.class);

    BigDecimal percentToInvoice =
        computeAmountToInvoicePercent(saleOrder, amountToInvoice, isPercent);
    Product invoicingProduct =
        accountConfigService.getAccountConfig(saleOrder.getCompany()).getAdvancePaymentProduct();
    Account advancePaymentAccount =
        accountConfigService.getAccountConfig(saleOrder.getCompany()).getAdvancePaymentAccount();
    if (invoicingProduct == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_INVOICE_MISSING_ADVANCE_PAYMENT_PRODUCT));
    }
    if (advancePaymentAccount == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_INVOICE_MISSING_ADVANCE_PAYMENT_ACCOUNT),
          saleOrder.getCompany().getName());
    }

    Invoice invoice =
        createInvoiceAndLines(
            saleOrder,
            taxLineList,
            invoicingProduct,
            percentToInvoice,
            InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE,
            advancePaymentAccount);

    // no need for link to sale order lines for an advance payment
    if (invoice.getInvoiceLineList() != null) {
      invoice.getInvoiceLineList().forEach(invoiceLine -> invoiceLine.setSaleOrderLine(null));
    }

    return invoiceRepo.save(invoice);
  }

  public Invoice createInvoiceAndLines(
      SaleOrder saleOrder,
      List<SaleOrderLineTax> taxLineList,
      Product invoicingProduct,
      BigDecimal percentToInvoice,
      int operationSubTypeSelect,
      Account partnerAccount)
      throws AxelorException {
    InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(saleOrder);

    Invoice invoice = invoiceGenerator.generate();

    List<InvoiceLine> invoiceLinesList =
        (taxLineList != null && !taxLineList.isEmpty())
            ? this.createInvoiceLinesFromTax(
                invoice, taxLineList, invoicingProduct, percentToInvoice)
            : this.createInvoiceLinesFromSO(invoice, saleOrder, invoicingProduct, percentToInvoice);

    invoiceGenerator.populate(invoice, invoiceLinesList);

    invoice.setAddressStr(saleOrder.getMainInvoicingAddressStr());

    invoice.setOperationSubTypeSelect(operationSubTypeSelect);

    if (partnerAccount != null) {
      Partner partner = invoice.getPartner();
      if (partner != null) {
        partnerAccount =
            Beans.get(FiscalPositionAccountService.class)
                .getAccount(partner.getFiscalPosition(), partnerAccount);
      }
      invoice.setPartnerAccount(partnerAccount);
    }

    return invoice;
  }

  @Override
  public List<InvoiceLine> createInvoiceLinesFromTax(
      Invoice invoice,
      List<SaleOrderLineTax> taxLineList,
      Product invoicingProduct,
      BigDecimal percentToInvoice)
      throws AxelorException {

    List<InvoiceLine> createdInvoiceLineList = new ArrayList<>();
    if (taxLineList != null) {
      for (SaleOrderLineTax saleOrderLineTax : taxLineList) {
        BigDecimal lineAmountToInvoice =
            percentToInvoice
                .multiply(saleOrderLineTax.getExTaxBase())
                .divide(new BigDecimal("100"), 4, BigDecimal.ROUND_HALF_EVEN);
        TaxLine taxLine = saleOrderLineTax.getTaxLine();
        BigDecimal lineAmountToInvoiceInclTax =
            (taxLine != null)
                ? lineAmountToInvoice.add(lineAmountToInvoice.multiply(taxLine.getValue()))
                : lineAmountToInvoice;

        InvoiceLineGenerator invoiceLineGenerator =
            new InvoiceLineGenerator(
                invoice,
                invoicingProduct,
                invoicingProduct.getName(),
                lineAmountToInvoice,
                lineAmountToInvoiceInclTax,
                invoice.getInAti() ? lineAmountToInvoiceInclTax : lineAmountToInvoice,
                invoicingProduct.getDescription(),
                BigDecimal.ONE,
                invoicingProduct.getUnit(),
                taxLine,
                InvoiceLineGenerator.DEFAULT_SEQUENCE,
                BigDecimal.ZERO,
                PriceListLineRepository.AMOUNT_TYPE_NONE,
                lineAmountToInvoice,
                null,
                false,
                false,
                0) {
              @Override
              public List<InvoiceLine> creates() throws AxelorException {

                InvoiceLine invoiceLine = this.createInvoiceLine();

                List<InvoiceLine> invoiceLines = new ArrayList<>();
                invoiceLines.add(invoiceLine);

                return invoiceLines;
              }
            };

        List<InvoiceLine> invoiceOneLineList = invoiceLineGenerator.creates();
        // link to the created invoice line the first line of the sale order.
        for (InvoiceLine invoiceLine : invoiceOneLineList) {
          SaleOrderLine saleOrderLine =
              saleOrderLineTax.getSaleOrder().getSaleOrderLineList().get(0);
          invoiceLine.setSaleOrderLine(saleOrderLine);
        }
        createdInvoiceLineList.addAll(invoiceOneLineList);
      }
    }
    return createdInvoiceLineList;
  }

  protected List<InvoiceLine> createInvoiceLinesFromSO(
      Invoice invoice, SaleOrder saleOrder, Product invoicingProduct, BigDecimal percentToInvoice)
      throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    BigDecimal lineAmountToInvoice =
        percentToInvoice
            .multiply(saleOrder.getInTaxTotal())
            .divide(new BigDecimal("100"), 4, BigDecimal.ROUND_HALF_EVEN);

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGenerator(
            invoice,
            invoicingProduct,
            invoicingProduct.getName(),
            lineAmountToInvoice,
            lineAmountToInvoice,
            lineAmountToInvoice,
            invoicingProduct.getDescription(),
            BigDecimal.ONE,
            invoicingProduct.getUnit(),
            null,
            InvoiceLineGenerator.DEFAULT_SEQUENCE,
            BigDecimal.ZERO,
            PriceListLineRepository.AMOUNT_TYPE_NONE,
            lineAmountToInvoice,
            null,
            false,
            false,
            0) {
          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    List<InvoiceLine> invoiceOneLineList = invoiceLineGenerator.creates();
    invoiceLineList.addAll(invoiceOneLineList);

    return invoiceLineList;
  }

  @Override
  public Invoice generateInvoiceFromLines(
      SaleOrder saleOrder, Map<Long, BigDecimal> qtyToInvoiceMap, boolean isPercent)
      throws AxelorException {

    if (qtyToInvoiceMap.isEmpty()) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_NO_LINES_SELECTED));
    }

    for (SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
      Long SOrderId = saleOrderLine.getId();
      if (qtyToInvoiceMap.containsKey(SOrderId)) {
        if (isPercent) {
          BigDecimal percent = qtyToInvoiceMap.get(SOrderId);
          BigDecimal realQty =
              saleOrderLine
                  .getQty()
                  .multiply(percent)
                  .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
          qtyToInvoiceMap.put(SOrderId, realQty);
        }
        if (qtyToInvoiceMap.get(SOrderId).compareTo(saleOrderLine.getQty()) > 0) {
          throw new AxelorException(
              saleOrder,
              TraceBackRepository.CATEGORY_INCONSISTENCY,
              I18n.get(IExceptionMessage.SO_INVOICE_QTY_MAX));
        }
      }
    }
    return this.generateInvoice(saleOrder, saleOrder.getSaleOrderLineList(), qtyToInvoiceMap);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(SaleOrder saleOrder) throws AxelorException {

    Invoice invoice = this.createInvoice(saleOrder);

    invoiceRepo.save(invoice);

    saleOrderRepo.save(fillSaleOrder(saleOrder, invoice));

    return invoice;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(SaleOrder saleOrder, List<SaleOrderLine> saleOrderLinesSelected)
      throws AxelorException {

    Invoice invoice = this.createInvoice(saleOrder, saleOrderLinesSelected);

    invoiceRepo.save(invoice);

    saleOrderRepo.save(fillSaleOrder(saleOrder, invoice));

    return invoice;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice generateInvoice(
      SaleOrder saleOrder,
      List<SaleOrderLine> saleOrderLinesSelected,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    Invoice invoice = this.createInvoice(saleOrder, saleOrderLinesSelected, qtyToInvoiceMap);
    invoiceRepo.save(invoice);

    saleOrderRepo.save(fillSaleOrder(saleOrder, invoice));

    return invoice;
  }

  public BigDecimal computeInTaxTotalInvoiced(Invoice invoice) {

    BigDecimal total = BigDecimal.ZERO;

    if (invoice.getStatusSelect() == InvoiceRepository.STATUS_VENTILATED) {
      if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_SALE) {
        total = total.add(invoice.getInTaxTotal());
      }
      if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND) {
        total = total.subtract(invoice.getInTaxTotal());
      }
    }

    if (invoice.getRefundInvoiceList() != null) {
      for (Invoice refund : invoice.getRefundInvoiceList()) {
        total = total.add(this.computeInTaxTotalInvoiced(refund));
      }
    }

    return total;
  }

  @Override
  public SaleOrder fillSaleOrder(SaleOrder saleOrder, Invoice invoice) {

    saleOrder.setOrderDate(appSupplychainService.getTodayDate());

    return saleOrder;
  }

  @Override
  public Invoice createInvoice(SaleOrder saleOrder) throws AxelorException {

    return createInvoice(saleOrder, saleOrder.getSaleOrderLineList());
  }

  @Override
  public Invoice createInvoice(SaleOrder saleOrder, List<SaleOrderLine> saleOrderLineList)
      throws AxelorException {
    Map<Long, BigDecimal> qtyToInvoiceMap = new HashMap<>();
    for (SaleOrderLine saleOrderLine : saleOrderLineList) {
      qtyToInvoiceMap.put(saleOrderLine.getId(), saleOrderLine.getQty());
    }
    return createInvoice(saleOrder, saleOrderLineList, qtyToInvoiceMap);
  }

  @Override
  public Invoice createInvoice(
      SaleOrder saleOrder,
      List<SaleOrderLine> saleOrderLineList,
      Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(saleOrder);

    Invoice invoice = invoiceGenerator.generate();

    invoiceGenerator.populate(
        invoice, this.createInvoiceLines(invoice, saleOrderLineList, qtyToInvoiceMap));
    invoice.setAddressStr(saleOrder.getMainInvoicingAddressStr());

    return invoice;
  }

  @Override
  public InvoiceGenerator createInvoiceGenerator(SaleOrder saleOrder) throws AxelorException {
    return createInvoiceGenerator(saleOrder, false);
  }

  @Override
  public InvoiceGenerator createInvoiceGenerator(SaleOrder saleOrder, boolean isRefund)
      throws AxelorException {
    if (saleOrder.getCurrency() == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_INVOICE_6),
          saleOrder.getSaleOrderSeq());
    }

    return new InvoiceGeneratorSupplyChain(saleOrder, isRefund) {
      @Override
      public Invoice generate() throws AxelorException {
        Invoice invoice = super.createInvoiceHeader();
        invoice.setHeadOfficeAddress(saleOrder.getClientPartner().getHeadOfficeAddress());
        return invoice;
      }
    };
  }

  // TODO ajouter tri sur les séquences
  @Override
  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<SaleOrderLine> saleOrderLineList, Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    Map<SaleOrderLine, InvoiceLine> packLineMap = Maps.newHashMap();
    boolean setPack = Beans.get(AppSaleService.class).getAppSale().getProductPackMgt();
    for (SaleOrderLine saleOrderLine : saleOrderLineList) {

      if (qtyToInvoiceMap.containsKey(saleOrderLine.getId())) {
        List<InvoiceLine> invoiceLines =
            this.createInvoiceLine(
                invoice, saleOrderLine, qtyToInvoiceMap.get(saleOrderLine.getId()));
        invoiceLineList.addAll(invoiceLines);
        if (setPack
            && !invoiceLineList.isEmpty()
            && (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK
                || saleOrderLine.getIsSubLine())) {
          packLineMap.put(saleOrderLine, invoiceLines.get(0));
        }
        saleOrderLine.setInvoiced(true);
      }
    }

    if (setPack) {
      for (SaleOrderLine saleOrderLine : packLineMap.keySet()) {
        if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_PACK) {
          InvoiceLine invoiceLine = packLineMap.get(saleOrderLine);
          if (invoiceLine == null) {
            continue;
          }
          BigDecimal totalPack = BigDecimal.ZERO;
          for (SaleOrderLine subLine : saleOrderLine.getSubLineList()) {
            InvoiceLine subInvoiceLine = packLineMap.get(subLine);
            if (subInvoiceLine != null) {
              totalPack = totalPack.add(subInvoiceLine.getExTaxTotal());
              subInvoiceLine.setParentLine(invoiceLine);
            }
          }
          invoiceLine.setTotalPack(totalPack);
        }
      }
    }

    return invoiceLineList;
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice, SaleOrderLine saleOrderLine, BigDecimal qtyToInvoice)
      throws AxelorException {

    Product product = saleOrderLine.getProduct();

    Integer packPriceSelect = saleOrderLine.getPackPriceSelect();
    if (saleOrderLine.getIsSubLine() && saleOrderLine.getParentLine() != null) {
      packPriceSelect = saleOrderLine.getParentLine().getPackPriceSelect();
    }

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            saleOrderLine.getProductName(),
            saleOrderLine.getDescription(),
            qtyToInvoice,
            saleOrderLine.getUnit(),
            saleOrderLine.getSequence(),
            false,
            saleOrderLine,
            null,
            null,
            saleOrderLine.getIsSubLine(),
            packPriceSelect) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  @Override
  public void update(SaleOrder saleOrder, Long currentInvoiceId, boolean excludeCurrentInvoice)
      throws AxelorException {

    update(saleOrder, currentInvoiceId, excludeCurrentInvoice, false);
  }

  protected void update(
      SaleOrder saleOrder,
      Long currentInvoiceId,
      boolean excludeCurrentInvoice,
      boolean checkInvoicedAmount)
      throws AxelorException {

    BigDecimal amountInvoiced =
        this.getInvoicedAmount(saleOrder, currentInvoiceId, excludeCurrentInvoice);
    if (checkInvoicedAmount && amountInvoiced.compareTo(saleOrder.getExTaxTotal()) > 0) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.TYPE_FUNCTIONNAL,
          I18n.get(IExceptionMessage.SO_INVOICE_TOO_MUCH_INVOICED),
          saleOrder.getSaleOrderSeq());
    }
    saleOrder.setAmountInvoiced(amountInvoiced);

    if (appSupplychainService.getAppSupplychain().getCompleteSaleOrderOnInvoicing()
        && amountInvoiced.compareTo(saleOrder.getExTaxTotal()) == 0) {
      saleOrderWorkflowServiceImpl.completeSaleOrder(saleOrder);
    }
  }

  @Override
  public BigDecimal getInvoicedAmount(SaleOrder saleOrder) {
    return this.getInvoicedAmount(saleOrder, null, true);
  }

  /**
   * Return the remaining amount to invoice for the saleOrder in parameter
   *
   * @param saleOrder
   * @param currentInvoiceId In the case of invoice ventilation or cancellation, the invoice status
   *     isn't modify in database but it will be integrated in calculation For ventilation, the
   *     invoice should be integrated in calculation For cancellation, the invoice shouldn't be
   *     integrated in calculation
   * @param includeInvoice To know if the invoice should be or not integrated in calculation
   */
  @Override
  public BigDecimal getInvoicedAmount(
      SaleOrder saleOrder, Long currentInvoiceId, boolean excludeCurrentInvoice) {

    BigDecimal invoicedAmount = BigDecimal.ZERO;

    BigDecimal saleAmount =
        this.getAmountVentilated(
            saleOrder,
            currentInvoiceId,
            excludeCurrentInvoice,
            InvoiceRepository.OPERATION_TYPE_CLIENT_SALE);
    BigDecimal refundAmount =
        this.getAmountVentilated(
            saleOrder,
            currentInvoiceId,
            excludeCurrentInvoice,
            InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND);

    if (saleAmount != null) {
      invoicedAmount = invoicedAmount.add(saleAmount);
    }
    if (refundAmount != null) {
      invoicedAmount = invoicedAmount.subtract(refundAmount);
    }

    if (!saleOrder.getCurrency().equals(saleOrder.getCompany().getCurrency())
        && saleOrder.getCompanyExTaxTotal().compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal rate =
          invoicedAmount.divide(saleOrder.getCompanyExTaxTotal(), 4, RoundingMode.HALF_UP);
      invoicedAmount = saleOrder.getExTaxTotal().multiply(rate);
    }

    log.debug(
        "Compute the invoiced amount ({}) of the sale order : {}",
        invoicedAmount,
        saleOrder.getSaleOrderSeq());

    return invoicedAmount;
  }

  @Override
  public List<Invoice> getInvoices(SaleOrder saleOrder) {
    return invoiceRepo
        .all()
        .filter(
            "self.saleOrder.id = ? OR (self.saleOrder.id IS NULL AND EXISTS(SELECT 1 FROM self.invoiceLineList inli WHERE inli.saleOrderLine.id IN (?)))",
            saleOrder.getId(),
            saleOrder
                .getSaleOrderLineList()
                .stream()
                .map(SaleOrderLine::getId)
                .collect(Collectors.toList()))
        .fetch();
  }

  protected BigDecimal getAmountVentilated(
      SaleOrder saleOrder,
      Long currentInvoiceId,
      boolean excludeCurrentInvoice,
      int invoiceOperationTypeSelect) {

    String query = "SELECT SUM(self.companyExTaxTotal)" + " FROM InvoiceLine as self";
    query += " WHERE self.saleOrderLine.saleOrder.id = :saleOrderId";
    query +=
        " AND self.invoice.operationTypeSelect = :invoiceOperationTypeSelect"
            + " AND self.invoice.statusSelect = :statusVentilated";

    // exclude invoices that are advance payments
    boolean invoiceIsNotAdvancePayment =
        (currentInvoiceId != null
            && invoiceRepo.find(currentInvoiceId).getOperationSubTypeSelect()
                != InvoiceRepository.OPERATION_SUB_TYPE_ADVANCE);

    if (invoiceIsNotAdvancePayment) {
      if (excludeCurrentInvoice) {
        query += " AND self.invoice.id <> :invoiceId";
      } else {
        query +=
            " OR (self.invoice.id = :invoiceId AND self.invoice.operationTypeSelect = :invoiceOperationTypeSelect) ";
      }
    }

    javax.persistence.Query q = JPA.em().createQuery(query, BigDecimal.class);

    q.setParameter("saleOrderId", saleOrder.getId());
    q.setParameter("statusVentilated", InvoiceRepository.STATUS_VENTILATED);
    q.setParameter("invoiceOperationTypeSelect", invoiceOperationTypeSelect);
    if (invoiceIsNotAdvancePayment) {
      q.setParameter("invoiceId", currentInvoiceId);
    }

    BigDecimal invoicedAmount = (BigDecimal) q.getSingleResult();

    if (invoicedAmount != null) {
      return invoicedAmount;
    } else {
      return BigDecimal.ZERO;
    }
  }

  @Override
  @Transactional
  public Invoice mergeInvoice(
      List<Invoice> invoiceList,
      Company company,
      Currency currency,
      Partner partner,
      Partner contactPartner,
      PriceList priceList,
      PaymentMode paymentMode,
      PaymentCondition paymentCondition,
      SaleOrder saleOrder)
      throws AxelorException {
    log.debug("service supplychain 1 (saleOrder) {}", saleOrder);
    if (saleOrder != null) {
      String numSeq = "";
      String externalRef = "";

      for (Invoice invoiceLocal : invoiceList) {
        if (!numSeq.isEmpty()) {
          numSeq += "-";
        }
        if (invoiceLocal.getInternalReference() != null) {
          numSeq += invoiceLocal.getInternalReference();
        }

        if (!externalRef.isEmpty()) {
          externalRef += "|";
        }
        if (invoiceLocal.getExternalReference() != null) {
          externalRef += invoiceLocal.getExternalReference();
        }
      }
      InvoiceGenerator invoiceGenerator = this.createInvoiceGenerator(saleOrder);
      Invoice invoiceMerged = invoiceGenerator.generate();
      invoiceMerged.setExternalReference(externalRef);
      invoiceMerged.setInternalReference(numSeq);

      if (paymentMode != null) invoiceMerged.setPaymentMode(paymentMode);
      if (paymentCondition != null) invoiceMerged.setPaymentCondition(paymentCondition);

      List<InvoiceLine> invoiceLines = invoiceService.getInvoiceLinesFromInvoiceList(invoiceList);
      invoiceGenerator.populate(invoiceMerged, invoiceLines);
      invoiceService.setInvoiceForInvoiceLines(invoiceLines, invoiceMerged);
      invoiceMerged.setSaleOrder(null);
      invoiceRepo.save(invoiceMerged);
      swapStockMoveInvoices(invoiceList, invoiceMerged);
      invoiceService.deleteOldInvoices(invoiceList);
      return invoiceMerged;
    } else {

      Invoice invoiceMerged =
          invoiceService.mergeInvoice(
              invoiceList,
              company,
              currency,
              partner,
              contactPartner,
              priceList,
              paymentMode,
              paymentCondition);
      swapStockMoveInvoices(invoiceList, invoiceMerged);
      invoiceService.deleteOldInvoices(invoiceList);
      return invoiceMerged;
    }
  }

  @Transactional
  public void swapStockMoveInvoices(List<Invoice> invoiceList, Invoice newInvoice) {
    com.axelor.db.Query<StockMove> stockMoveQuery =
        stockMoveRepository
            .all()
            .filter("self.invoiceSet.id in (" + getIdListString(invoiceList) + ")");
    stockMoveQuery
        .fetch()
        .forEach(
            stockMove -> {
              if (stockMove.getInvoiceSet() != null) {
                stockMove.getInvoiceSet().add(newInvoice);
              } else {
                Set<Invoice> invoiceSet = new HashSet<>();
                invoiceSet.add(newInvoice);
                stockMove.setInvoiceSet(invoiceSet);
              }
              stockMoveRepository.save(stockMove);
            });
  }

  @Override
  public BigDecimal getInTaxInvoicedAmount(SaleOrder saleOrder) {
    BigDecimal exTaxTotal = saleOrder.getExTaxTotal();
    BigDecimal inTaxTotal = saleOrder.getInTaxTotal();

    BigDecimal exTaxAmountInvoiced = saleOrder.getAmountInvoiced();
    if (exTaxTotal.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    } else {
      return inTaxTotal
          .multiply(exTaxAmountInvoiced)
          .divide(exTaxTotal, 2, BigDecimal.ROUND_HALF_EVEN);
    }
  }

  @Override
  public List<Integer> getInvoicingWizardOperationDomain(SaleOrder saleOrder) {
    boolean manageAdvanceInvoice =
        Beans.get(AppAccountService.class).getAppAccount().getManageAdvancePaymentInvoice();
    boolean allowTimetableInvoicing =
        Beans.get(AppSupplychainService.class).getAppSupplychain().getAllowTimetableInvoicing();
    BigDecimal amountInvoiced = saleOrder.getAmountInvoiced();
    BigDecimal exTaxTotal = saleOrder.getExTaxTotal();
    Invoice invoice =
        Query.of(Invoice.class)
            .filter(" self.saleOrder.id = :saleOrderId AND self.statusSelect != :invoiceStatus")
            .bind("saleOrderId", saleOrder.getId())
            .bind("invoiceStatus", InvoiceRepository.STATUS_CANCELED)
            .fetchOne();
    List<Integer> operationSelectList = new ArrayList<>();
    operationSelectList.add(0);
    if (exTaxTotal.compareTo(BigDecimal.ZERO) != 0) {
      operationSelectList.add(Integer.valueOf(SaleOrderRepository.INVOICE_LINES));
    }
    if (manageAdvanceInvoice && exTaxTotal.compareTo(BigDecimal.ZERO) != 0) {
      operationSelectList.add(Integer.valueOf(SaleOrderRepository.INVOICE_ADVANCE_PAYMENT));
    }
    if (allowTimetableInvoicing) {
      operationSelectList.add(Integer.valueOf(SaleOrderRepository.INVOICE_TIMETABLES));
    }
    if (invoice == null && amountInvoiced.compareTo(BigDecimal.ZERO) == 0
        || exTaxTotal.compareTo(BigDecimal.ZERO) == 0) {
      operationSelectList.add(Integer.valueOf(SaleOrderRepository.INVOICE_ALL));
    }

    return operationSelectList;
  }

  @Override
  public void displayErrorMessageIfSaleOrderIsInvoiceable(
      SaleOrder saleOrder, BigDecimal amountToInvoice, boolean isPercent) throws AxelorException {
    List<Invoice> invoices =
        Query.of(Invoice.class)
            .filter(" self.saleOrder.id = :saleOrderId AND self.statusSelect != :invoiceStatus")
            .bind("saleOrderId", saleOrder.getId())
            .bind("invoiceStatus", InvoiceRepository.STATUS_CANCELED)
            .fetch();
    if (isPercent) {
      amountToInvoice =
          (amountToInvoice.multiply(saleOrder.getExTaxTotal()))
              .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_EVEN);
    }
    BigDecimal sumInvoices = computeSumInvoices(invoices);
    sumInvoices = sumInvoices.add(amountToInvoice);
    if (sumInvoices.compareTo(saleOrder.getExTaxTotal()) > 0) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_TOO_MUCH_INVOICED),
          saleOrder.getSaleOrderSeq());
    }
  }

  @Override
  public void displayErrorMessageBtnGenerateInvoice(SaleOrder saleOrder) throws AxelorException {
    List<Invoice> invoices =
        Query.of(Invoice.class)
            .filter(
                " self.saleOrder.id = :saleOrderId AND self.operationSubTypeSelect = :operationSubTypeSelect"
                    + " AND self.statusSelect != :invoiceStatus")
            .bind("saleOrderId", saleOrder.getId())
            .bind("operationSubTypeSelect", InvoiceRepository.OPERATION_SUB_TYPE_DEFAULT)
            .bind("invoiceStatus", InvoiceRepository.STATUS_CANCELED)
            .fetch();
    BigDecimal sumInvoices = computeSumInvoices(invoices);
    if (sumInvoices.compareTo(saleOrder.getExTaxTotal()) > 0) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SO_INVOICE_GENERATE_ALL_INVOICES));
    }
  }

  protected BigDecimal computeSumInvoices(List<Invoice> invoices) {
    BigDecimal sumInvoices = BigDecimal.ZERO;
    for (Invoice invoice : invoices) {
      if (invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND
          || invoice.getOperationTypeSelect() == InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND) {
        sumInvoices = sumInvoices.subtract(invoice.getExTaxTotal());
      } else {
        sumInvoices = sumInvoices.add(invoice.getExTaxTotal());
      }
    }
    return sumInvoices;
  }
}

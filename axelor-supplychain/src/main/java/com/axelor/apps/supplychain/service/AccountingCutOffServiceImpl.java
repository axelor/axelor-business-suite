/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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

import static com.axelor.apps.base.service.administration.AbstractBatch.FETCH_LIMIT;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.AccountConfig;
import com.axelor.apps.account.db.AnalyticDistributionTemplate;
import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.Tax;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.TaxAccountService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.account.service.move.MoveCreateService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.base.db.Batch;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppAccountRepository;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.db.repo.SupplychainBatchRepository;
import com.axelor.apps.supplychain.service.config.AccountConfigSupplychainService;
import com.axelor.db.JPA;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class AccountingCutOffServiceImpl implements AccountingCutOffService {

  protected StockMoveRepository stockMoverepository;
  protected StockMoveLineRepository stockMoveLineRepository;
  protected MoveCreateService moveCreateService;
  protected MoveLineService moveLineService;
  protected AccountConfigSupplychainService accountConfigSupplychainService;
  protected SaleOrderRepository saleOrderRepository;
  protected PurchaseOrderRepository purchaseOrderRepository;
  protected MoveToolService moveToolService;
  protected AccountManagementAccountService accountManagementAccountService;
  protected TaxAccountService taxAccountService;
  protected AppAccountService appAccountService;
  protected AnalyticMoveLineService analyticMoveLineService;
  protected MoveRepository moveRepository;
  protected MoveValidateService moveValidateService;
  protected UnitConversionService unitConversionService;
  protected AnalyticMoveLineRepository analyticMoveLineRepository;
  protected ReconcileService reconcileService;
  protected int counter = 0;

  @Inject
  public AccountingCutOffServiceImpl(
      StockMoveRepository stockMoverepository,
      StockMoveLineRepository stockMoveLineRepository,
      MoveCreateService moveCreateService,
      MoveLineService moveLineService,
      AccountConfigSupplychainService accountConfigSupplychainService,
      SaleOrderRepository saleOrderRepository,
      PurchaseOrderRepository purchaseOrderRepository,
      MoveToolService moveToolService,
      AccountManagementAccountService accountManagementAccountService,
      TaxAccountService taxAccountService,
      AppAccountService appAccountService,
      AnalyticMoveLineService analyticMoveLineService,
      MoveRepository moveRepository,
      MoveValidateService moveValidateService,
      UnitConversionService unitConversionService,
      AnalyticMoveLineRepository analyticMoveLineRepository,
      ReconcileService reconcileService) {

    this.stockMoverepository = stockMoverepository;
    this.stockMoveLineRepository = stockMoveLineRepository;
    this.moveCreateService = moveCreateService;
    this.moveLineService = moveLineService;
    this.accountConfigSupplychainService = accountConfigSupplychainService;
    this.saleOrderRepository = saleOrderRepository;
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.moveToolService = moveToolService;
    this.accountManagementAccountService = accountManagementAccountService;
    this.taxAccountService = taxAccountService;
    this.appAccountService = appAccountService;
    this.analyticMoveLineService = analyticMoveLineService;
    this.moveRepository = moveRepository;
    this.moveValidateService = moveValidateService;
    this.unitConversionService = unitConversionService;
    this.analyticMoveLineRepository = analyticMoveLineRepository;
    this.reconcileService = reconcileService;
  }

  public List<StockMove> getStockMoves(
      Company company,
      int accountingCutOffTypeSelect,
      LocalDate moveDate,
      Integer limit,
      Integer offset) {

    int stockMoveTypeSelect = 0;

    if (accountingCutOffTypeSelect
        == SupplychainBatchRepository.ACCOUNTING_CUT_OFF_TYPE_SUPPLIER_INVOICES) {
      stockMoveTypeSelect = StockMoveRepository.TYPE_INCOMING;
    } else if (accountingCutOffTypeSelect
        == SupplychainBatchRepository.ACCOUNTING_CUT_OFF_TYPE_CUSTOMER_INVOICES) {
      stockMoveTypeSelect = StockMoveRepository.TYPE_OUTGOING;
    }

    String queryStr =
        "self.invoicingStatusSelect != :stockMoveInvoiced "
            + "AND self.statusSelect = :stockMoveStatusRealized and self.realDate <= :moveDate "
            + "AND self.typeSelect = :stockMoveType ";

    if (company != null) {
      queryStr += "AND self.company.id = :companyId";
    }

    Query<StockMove> query =
        stockMoverepository
            .all()
            .filter(queryStr)
            .bind("stockMoveInvoiced", StockMoveRepository.STATUS_INVOICED)
            .bind("stockMoveStatusRealized", StockMoveRepository.STATUS_REALIZED)
            .bind("stockMoveType", stockMoveTypeSelect)
            .bind("moveDate", moveDate);

    if (company != null) {
      query.bind("companyId", company.getId());
    }

    if (limit != null && offset != null) {
      return query.order("id").fetch(limit, offset);
    }

    return query.order("id").fetch();
  }

  @Transactional(rollbackOn = {Exception.class})
  public List<Move> generateCutOffMoves(
      StockMove stockMove,
      LocalDate moveDate,
      LocalDate reverseMoveDate,
      int accountingCutOffTypeSelect,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean includeNotStockManagedProduct)
      throws AxelorException {

    List<Move> moveList = new ArrayList<>();

    List<StockMoveLine> stockMoveLineSortedList = stockMove.getStockMoveLineList();
    Collections.sort(stockMoveLineSortedList, Comparator.comparing(StockMoveLine::getSequence));

    Move move =
        generateCutOffMove(
            stockMove,
            stockMoveLineSortedList,
            moveDate,
            moveDate,
            accountingCutOffTypeSelect
                == SupplychainBatchRepository.ACCOUNTING_CUT_OFF_TYPE_SUPPLIER_INVOICES,
            recoveredTax,
            ati,
            moveDescription,
            includeNotStockManagedProduct,
            false);

    if (move == null) {
      return null;
    }
    moveList.add(move);

    Move reverseMove =
        generateCutOffMove(
            stockMove,
            stockMoveLineSortedList,
            reverseMoveDate,
            moveDate,
            accountingCutOffTypeSelect
                == SupplychainBatchRepository.ACCOUNTING_CUT_OFF_TYPE_SUPPLIER_INVOICES,
            recoveredTax,
            ati,
            moveDescription,
            includeNotStockManagedProduct,
            true);

    if (reverseMove == null) {
      return null;
    }
    moveList.add(reverseMove);

    reconcile(move, reverseMove);

    return moveList;
  }

  public Move generateCutOffMove(
      StockMove stockMove,
      List<StockMoveLine> sortedStockMoveLine,
      LocalDate moveDate,
      LocalDate originDate,
      boolean isPurchase,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean includeNotStockManagedProduct,
      boolean isReverse)
      throws AxelorException {

    if (moveDate == null
        || stockMove.getOriginTypeSelect() == null
        || stockMove.getOriginId() == null) {
      return null;
    }

    Company company = stockMove.getCompany();

    AccountConfig accountConfig = accountConfigSupplychainService.getAccountConfig(company);

    Partner partner = stockMove.getPartner();
    Account partnerAccount = null;

    Currency currency = null;
    if (StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())
        && stockMove.getOriginId() != null) {
      SaleOrder saleOrder = saleOrderRepository.find(stockMove.getOriginId());
      currency = saleOrder.getCurrency();
      if (partner == null) {
        partner = saleOrder.getClientPartner();
      }
      partnerAccount = accountConfigSupplychainService.getForecastedInvCustAccount(accountConfig);
    }
    if (StockMoveRepository.ORIGIN_PURCHASE_ORDER.equals(stockMove.getOriginTypeSelect())
        && stockMove.getOriginId() != null) {
      PurchaseOrder purchaseOrder = purchaseOrderRepository.find(stockMove.getOriginId());
      currency = purchaseOrder.getCurrency();
      if (partner == null) {
        partner = purchaseOrder.getSupplierPartner();
      }
      partnerAccount = accountConfigSupplychainService.getForecastedInvSuppAccount(accountConfig);
    }

    String origin = stockMove.getStockMoveSeq();

    Move move =
        moveCreateService.createMove(
            accountConfigSupplychainService.getAutoMiscOpeJournal(accountConfig),
            company,
            currency,
            partner,
            moveDate,
            null,
            MoveRepository.TECHNICAL_ORIGIN_AUTOMATIC);

    counter = 0;

    this.generateMoveLines(
        move,
        stockMove.getStockMoveLineList(),
        origin,
        isPurchase,
        recoveredTax,
        ati,
        moveDescription,
        isReverse,
        originDate,
        includeNotStockManagedProduct);

    this.generatePartnerMoveLine(move, origin, partnerAccount, moveDescription, originDate);

    if (move.getMoveLineList() != null && !move.getMoveLineList().isEmpty()) {
      move.setStockMove(stockMove);
      moveValidateService.validate(move);
    } else {
      moveRepository.remove(move);
      return null;
    }

    return move;
  }

  protected List<MoveLine> generateMoveLines(
      Move move,
      List<StockMoveLine> stockMoveLineList,
      String origin,
      boolean isPurchase,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean isReverse,
      LocalDate originDate,
      boolean includeNotStockManagedProduct)
      throws AxelorException {

    if (stockMoveLineList != null) {

      for (StockMoveLine stockMoveLine : stockMoveLineList) {

        Product product = stockMoveLine.getProduct();

        if (checkStockMoveLine(stockMoveLine, product, includeNotStockManagedProduct)) {
          continue;
        }

        generateProductMoveLine(
            move,
            stockMoveLine,
            origin,
            isPurchase,
            recoveredTax,
            ati,
            moveDescription,
            isReverse,
            originDate);
      }
    }

    return move.getMoveLineList();
  }

  protected boolean checkStockMoveLine(
      StockMoveLine stockMoveLine, Product product, boolean includeNotStockManagedProduct) {
    return (stockMoveLine.getRealQty().compareTo(BigDecimal.ZERO) == 0
            || product == null
            || (!includeNotStockManagedProduct && !product.getStockManaged()))
        || (stockMoveLine.getRealQty().compareTo(stockMoveLine.getQtyInvoiced()) == 0);
  }

  protected MoveLine generateProductMoveLine(
      Move move,
      StockMoveLine stockMoveLine,
      String origin,
      boolean isPurchase,
      boolean recoveredTax,
      boolean ati,
      String moveDescription,
      boolean isReverse,
      LocalDate originDate)
      throws AxelorException {

    SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    PurchaseOrderLine purchaseOrderLine = stockMoveLine.getPurchaseOrderLine();
    Company company = move.getCompany();
    LocalDate moveDate = move.getDate();
    Partner partner = move.getPartner();

    boolean isFixedAssets = false;
    BigDecimal amountInCurrency = null;
    BigDecimal totalQty = null;
    BigDecimal notInvoicedQty = null;

    if (isPurchase && purchaseOrderLine != null) {
      totalQty = purchaseOrderLine.getQty();

      notInvoicedQty =
          unitConversionService.convert(
              stockMoveLine.getUnit(),
              purchaseOrderLine.getUnit(),
              stockMoveLine.getRealQty().subtract(stockMoveLine.getQtyInvoiced()),
              stockMoveLine.getRealQty().scale(),
              purchaseOrderLine.getProduct());

      isFixedAssets = purchaseOrderLine.getFixedAssets();
      if (ati && !recoveredTax) {
        amountInCurrency = purchaseOrderLine.getInTaxTotal();
      } else {
        amountInCurrency = purchaseOrderLine.getExTaxTotal();
      }
    }
    if (!isPurchase && saleOrderLine != null) {
      totalQty = saleOrderLine.getQty();

      notInvoicedQty =
          unitConversionService.convert(
              stockMoveLine.getUnit(),
              saleOrderLine.getUnit(),
              stockMoveLine.getRealQty().subtract(stockMoveLine.getQtyInvoiced()),
              stockMoveLine.getRealQty().scale(),
              saleOrderLine.getProduct());
      if (ati) {
        amountInCurrency = saleOrderLine.getInTaxTotal();
      } else {
        amountInCurrency = saleOrderLine.getExTaxTotal();
      }
    }
    if (totalQty == null || BigDecimal.ZERO.compareTo(totalQty) == 0) {
      return null;
    }

    BigDecimal qtyRate = notInvoicedQty.divide(totalQty, 10, RoundingMode.HALF_EVEN);
    amountInCurrency = amountInCurrency.multiply(qtyRate).setScale(2, RoundingMode.HALF_EVEN);

    if (amountInCurrency == null || amountInCurrency.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    Product product = stockMoveLine.getProduct();

    Account account =
        accountManagementAccountService.getProductAccount(
            product, company, partner.getFiscalPosition(), isPurchase, isFixedAssets);

    boolean isDebit = false;
    if ((isPurchase && amountInCurrency.compareTo(BigDecimal.ZERO) == 1)
        || !isPurchase && amountInCurrency.compareTo(BigDecimal.ZERO) == -1) {
      isDebit = true;
    }
    if (isReverse) {
      isDebit = !isDebit;
    }

    MoveLine moveLine =
        moveLineService.createMoveLine(
            move,
            partner,
            account,
            amountInCurrency,
            isDebit,
            originDate,
            ++counter,
            origin,
            moveDescription);
    moveLine.setDate(moveDate);
    moveLine.setDueDate(moveDate);

    getAndComputeAnalyticDistribution(product, move, moveLine);

    move.addMoveLineListItem(moveLine);

    if (recoveredTax) {
      TaxLine taxLine =
          accountManagementAccountService.getTaxLine(
              originDate, product, company, partner.getFiscalPosition(), isPurchase);
      if (taxLine != null) {
        moveLine.setTaxLine(taxLine);
        moveLine.setTaxRate(taxLine.getValue());
        moveLine.setTaxCode(taxLine.getTax().getCode());

        if (taxLine.getValue().compareTo(BigDecimal.ZERO) != 0) {
          generateTaxMoveLine(move, moveLine, origin, isPurchase, isFixedAssets, moveDescription);
        }
      }
    }

    return moveLine;
  }

  protected void generateTaxMoveLine(
      Move move,
      MoveLine productMoveLine,
      String origin,
      boolean isPurchase,
      boolean isFixedAssets,
      String moveDescription)
      throws AxelorException {

    TaxLine taxLine = productMoveLine.getTaxLine();

    Tax tax = taxLine.getTax();

    Account taxAccount =
        taxAccountService.getAccount(tax, move.getCompany(), isPurchase, isFixedAssets);

    BigDecimal currencyTaxAmount =
        InvoiceLineManagement.computeAmount(
            productMoveLine.getCurrencyAmount(), taxLine.getValue().divide(new BigDecimal(100)));

    MoveLine taxMoveLine =
        moveLineService.createMoveLine(
            move,
            move.getPartner(),
            taxAccount,
            currencyTaxAmount,
            productMoveLine.getDebit().compareTo(BigDecimal.ZERO) == 1,
            productMoveLine.getOriginDate(),
            ++counter,
            origin,
            moveDescription);
    taxMoveLine.setDate(move.getDate());
    taxMoveLine.setDueDate(move.getDate());

    move.addMoveLineListItem(taxMoveLine);
  }

  protected MoveLine generatePartnerMoveLine(
      Move move, String origin, Account account, String moveDescription, LocalDate originDate)
      throws AxelorException {
    LocalDate moveDate = move.getDate();

    BigDecimal currencyBalance = moveToolService.getBalanceCurrencyAmount(move.getMoveLineList());
    BigDecimal balance = moveToolService.getBalanceAmount(move.getMoveLineList());

    if (balance.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    MoveLine moveLine =
        moveLineService.createMoveLine(
            move,
            move.getPartner(),
            account,
            currencyBalance.abs(),
            balance.abs(),
            null,
            balance.compareTo(BigDecimal.ZERO) == -1,
            moveDate,
            moveDate,
            originDate,
            ++counter,
            origin,
            moveDescription);

    move.addMoveLineListItem(moveLine);

    return moveLine;
  }

  protected void getAndComputeAnalyticDistribution(Product product, Move move, MoveLine moveLine) {

    if (appAccountService.getAppAccount().getAnalyticDistributionTypeSelect()
        == AppAccountRepository.DISTRIBUTION_TYPE_FREE) {
      return;
    }

    AnalyticDistributionTemplate analyticDistributionTemplate =
        analyticMoveLineService.getAnalyticDistributionTemplate(
            move.getPartner(), product, move.getCompany());

    moveLine.setAnalyticDistributionTemplate(analyticDistributionTemplate);

    List<AnalyticMoveLine> analyticMoveLineList =
        moveLineService.createAnalyticDistributionWithTemplate(moveLine).getAnalyticMoveLineList();
    for (AnalyticMoveLine analyticMoveLine : analyticMoveLineList) {
      analyticMoveLine.setMoveLine(moveLine);
    }
    analyticMoveLineList.stream().forEach(analyticMoveLineRepository::save);
  }

  protected void reconcile(Move move, Move reverseMove) throws AxelorException {

    List<MoveLine> moveLineSortedList = move.getMoveLineList();
    Collections.sort(moveLineSortedList, Comparator.comparing(MoveLine::getCounter));

    List<MoveLine> reverseMoveLineSortedList = reverseMove.getMoveLineList();
    Collections.sort(reverseMoveLineSortedList, Comparator.comparing(MoveLine::getCounter));

    Iterator<MoveLine> reverseMoveLinesIt = reverseMoveLineSortedList.iterator();

    for (MoveLine moveLine : moveLineSortedList) {

      MoveLine reverseMoveLine = reverseMoveLinesIt.next();

      reconcileService.reconcile(moveLine, reverseMoveLine, false, false);
    }
  }

  public List<Long> getStockMoveLines(Batch batch) {
    int offset = 0;
    Boolean includeNotStockManagedProduct =
        batch.getSupplychainBatch().getIncludeNotStockManagedProduct();

    List<StockMoveLine> stockMoveLineList;
    List<Long> stockMoveLineIdList = new ArrayList<>();

    Query<StockMove> stockMoveQuery =
        stockMoverepository.all().filter(":batch MEMBER OF self.batchSet").bind("batch", batch);
    List<Long> stockMoveIdList =
        stockMoveQuery.select("id").fetch(0, 0).stream()
            .map(m -> (Long) m.get("id"))
            .collect(Collectors.toList());

    if (stockMoveIdList.isEmpty()) {
      stockMoveLineIdList.add(0L);
    } else {
      Query<StockMoveLine> stockMoveLineQuery =
          stockMoveLineRepository
              .all()
              .filter("self.stockMove.id IN :stockMoveIdList")
              .bind("stockMoveIdList", stockMoveIdList)
              .order("id");

      while (!(stockMoveLineList = stockMoveLineQuery.fetch(FETCH_LIMIT, offset)).isEmpty()) {
        offset += stockMoveLineList.size();

        for (StockMoveLine stockMoveLine : stockMoveLineList) {
          Product product = stockMoveLine.getProduct();
          if (!checkStockMoveLine(stockMoveLine, product, includeNotStockManagedProduct)) {
            stockMoveLineIdList.add(stockMoveLine.getId());
          }
        }

        JPA.clear();
      }
    }
    return stockMoveLineIdList;
  }
}

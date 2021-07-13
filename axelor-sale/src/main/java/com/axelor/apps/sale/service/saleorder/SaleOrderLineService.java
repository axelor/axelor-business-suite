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
package com.axelor.apps.sale.service.saleorder;

import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.sale.db.ComplementaryProduct;
import com.axelor.apps.sale.db.Pack;
import com.axelor.apps.sale.db.PackLine;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.exception.AxelorException;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface SaleOrderLineService {

  /**
   * Update all fields of the sale order line from the product.
   *
   * @param saleOrderLine
   * @param saleOrder
   * @throws ClassNotFoundException
   */
  void computeProductInformation(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;

  SaleOrderLine resetProductInformation(SaleOrderLine line);

  /**
   * Compute totals from a sale order line
   *
   * @param saleOrder
   * @param saleOrderLine
   * @return
   * @throws AxelorException
   */
  public Map<String, BigDecimal> computeValues(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException;

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @param saleOrderLine the sale order line which total amount you want to compute.
   * @return The excluded tax total amount.
   */
  public BigDecimal computeAmount(SaleOrderLine saleOrderLine);

  /**
   * Compute the excluded tax total amount of a sale order line.
   *
   * @param quantity The quantity.
   * @param price The unit price.
   * @return The excluded tax total amount.
   */
  public BigDecimal computeAmount(BigDecimal quantity, BigDecimal price);

  public BigDecimal getExTaxUnitPrice(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException;

  public BigDecimal getInTaxUnitPrice(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException;

  public TaxLine getTaxLine(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException;

  public BigDecimal getAmountInCompanyCurrency(BigDecimal exTaxTotal, SaleOrder saleOrder)
      throws AxelorException;

  public BigDecimal getCompanyCostPrice(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException;

  public PriceListLine getPriceListLine(
      SaleOrderLine saleOrderLine, PriceList priceList, BigDecimal price);

  /**
   * Compute and return the discounted price of a sale order line.
   *
   * @param saleOrderLine the sale order line.
   * @param inAti whether or not the sale order line (and thus the discounted price) includes taxes.
   * @return the discounted price of the line, including taxes if inAti is true.
   */
  public BigDecimal computeDiscount(SaleOrderLine saleOrderLine, Boolean inAti);

  /**
   * Convert a product's unit price from incl. tax to ex. tax or the other way round.
   *
   * <p>If the price is ati, it will be converted to ex. tax, and if it isn't it will be converted
   * to ati.
   *
   * @param priceIsAti a boolean indicating if the price is ati.
   * @param taxLine the tax to apply.
   * @param price the unit price to convert.
   * @return the converted price as a BigDecimal.
   */
  public BigDecimal convertUnitPrice(Boolean inAti, TaxLine taxLine, BigDecimal price);

  public Map<String, Object> getDiscountsFromPriceLists(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, BigDecimal price);

  public int getDiscountTypeSelect(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, BigDecimal price);

  public Unit getSaleUnit(SaleOrderLine saleOrderLine);

  public SaleOrder getSaleOrder(Context context);

  public Map<String, BigDecimal> computeSubMargin(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException;

  public BigDecimal getAvailableStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine);

  public BigDecimal getAllocatedStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine);

  public void checkMultipleQty(SaleOrderLine saleOrderLine, ActionResponse response);

  /**
   * Fill price for standard line.
   *
   * @param saleOrderLine
   * @param saleOrder
   * @throws AxelorException
   * @throws ClassNotFoundException
   */
  public void fillPrice(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException, ClassNotFoundException;

  /**
   * Fill the complementaryProductList of the saleOrderLine from the possible complementary products
   * of the product of the line
   *
   * @param saleOrderLine
   */
  public void fillComplementaryProductList(SaleOrderLine saleOrderLine);

  public SaleOrderLine createSaleOrderLine(
      PackLine packLine,
      SaleOrder saleOrder,
      BigDecimal packQty,
      BigDecimal conversionRate,
      Integer sequence);

  /**
   * Get unique values of type field from pack lines
   *
   * @param packLineList
   * @return
   */
  public Set<Integer> getPackLineTypes(List<PackLine> packLineList);

  /**
   * To create non standard SaleOrderLine from Pack.
   *
   * @param pack
   * @param saleOrder
   * @param packQty
   * @param saleOrderLineList
   * @param sequence
   * @return
   */
  public List<SaleOrderLine> createNonStandardSOLineFromPack(
      Pack pack,
      SaleOrder saleOrder,
      BigDecimal packQty,
      List<SaleOrderLine> saleOrderLineList,
      Integer sequence);

  /**
   * Finds max discount from product category and his parents, and returns it.
   *
   * @param saleOrder a sale order (from context or sale order line)
   * @param saleOrderLine a sale order line
   * @return The maximal discount or null if the value is not needed
   */
  BigDecimal computeMaxDiscount(SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException;

  /**
   * Compares sale order line discount with given max discount. Manages the two cases of amount
   * percent and amount fixed.
   *
   * @param saleOrderLine a sale order line
   * @param maxDiscount a max discount
   * @return whether the discount is greather than the one authorized
   */
  boolean isSaleOrderLineDiscountGreaterThanMaxDiscount(
      SaleOrderLine saleOrderLine, BigDecimal maxDiscount);

  /**
   * To create 'Start of pack' and 'End of pack' type {@link SaleOrderLine}.
   *
   * @param pack
   * @param saleOrder
   * @param packQty
   * @param packLine
   * @param typeSelect
   * @param sequence
   * @return
   */
  public SaleOrderLine createStartOfPackAndEndOfPackTypeSaleOrderLine(
      Pack pack,
      SaleOrder saleOrder,
      BigDecimal packQty,
      PackLine packLine,
      Integer typeSelect,
      Integer sequence);

  /**
   * To check that saleOrderLineList has "End of pack" type line.
   *
   * @param saleOrderLineList
   * @return
   */
  public boolean hasEndOfPackTypeLine(List<SaleOrderLine> saleOrderLineList);

  /**
   * Update product qty.
   *
   * @param saleOrderLine
   * @param saleOrder
   * @param oldQty
   * @param newQty
   * @return {@link SaleOrderLine}}
   * @throws AxelorException
   */
  public SaleOrderLine updateProductQty(
      SaleOrderLine saleOrderLine, SaleOrder saleOrder, BigDecimal oldQty, BigDecimal newQty)
      throws AxelorException;

  /**
   * To check that Start of pack type line quantity changed or not.
   *
   * @param saleOrderLineList
   * @return
   */
  public boolean isStartOfPackTypeLineQtyChanged(List<SaleOrderLine> saleOrderLineList);

  /**
   * Fill price for standard line from pack line.
   *
   * @param saleOrderLine
   * @param saleOrder
   * @return
   * @throws AxelorException
   */
  public void fillPriceFromPackLine(SaleOrderLine saleOrderLine, SaleOrder saleOrder)
      throws AxelorException;

  /**
   * A function used to get the ex tax unit price of a sale order line from pack line
   *
   * @param saleOrder the sale order containing the sale order line
   * @param saleOrderLine
   * @param taxLine
   * @return the ex tax unit price of the sale order line
   * @throws AxelorException
   */
  public BigDecimal getExTaxUnitPriceFromPackLine(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException;

  /**
   * A function used to get the in tax unit price of a sale order line from pack line
   *
   * @param saleOrder the sale order containing the sale order line
   * @param saleOrderLine
   * @param taxLine
   * @return the in tax unit price of the sale order line
   * @throws AxelorException
   */
  public BigDecimal getInTaxUnitPriceFromPackLine(
      SaleOrder saleOrder, SaleOrderLine saleOrderLine, TaxLine taxLine) throws AxelorException;

  /**
   * Compute product domain from configurations and sale order.
   *
   * @param saleOrderLine a sale order line
   * @param saleOrder a sale order (can be a sale order from context and not from database)
   * @return a String with the JPQL expression used to filter product selection
   */
  String computeProductDomain(SaleOrderLine saleOrderLine, SaleOrder saleOrder);

  /**
   * To manage Complementary Product sale order line.
   *
   * @param complementaryProduct
   * @param saleOrder
   * @param saleOrderLine
   * @return New complementary sales order lines
   * @throws AxelorException
   * @throws ClassNotFoundException
   */
  public List<SaleOrderLine> manageComplementaryProductSaleOrderLine(
      ComplementaryProduct complementaryProduct, SaleOrder saleOrder, SaleOrderLine saleOrderLine)
      throws AxelorException, ClassNotFoundException;

  public void computePricingScale(SaleOrder saleOrder, SaleOrderLine orderLine)
      throws ClassNotFoundException;
}

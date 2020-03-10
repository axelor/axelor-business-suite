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
package com.axelor.apps.stock.service;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.ProductCategory;
import com.axelor.apps.base.db.ProductFamily;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.stock.db.Inventory;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.TrackingNumber;
import com.axelor.apps.stock.db.repo.InventoryRepository;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.db.repo.TrackingNumberRepository;
import com.axelor.apps.stock.exception.IExceptionMessage;
import com.axelor.apps.stock.service.config.StockConfigService;
import com.axelor.apps.tool.file.CsvTool;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected InventoryLineService inventoryLineService;
  protected SequenceService sequenceService;
  protected StockConfigService stockConfigService;
  protected ProductRepository productRepo;
  protected InventoryRepository inventoryRepo;
  protected StockMoveRepository stockMoveRepo;
  protected StockLocationLineService stockLocationLineService;
  protected StockMoveService stockMoveService;
  protected StockMoveLineService stockMoveLineService;
  protected StockLocationLineRepository stockLocationLineRepository;
  protected TrackingNumberRepository trackingNumberRepository;
  protected AppBaseService appBaseService;

  @Inject
  public InventoryService(
      InventoryLineService inventoryLineService,
      SequenceService sequenceService,
      StockConfigService stockConfigService,
      ProductRepository productRepo,
      InventoryRepository inventoryRepo,
      StockMoveRepository stockMoveRepo,
      StockLocationLineService stockLocationLineService,
      StockMoveService stockMoveService,
      StockMoveLineService stockMoveLineService,
      StockLocationLineRepository stockLocationLineRepository,
      TrackingNumberRepository trackingNumberRepository,
      AppBaseService appBaseService) {
    this.inventoryLineService = inventoryLineService;
    this.sequenceService = sequenceService;
    this.stockConfigService = stockConfigService;
    this.productRepo = productRepo;
    this.inventoryRepo = inventoryRepo;
    this.stockMoveRepo = stockMoveRepo;
    this.stockLocationLineService = stockLocationLineService;
    this.stockMoveService = stockMoveService;
    this.stockMoveLineService = stockMoveLineService;
    this.stockLocationLineRepository = stockLocationLineRepository;
    this.trackingNumberRepository = trackingNumberRepository;
    this.appBaseService = appBaseService;
  }

  public Inventory createInventory(
      LocalDate plannedStartDate,
      LocalDate plannedEndDate,
      String description,
      StockLocation stockLocation,
      boolean excludeOutOfStock,
      boolean includeObsolete,
      ProductFamily productFamily,
      ProductCategory productCategory)
      throws AxelorException {

    if (stockLocation == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVENTORY_1));
    }

    Inventory inventory = new Inventory();

    inventory.setInventorySeq(this.getInventorySequence(stockLocation.getCompany()));

    inventory.setPlannedStartDateT(plannedStartDate.atStartOfDay(ZoneOffset.UTC));

    inventory.setPlannedEndDateT(plannedEndDate.atStartOfDay(ZoneOffset.UTC));

    inventory.setDescription(description);

    inventory.setFormatSelect(InventoryRepository.FORMAT_PDF);

    inventory.setStockLocation(stockLocation);

    inventory.setExcludeOutOfStock(excludeOutOfStock);

    inventory.setIncludeObsolete(includeObsolete);

    inventory.setProductCategory(productCategory);

    inventory.setProductFamily(productFamily);

    inventory.setStatusSelect(InventoryRepository.STATUS_DRAFT);

    return inventory;
  }

  public String getInventorySequence(Company company) throws AxelorException {

    String ref = sequenceService.getSequenceNumber(SequenceRepository.INVENTORY, company);
    if (ref == null)
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVENTORY_2) + " " + company.getName());

    return ref;
  }

  @Transactional(rollbackOn = {Exception.class})
  public Path importFile(Inventory inventory) throws AxelorException {

    List<InventoryLine> inventoryLineList = inventory.getInventoryLineList();
    Path filePath = MetaFiles.getPath(inventory.getImportFile());
    List<String[]> data = this.getDatas(filePath);

    HashMap<String, InventoryLine> inventoryLineMap = this.getInventoryLines(inventory);

    for (String[] line : data) {
      if (line.length < 6)
        throw new AxelorException(
            new Throwable(I18n.get(IExceptionMessage.INVENTORY_3_LINE_LENGHT)),
            inventory,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.INVENTORY_3));

      String code = line[1].replace("\"", "");
      String rack = line[2].replace("\"", "");
      String trackingNumberSeq = line[3].replace("\"", "");

      BigDecimal realQty;
      try {
        realQty = new BigDecimal(line[5].replace("\"", ""));
      } catch (NumberFormatException e) {
        throw new AxelorException(
            new Throwable(I18n.get(IExceptionMessage.INVENTORY_3_REAL_QUANTITY)),
            inventory,
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.INVENTORY_3));
      }

      String description = line[6].replace("\"", "");

      if (inventoryLineMap.containsKey(code)) {
        inventoryLineMap.get(code).setRealQty(realQty);
        inventoryLineMap.get(code).setDescription(description);
      } else {
        BigDecimal currentQty;
        try {
          currentQty = new BigDecimal(line[4].replace("\"", ""));
        } catch (NumberFormatException e) {
          throw new AxelorException(
              new Throwable(I18n.get(IExceptionMessage.INVENTORY_3_CURRENT_QUANTITY)),
              inventory,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVENTORY_3));
        }

        InventoryLine inventoryLine = new InventoryLine();
        List<Product> productList =
            productRepo.all().filter("self.code = :code").bind("code", code).fetch();
        if (productList != null && !productList.isEmpty()) {
          if (productList.size() > 1) {
            throw new AxelorException(
                inventory,
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                I18n.get(IExceptionMessage.INVENTORY_12) + " " + code);
          }
        }
        Product product = productList.get(0);
        if (product == null
            || !product.getProductTypeSelect().equals(ProductRepository.PRODUCT_TYPE_STORABLE))
          throw new AxelorException(
              inventory,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.INVENTORY_4) + " " + code);
        inventoryLine.setProduct(product);
        inventoryLine.setInventory(inventory);
        inventoryLine.setRack(rack);
        inventoryLine.setCurrentQty(currentQty);
        inventoryLine.setRealQty(realQty);
        inventoryLine.setDescription(description);
        inventoryLine.setTrackingNumber(this.getTrackingNumber(trackingNumberSeq));
        inventoryLineList.add(inventoryLine);
      }
    }
    inventory.setInventoryLineList(inventoryLineList);

    inventoryRepo.save(inventory);
    return filePath;
  }

  public List<String[]> getDatas(Path filePath) throws AxelorException {

    List<String[]> data = null;
    char separator = ';';
    try {
      data = CsvTool.cSVFileReader(filePath.toString(), separator);
    } catch (Exception e) {
      throw new AxelorException(
          e.getCause(),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVENTORY_5));
    }

    if (data == null || data.isEmpty()) {
      throw new AxelorException(
          new Throwable(I18n.get(IExceptionMessage.INVENTORY_3_DATA_NULL_OR_EMPTY)),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVENTORY_3));
    }

    data.remove(0); /* Skip headers */
    return data;
  }

  public HashMap<String, InventoryLine> getInventoryLines(Inventory inventory) {
    HashMap<String, InventoryLine> inventoryLineMap = new HashMap<>();

    for (InventoryLine line : inventory.getInventoryLineList()) {
      String key = "";
      if (line.getProduct() != null) {
        key += line.getProduct().getCode();
      }
      if (line.getTrackingNumber() != null) {
        key += line.getTrackingNumber().getTrackingNumberSeq();
      }

      inventoryLineMap.put(key, line);
    }

    return inventoryLineMap;
  }

  public TrackingNumber getTrackingNumber(String sequence) {

    if (sequence != null && !sequence.isEmpty()) {
      return trackingNumberRepository.findBySeq(sequence);
    }
    return null;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void validateInventory(Inventory inventory) throws AxelorException {

    inventory.setValidatedOn(appBaseService.getTodayDate());
    inventory.setStatusSelect(InventoryRepository.STATUS_VALIDATED);
    inventory.setValidatedBy(AuthUtils.getUser());
    generateStockMove(inventory, true);
    generateStockMove(inventory, false);
    storeLastInventoryData(inventory);
  }

  protected void storeLastInventoryData(Inventory inventory) {
    Map<Pair<Product, TrackingNumber>, BigDecimal> realQties = new HashMap<>();
    Map<Product, BigDecimal> consolidatedRealQties = new HashMap<>();
    Map<Product, String> realRacks = new HashMap<>();

    List<InventoryLine> inventoryLineList = inventory.getInventoryLineList();

    if (inventoryLineList != null) {
      for (InventoryLine inventoryLine : inventoryLineList) {
        Product product = inventoryLine.getProduct();
        TrackingNumber trackingNumber = inventoryLine.getTrackingNumber();

        realQties.put(Pair.of(product, trackingNumber), inventoryLine.getRealQty());

        BigDecimal realQty = consolidatedRealQties.getOrDefault(product, BigDecimal.ZERO);
        realQty = realQty.add(inventoryLine.getRealQty());
        consolidatedRealQties.put(product, realQty);

        realRacks.put(product, inventoryLine.getRack());
      }
    }

    List<StockLocationLine> stockLocationLineList =
        inventory.getStockLocation().getStockLocationLineList();

    if (stockLocationLineList != null) {
      for (StockLocationLine stockLocationLine : stockLocationLineList) {
        Product product = stockLocationLine.getProduct();
        BigDecimal realQty = consolidatedRealQties.get(product);
        if (realQty != null) {
          stockLocationLine.setLastInventoryRealQty(realQty);
          stockLocationLine.setLastInventoryDateT(
              inventory.getValidatedOn().atStartOfDay().atZone(ZoneOffset.UTC));
        }

        String rack = realRacks.get(product);
        if (rack != null) {
          stockLocationLine.setRack(rack);
        }
      }
    }

    List<StockLocationLine> detailsStockLocationLineList =
        inventory.getStockLocation().getDetailsStockLocationLineList();

    if (detailsStockLocationLineList != null) {
      for (StockLocationLine detailsStockLocationLine : detailsStockLocationLineList) {
        Product product = detailsStockLocationLine.getProduct();
        TrackingNumber trackingNumber = detailsStockLocationLine.getTrackingNumber();
        BigDecimal realQty = realQties.get(Pair.of(product, trackingNumber));
        if (realQty != null) {
          detailsStockLocationLine.setLastInventoryRealQty(realQty);
          detailsStockLocationLine.setLastInventoryDateT(
              inventory.getValidatedOn().atStartOfDay().atZone(ZoneOffset.UTC));
        }

        String rack = realRacks.get(product);
        if (rack != null) {
          detailsStockLocationLine.setRack(rack);
        }
      }
    }
  }

  /**
   * Generate a stock move from an inventory.
   *
   * @param inventory a realized inventory.
   * @param isEnteringStock whether we want to create incoming or upcoming stock move of this
   *     inventory.
   * @return the generated stock move.
   * @throws AxelorException
   */
  public StockMove generateStockMove(Inventory inventory, boolean isEnteringStock)
      throws AxelorException {

    StockLocation toStockLocation;
    StockLocation fromStockLocation;
    Company company = inventory.getCompany();
    if (isEnteringStock) {
      toStockLocation = inventory.getStockLocation();
      fromStockLocation =
          stockConfigService.getInventoryVirtualStockLocation(
              stockConfigService.getStockConfig(company));
    } else {
      toStockLocation =
          stockConfigService.getInventoryVirtualStockLocation(
              stockConfigService.getStockConfig(company));
      fromStockLocation = inventory.getStockLocation();
    }

    String inventorySeq = inventory.getInventorySeq();

    LocalDate inventoryDate = inventory.getPlannedStartDateT().toLocalDate();
    LocalDate realDate = inventory.getValidatedOn();
    StockMove stockMove =
        stockMoveService.createStockMove(
            null,
            null,
            company,
            fromStockLocation,
            toStockLocation,
            realDate,
            inventoryDate,
            null,
            StockMoveRepository.TYPE_INTERNAL);

    stockMove.setName(inventorySeq);

    stockMove.setOriginTypeSelect(StockMoveRepository.ORIGIN_INVENTORY);
    stockMove.setOriginId(inventory.getId());
    stockMove.setOrigin(inventorySeq);

    for (InventoryLine inventoryLine : inventory.getInventoryLineList()) {
      generateStockMoveLines(inventoryLine, stockMove, isEnteringStock);
    }
    if (stockMove.getStockMoveLineList() != null && !stockMove.getStockMoveLineList().isEmpty()) {

      stockMoveService.plan(stockMove);
      stockMoveService.copyQtyToRealQty(stockMove);
      stockMoveService.realize(stockMove, false);
    }
    return stockMove;
  }

  /**
   * Generate lines for the given stock move. Depending if we are creating an incoming or outgoing
   * stock move, we only create stock move line with positive quantity.
   *
   * @param inventoryLine an inventory line
   * @param stockMove a stock move being created
   * @param isEnteringStock whether we are creating an incoming or outgoing stock move.
   * @throws AxelorException
   */
  protected void generateStockMoveLines(
      InventoryLine inventoryLine, StockMove stockMove, boolean isEnteringStock)
      throws AxelorException {
    Product product = inventoryLine.getProduct();
    TrackingNumber trackingNumber = inventoryLine.getTrackingNumber();
    BigDecimal diff = inventoryLine.getRealQty().subtract(inventoryLine.getCurrentQty());
    if (!isEnteringStock) {
      diff = diff.negate();
    }
    if (diff.signum() > 0) {
      BigDecimal avgPrice;
      StockLocationLine stockLocationLine =
          stockLocationLineService.getStockLocationLine(stockMove.getToStockLocation(), product);
      if (stockLocationLine != null) {
        avgPrice = stockLocationLine.getAvgPrice();
      } else {
        avgPrice = BigDecimal.ZERO;
      }

      StockMoveLine stockMoveLine =
          stockMoveLineService.createStockMoveLine(
              product,
              product.getName(),
              product.getDescription(),
              diff,
              avgPrice,
              avgPrice,
              product.getUnit(),
              stockMove,
              StockMoveLineService.TYPE_NULL,
              false,
              BigDecimal.ZERO);
      if (stockMoveLine == null) {
        throw new AxelorException(
            inventoryLine.getInventory(),
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.INVENTORY_7)
                + " "
                + inventoryLine.getInventory().getInventorySeq());
      }
      if (trackingNumber != null && stockMoveLine.getTrackingNumber() == null) {
        stockMoveLine.setTrackingNumber(trackingNumber);
      }
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void cancel(Inventory inventory) throws AxelorException {
    List<StockMove> stockMoveList =
        stockMoveRepo
            .all()
            .filter("self.originTypeSelect = :originTypeSelect AND self.originId = :originId")
            .bind("originTypeSelect", StockMoveRepository.ORIGIN_INVENTORY)
            .bind("originId", inventory.getId())
            .fetch();

    for (StockMove stockMove : stockMoveList) {
      stockMoveService.cancel(stockMove);
    }

    inventory.setStatusSelect(InventoryRepository.STATUS_CANCELED);
  }

  @Transactional(rollbackOn = {Exception.class})
  public Boolean fillInventoryLineList(Inventory inventory) throws AxelorException {

    if (inventory.getStockLocation() == null) {
      throw new AxelorException(
          inventory,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.INVENTORY_1));
    }

    this.initInventoryLines(inventory);

    List<? extends StockLocationLine> stockLocationLineList = this.getStockLocationLines(inventory);

    if (stockLocationLineList != null) {
      Boolean succeed = false;
      for (StockLocationLine stockLocationLine : stockLocationLineList) {
        if (stockLocationLine.getTrackingNumber()
            == null) { // if no tracking number on stockLocationLine, check if there is a tracking
          // number on the product
          long numberOfTrackingNumberOnAProduct =
              stockLocationLineRepository
                  .all()
                  .filter(
                      "self.product = ?1 AND self.trackingNumber IS NOT null AND self.detailsStockLocation = ?2",
                      stockLocationLine.getProduct(),
                      inventory.getStockLocation())
                  .count();

          if (numberOfTrackingNumberOnAProduct != 0) { // there is a tracking number on the product
            continue;
          }
        }
        inventory.addInventoryLineListItem(this.createInventoryLine(inventory, stockLocationLine));
        succeed = true;
      }
      inventoryRepo.save(inventory);
      return succeed;
    }
    return null;
  }

  public List<? extends StockLocationLine> getStockLocationLines(Inventory inventory) {

    String query = "(self.stockLocation = ? OR self.detailsStockLocation = ?)";
    List<Object> params = new ArrayList<>();

    params.add(inventory.getStockLocation());
    params.add(inventory.getStockLocation());

    if (inventory.getExcludeOutOfStock()) {
      query += " and self.currentQty > 0";
    }

    if (!inventory.getIncludeObsolete()) {
      query += " and (self.product.endDate > ? or self.product.endDate is null)";
      params.add(inventory.getPlannedEndDateT().toLocalDate());
    }

    if (inventory.getProductFamily() != null) {
      query += " and self.product.productFamily = ?";
      params.add(inventory.getProductFamily());
    }

    if (inventory.getProductCategory() != null) {
      query += " and self.product.productCategory = ?";
      params.add(inventory.getProductCategory());
    }

    if (inventory.getProduct() != null) {
      query += " and self.product = ?";
      params.add(inventory.getProduct());
    }

    if (!Strings.isNullOrEmpty(inventory.getFromRack())) {
      query += " and self.rack >= ?";
      params.add(inventory.getFromRack());
    }

    if (!Strings.isNullOrEmpty(inventory.getToRack())) {
      query += " and self.rack <= ?";
      params.add(inventory.getToRack());
    }

    return stockLocationLineRepository.all().filter(query, params.toArray()).fetch();
  }

  public InventoryLine createInventoryLine(
      Inventory inventory, StockLocationLine stockLocationLine) {

    return inventoryLineService.createInventoryLine(
        inventory,
        stockLocationLine.getProduct(),
        stockLocationLine.getCurrentQty(),
        stockLocationLine.getRack(),
        stockLocationLine.getTrackingNumber());
  }

  public void initInventoryLines(Inventory inventory) {

    if (inventory.getInventoryLineList() == null) {
      inventory.setInventoryLineList(new ArrayList<InventoryLine>());
    } else {
      inventory.getInventoryLineList().clear();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public MetaFile exportInventoryAsCSV(Inventory inventory) throws IOException {

    List<String[]> list = new ArrayList<>();

    for (InventoryLine inventoryLine : inventory.getInventoryLineList()) {
      String[] item = new String[9];
      String realQty = "";

      item[0] = (inventoryLine.getProduct() == null) ? "" : inventoryLine.getProduct().getName();
      item[1] = (inventoryLine.getProduct() == null) ? "" : inventoryLine.getProduct().getCode();
      item[2] =
          (inventoryLine.getProduct() == null)
              ? ""
              : ((inventoryLine.getProduct().getProductCategory() == null)
                  ? ""
                  : inventoryLine.getProduct().getProductCategory().getName());
      item[3] = (inventoryLine.getRack() == null) ? "" : inventoryLine.getRack();
      item[4] =
          (inventoryLine.getTrackingNumber() == null)
              ? ""
              : inventoryLine.getTrackingNumber().getTrackingNumberSeq();
      item[5] = inventoryLine.getCurrentQty().toString();
      if (inventoryLine.getRealQty() != null
          && inventory.getStatusSelect() != InventoryRepository.STATUS_DRAFT
          && inventory.getStatusSelect() != InventoryRepository.STATUS_PLANNED) {
        realQty = inventoryLine.getRealQty().toString();
      }
      item[6] = realQty;
      item[7] = (inventoryLine.getDescription() == null) ? "" : inventoryLine.getDescription();

      String lastInventoryDateTString = "";
      StockLocationLine stockLocationLine =
          stockLocationLineService.getStockLocationLine(
              inventory.getStockLocation(), inventoryLine.getProduct());
      if (stockLocationLine != null) {
        ZonedDateTime lastInventoryDateT = stockLocationLine.getLastInventoryDateT();
        lastInventoryDateTString =
            lastInventoryDateT == null
                ? ""
                : lastInventoryDateT.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      }
      item[8] = lastInventoryDateTString;
      list.add(item);
    }

    Collections.sort(
        list,
        new Comparator<String[]>() { // sort the list by code product
          @Override
          public int compare(String[] strings, String[] otherStrings) {
            return strings[1].compareTo(otherStrings[1]);
          }
        });

    String fileName = I18n.get("Inventory") + "_" + inventory.getInventorySeq() + ".csv";
    String filePath = AppSettings.get().get("file.upload.dir");
    Path path = Paths.get(filePath, fileName);
    File file = path.toFile();

    log.debug("File Located at: {}", path);

    String[] headers = {
      I18n.get("Product Name"),
      I18n.get("Product Code"),
      I18n.get("Product category"),
      I18n.get("Rack"),
      I18n.get("Tracking Number"),
      I18n.get("Current Quantity"),
      I18n.get("Real Quantity"),
      I18n.get("Description"),
      I18n.get("Last Inventory date")
    };
    CsvTool.csvWriter(filePath, fileName, ';', '"', headers, list);

    try (InputStream is = new FileInputStream(file)) {
      return Beans.get(MetaFiles.class).upload(is, fileName);
    }
  }

  public List<StockMove> findStockMoves(Inventory inventory) {
    return stockMoveRepo
        .all()
        .filter(
            "self.originTypeSelect = ?1 AND self.originId = ?2",
            StockMoveRepository.ORIGIN_INVENTORY,
            inventory.getId())
        .fetch();
  }

  public String computeTitle(Inventory entity) {
    return entity.getStockLocation().getName()
        + (!Strings.isNullOrEmpty(entity.getDescription())
            ? "-" + StringUtils.abbreviate(entity.getDescription(), 10)
            : "");
  }
}

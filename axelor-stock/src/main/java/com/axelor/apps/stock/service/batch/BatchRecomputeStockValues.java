package com.axelor.apps.stock.service.batch;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.batch.BatchStrategy;
import com.axelor.apps.stock.db.Inventory;
import com.axelor.apps.stock.db.InventoryLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs recalculation of values for all StockLocationLine. Cannot be @{@link
 * com.google.inject.Singleton} because of AbstractBatch in inheritance chain.
 */
public class BatchRecomputeStockValues extends BatchStrategy {
  private static final int WINDOW_SIZE = 50;

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private StockLocationLineService locationLineService;
  private StockLocationRepository locationRepository;
  private ProductRepository productRepository;

  @Inject
  public BatchRecomputeStockValues(
      StockLocationLineService locationLineService,
      StockLocationRepository locationRepository,
      ProductRepository productRepository) {
    this.locationLineService = locationLineService;
    this.locationRepository = locationRepository;
    this.productRepository = productRepository;
  }

  @Override
  protected void process() {
    recomputeStandardLines();
    checkPoint();
    recomputeDetailsLines();
    checkPoint();
  }

  @SuppressWarnings("WeakerAccess") // protected needed for @Transactional
  @Transactional
  protected void recomputeStandardLines() {
    // Cleanup every existing entries, allowing us to also update lines that shouldn't be there
    JPA.em()
        .createQuery(
            "UPDATE StockLocationLine SET avgPrice = 0, currentQty = 0, lastInventoryDateT = NULL, lastInventoryRealQty = 0, rack = NULL, reservedQty = 0, version = version + 1 WHERE detailsStockLocation is null")
        .executeUpdate();

    // FIXME Need to handle the moveline units & reserved quantity
    // Get all information about stock, by product and location. Real quantity is defined as the
    // delta input - output on REALIZED stock moves, futureQuantity takes PLANNED and REALIZED moves
    // into account. Reserved quantity is the delta of the reserved quantities of output - input of
    // PLANNED stock moves (see
    // com.axelor.apps.supplychain.service.StockLocationLineServiceSupplychainImpl.updateLocation,
    // planned is current:false, future:true, REALIZED is current:true,future:true).
    @SuppressWarnings("unchecked")
    Query q =
        JPA.em()
            .createQuery(
                "select new map(product as product, location as location, "
                    + "COALESCE(SUM(CASE WHEN move.statusSelect <> :realizedStatus THEN 0.0 WHEN move.fromStockLocation = location THEN -line.realQty ELSE line.realQty END), 0.0)  as realQuantity,"
                    + "COALESCE(SUM(CASE WHEN move.statusSelect <> :realizedStatus AND move.statusSelect <> :plannedStatus THEN 0.0 WHEN move.fromStockLocation = location THEN -line.qty ELSE line.qty END), 0.0) as futureQuantity,"
                    + "MAX(CASE WHEN move.statusSelect <> :plannedStatus THEN null ELSE move.estimatedDate END) as lastFutureQuantity, "
                    + "CASE "
                    + "WHEN COALESCE(SUM(CASE WHEN move.statusSelect <> :realizedStatus OR move.fromStockLocation = location THEN 0 ELSE line.realQty END), 0) = 0 THEN 0.0 "
                    + "ELSE COALESCE("
                    + "SUM(line.realQty * CASE WHEN move.statusSelect <> :realizedStatus OR move.fromStockLocation = location THEN 0 ELSE line.unitPriceUntaxed END)"
                    + " / "
                    + "SUM(CASE WHEN move.statusSelect <> :realizedStatus OR move.fromStockLocation = location THEN 0 ELSE line.realQty END), 0.0 ) END as avgPrice) "
                    // + ", SUM(CASE WHEN move.statusSelect <> :plannedStatus THEN 0 WHEN
                    // move.fromStockLocation = location THEN line.reserved_qty ELSE
                    // -line.reserved_qty END) as reservedQuantity "
                    + "FROM StockLocation location, "
                    + "StockMove move "
                    + "JOIN move.stockMoveLineList line "
                    + "JOIN line.product product "
                    + "WHERE (move.fromStockLocation = location OR move.toStockLocation = location) "
                    + "AND move.fromStockLocation <> move.toStockLocation "
                    + "AND location.typeSelect <> :virtualLocation "
                    + "AND move.statusSelect <> :canceledStatus "
                    + "AND ((move.statusSelect = :realizedStatus AND move.realDate <= :today) OR (move.statusSelect = :plannedStatus AND move.estimatedDate <= :today))"
                    + "AND product.productTypeSelect = :storableType "
                    + "GROUP BY product, location "
                    + "ORDER BY product.code, location.id")
            .setParameter("realizedStatus", StockMoveRepository.STATUS_REALIZED)
            .setParameter("plannedStatus", StockMoveRepository.STATUS_PLANNED)
            .setParameter("canceledStatus", StockMoveRepository.STATUS_CANCELED)
            .setParameter("virtualLocation", StockLocationRepository.TYPE_VIRTUAL)
            .setParameter("today", LocalDate.now())
            .setParameter("storableType", ProductRepository.PRODUCT_TYPE_STORABLE);
    int offset = 0;
    List<Map<String, Object>> stocks;

    do {
      stocks = q.setFirstResult(offset).setMaxResults(WINDOW_SIZE).getResultList();

      for (Map<String, Object> row : stocks) {
        final Product product = (Product) row.get("product");
        final StockLocation location = (StockLocation) row.get("location");
        final Double realQuantity = (Double) row.get("realQuantity");
        final LocalDate lastFutureQuantity = (LocalDate) row.get("lastFutureQuantity");
        final Double futureQuantity = (Double) row.get("futureQuantity");
        final Double avgPrice = (Double) row.get("avgPrice");
        log.info(
            "Stock for product #{} ({}) at location #{} ({}): {} (avg price: {}), future quantity: {}, last future quantity: {}",
            product.getId(),
            product.getCode(),
            location.getId(),
            location.getName(),
            realQuantity,
            avgPrice,
            futureQuantity,
            lastFutureQuantity);
        StockLocationLine line =
            locationLineService.getOrCreateStockLocationLine(location, product);
        line.setCurrentQty(BigDecimal.valueOf(realQuantity).setScale(10, RoundingMode.HALF_EVEN));
        line.setAvgPrice(BigDecimal.valueOf(avgPrice).setScale(10, RoundingMode.HALF_EVEN));
        line.setFutureQty(BigDecimal.valueOf(futureQuantity).setScale(10, RoundingMode.HALF_EVEN));
        line.setLastFutureStockMoveDate(lastFutureQuantity);
      }
      offset += WINDOW_SIZE;
    } while (stocks.size() == WINDOW_SIZE);

    // Get information from last inventory on each product. A product can be present several times
    // on the same inventory but only the last recorded line is taken into account (to be consistent
    // with inventory processing)
    q =
        JPA.em()
            .createQuery(
                "select new map(product as product, inventory as inventory, inventoryLine as inventoryLine, stockLocation as stockLocation)"
                    + "FROM InventoryLine inventoryLine "
                    + "JOIN inventoryLine.product product "
                    + "JOIN inventoryLine.inventory inventory "
                    + "JOIN inventory.stockLocation stockLocation "
                    + "LEFT JOIN InventoryLine newerLine on (newerLine.inventory = inventory AND newerLine.product = inventoryLine.product AND newerLine.realQty IS NOT null AND newerLine.id > inventoryLine.id)"
                    + "WHERE inventoryLine.realQty IS NOT null AND newerLine IS null AND NOT EXISTS ("
                    + "SELECT innerLine "
                    + "FROM InventoryLine innerLine "
                    + "JOIN innerLine.inventory innerInventory "
                    + "WHERE innerLine.product = inventoryLine.product AND innerInventory.dateT <= :today "
                    + "AND innerLine.realQty IS NOT null AND innerInventory.dateT >= inventory.dateT AND innerInventory.id > inventory.id"
                    + ")"
                    + "ORDER BY product.code, inventory.stockLocation.id")
            .setParameter("today", ZonedDateTime.now());

    offset = 0;
    List<Map<String, Object>> inventories;

    do {
      inventories = q.setFirstResult(offset).setMaxResults(WINDOW_SIZE).getResultList();

      for (Map<String, Object> row : inventories) {
        final Product product = (Product) row.get("product");
        final Inventory inventory = (Inventory) row.get("inventory");
        final InventoryLine inventoryLine = (InventoryLine) row.get("inventoryLine");
        final StockLocation stockLocation = (StockLocation) row.get("stockLocation");
        log.info(
            "Last inventory info for product #{} ({}) on stock location #{} ({}): date: {}, rack: {}, quantity: {}",
            product.getId(),
            product.getCode(),
            stockLocation.getId(),
            stockLocation.getName(),
            inventory.getDateT(),
            inventoryLine.getRack(),
            inventoryLine.getRealQty());

        final StockLocationLine line =
            locationLineService.getOrCreateStockLocationLine(stockLocation, product);
        line.setLastInventoryDateT(inventory.getDateT());
        line.setRack(inventoryLine.getRack());
        line.setLastInventoryRealQty(inventoryLine.getRealQty());
      }
      offset += WINDOW_SIZE;
    } while (inventories.size() == WINDOW_SIZE);

    // Cleanup inconsistent records
    JPA.em()
        .createQuery(
            "DELETE StockLocationLine outerLine WHERE outerLine IN (SELECT line FROM StockLocationLine line JOIN line.product product WHERE product.productTypeSelect <> :storableType)")
        .setParameter("storableType", ProductRepository.PRODUCT_TYPE_STORABLE)
        .executeUpdate();
  }

  @Transactional
  protected void recomputeDetailsLines() {}
}

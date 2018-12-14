# Changelog

## [Unreleased 5.1.1]
## Features
- Subrogation Release : add change tracking on status and add new fields.
- MO printing : new design
- MRP : set timeout for Calculation Process from App SupplyChain.when the process take more time that allowed, stop it and update MRP status to "Time limit exceeded"

## Improvements
- FINANCIAL ACCOUNT : New boolean 'Analytic Distribution Authorized' added to make Analytic fields required on Invoice Line.
- COSTSHEET : add new Field Currency and Fix Information on CoshSheet Report Printing
- Sale Invoice & Refund : Add factor viewer.

## Bug Fixes
-SEQUENCES : Fix panelSide Tips problem

## [5.1.0] - 2018-12-13
## Features
- Production: billOfMaterial set the BOM as default on product (button and viewer)
- MARKETING CAMPAIGN : Generate event to the team and salesman in charge depending of the partner or leads.
- Sequences : Add a selection option to use letter sequences.
- Manuf Order: add status in BOM and create manufacturing order only with "Applicable" BOM
- Prod Process: add status and create manufacturing order only with "Applicable" prod process
- Add ISPM15 feature on Picking stock move report.
- New menu entry for calendars and a new sharing system.
- Product description can be copied to Invoice/Order/Purchase/StockMove line.
- Add Wiki tab in project view form and disable project edit from Wiki view.
- Automatic mail on stock move planning.
- New printing: certificate of conformity on stock move.
- Purchases : possibility to set purchases as fixed assets.
- Add a Job to update the currency conversion rates from a scheduler.
- Add reconcile group which is a group of move lines. It also contains the reconciles between the lines.
- Ticket: add mail subject field in order to choose the default subject for each mail sent to followers.
- Balance on top of Partner view become configurable for use specific balance
- Project: Can now generate project with task templates by product.
- Add abstract class and interface for time model: "Timer" and "TimerHistory".
- Add Timer to TeamTask.
- Add Timer to Ticket.
- Stock location line : add two buttons to see 'Planned' and 'Realized' stock move details for the current product and location.
- Machine type added for the machine.
- Selection added for priority of manufacturing order.
- Payment schedule line: management of direct debit rejection.
- New stock location configuration for quality control.
- Create multiple supplier stock move when purchasing products requiring a quality check and normal products.
- Selection added for priority of manufacturing order.
- Add currency in Accounting reports and filter all results with it.
- New field `launchQty` on production process.
- Fill `qty` from `launchQty` in manuf order on filling prod process.
- Declaration of exchanges of services and declaration of services
- Project : project assignment on Sale order, Purchase order and Invoices.
- PROJECT : Change project planning to project planning time with some extra fields.
- PROJECT : Change business project reporting.
- Project view enhancements
- TIMELINE : Generate lines from Project Planning
- Pack Price Select and Qty update from packLine to subLine.
- Implement PackLine and SubLine for InvoiceLine and StockMoveLine.
- TIMELINE : Generate lines from Project Planning
- Account Moves: ability to set currency, currency rate and currency amount for manual moves.
- PROJECT : Add new o2m 'manualElementList' and display it on financial report.
- PROJECT : Add translation of financial report.
- Manuf Order: show in toConsumeProdProductList if there is product missing in stock.
- Manuf Order: show in diff O2M the planned and real quantities.
- Add some checkboxes to identify which stock location can be selected on sale order, purchase order, manuf order.
- Business Project : Fill default values from Project while generating the sale Quotation.
- Business Project : Enhancement of task and project view.
- Project : Change invoicing project view.
- Stock Move : Add Wizard for tracking number
- Production: new report showing raw material requirements.
- Business Project : add new m2m 'teamTaskSet' in invoicing project.
- Add translation of project view.
- New boolean for manuf order printing to choose whether planned date needs to be showed.
- Project Business Generator: when generating phases by lines, product's task templates are added to each phase.
- Project: Move tasks menu entry to root menu and move tasks historical menu entry in historical submenu.
- Product : Remove 'isPack' field and update productTypeSelect selection to add it.
- STOCK MOVE : add column availability qty of product on grid view displayed in popup when click on select button on m2o product.
- Project : add 'Task invoicing' table in financial report.
- SALE ORDER : add column availability qty of product on grid view displayed in popup when click on select button on m2o product.
- SaleOrder : modify the invoice generation from Sale Order.
- Manuf Order : Apply domain on clientPartner and saleOrder & Assign them while generating manufOrder from saleOrder.
- PROJECT : add translations on project view.
- STOCK MOVE : add column availability qty of selected product on grid view displayed in popup when click on select button on m2o tracking number.
- New field workshop on production process, bill of material and manufacturing order.
- Manufacturing order sequence is now configured in company config, and can be configured by workshop.
- Studio: Add description field in workflow
- Group menu assistant: Add support for roles management.
- Stock Move : add three transient fields 'availableQty','availableQtyForProduct' and 'availableStatus' to know the availability of the product on stock move line.
- Studio: Modified custom editor with new design
- Product : add unique constraint on product code.
- Business Project : Link project or phase and price list to invoice and invoice line generated from invoicing project.
- Stock move : display availability status and missing qty on stock move line grid.
- Timesheet : add menu 'All timesheets'.
- Partner : Modify fullName value calculation to add partnerSeq and update reports accordingly.
- Sale order line : add 'Partially invoiced' tag and invoiced tag depends on 'amountInvoiced' and 'exTaxTotal'.
- Stock Location Line : display stock avgPrice with as much decimal digit as specified in config.
- Stock : Move the stock Location menu to root menu from configration menu.
- Add a button on studio process tracking to display related workflow dashboard.
- Stock Location Line : Add Button to display detailed stock location lines with tracking number.
- Stock move : enable the changing tracking on stock move form.
- Purchase Order Line : add dummy boolean field 'Filter on supplier' to filter the product on supplier catelog.
- PRODUCT : add 'Standard' and 'Approval' fields and 'ProductApproval' object in production module.
- Partner : Add simpleFullName field.
- PRODUCT : add new button 'Reserved qty' to know the reserved quantity on product form.
- Sale Order : add new large string field 'Production note' and display it on picking stock move report.
- BillOfMaterial : Add new large field 'Note'.
- BANK RECONCILIATION : Fully rework the bank statement process.
- Stock Location Line : Add new button 'Reserved qty' to display the reserved quantity on stock location line form.
- Product : enable massUpdate on usedInDEB.
- Account : Add a viewer to display the balance and add button to display all account move lines linked to the account on account form.
- StockLocationLine : Add dashlet to know prosuct quantities in other stock locations.
- Stock Move Line: add button to update reserved qty in stock location line.
- Bank Statement : Add action to BankPaymentBatch.
- Add support of Customer catalog and display customer product code and name on SaleOrder, StockMove and Invoice reports.
- Add WAP value in StockLocation report
- Contract: Removed wrong demo data and added demo data for app configuration.
- Analytic move line : accountType added on grid view.
- Stock Move : Attach conformity certificate to record.
- Stock Rules : Add massUpdate on 'Stock location', 'Product', 'Refill type' and 'Min. Qty'.
- Inventory : Add filter on two new string fields on form when click on 'Fill inventory' button.
- Add direct order stock location.
- Inventory:Allow to do an inventory for one product.
- Extra Hours : Add new field 'totalQty' to compute extra hours line.
- ACCOUNT MANAGEMENT : Allow to define all elements (account, tax, analytic distribution) per product family and only override one element in product account management.
- STOCK LOCATION : On StockLocationLine, add a dashlet to display all StockRules linked to the current product and current stock location.
- Account Move, Move Line : Add change tracking.
- Invoice : Allow to generate invoice without payment mode and condition.
- ACCOUNT MOVE : Removed readonlyIf from MoveLine-form and made editable form even after validated status
- Invoice : Add new panel-dashlet of StockMove in new panel 'Delivery'
- Purchase : Add Suppier Catalog management configurtion on purchase app .
- LOGISTICAL FORM : action-validate added for mass validation on-save.
- STOCK MOVE : Display specificPackage field on Stock Move generated from Sale Order.
- STOCKMOVE : External Reference field added in StockMove Report.
- STOCK MOVE :	Add a new option in Stock app : "Separate the shipment operations".Add new field Avilability Request in Stock Move and Add new Menu entries for this.
- AnalyticMoveLine : Displayed 'accountType' in right of column 'Analytic Account' in grid view and also added to analytic-move-line-distribution-form
- INVOICE : Customer tax nbr field added for the invoices based on sale order.
- MOVELINE : Taxline display on accountType='tax', 'immobilisation' and moveline grid, also fill taxRate and taxCode accordingly onSave and update to daybook.
- Product : Add a decimal field 'economicManufOrderQty' and product.economicManufOrderQty in ManufOrder form view and its translation.
- Account MOVE Line:Add new field originDate.On FEC move line export,use the originDate instead of date field.
- ManufOrder : Add new many-to-one field 'unit' and set its value from billOfMaterial unit and also added to report.
- Tracking Number : change in stock move and stock move line based on tracking number configuration.
- StockMove : Include sub stock location's lines in Report.
- Notification : Improve notification management.
- Manage origin date on supplier invoice. This date is used for compute due date and is used in account move line to fill the origin date.
- Fill analytic template on move line when we generate the accounting move from an invoice and use it as a key to consolidate the generated move lines.
- Fill analytic template on invoice line when we generate the invoice from a sale or purchase order or stock move linked to a sale or purchase order.
- STOCK MOVE : filter on fromStockLocation
- ManufOrder : Track the Manufacture Order.
- STOCK MOVE : Report footer Modify
- SALEORDER : Generate stock move per estimated delivery date
- PURCHASEORDER : Generate stock move per estimated delivery date
- INVOICE LINE : New option "Analytic template required on InvoiceLine" added in Account App.
- Partner : Add filter on partner depending on company.
- STOCK MOVE : Report mass printing
- PURCHASE ORDER : Manage received qty per line
- MRP : improve runtime for huge database
- MRP : generate proposal for product that stock qty is less than stock rule min qty even if there is no other out movement.
- MRP : if the sale order or purchase order is not delivered, if the date is in the past, now we take it into account with today date.
If not date is filled, added an option to choose or not to include it for sale order, purchase order and manuf order.
- STOCK MOVE : mass printing for picking stock move report.
- CONTRACT : classic contract lines can now be ordered with drag and drop, and generating an invoice from a contract will keep the order in the invoice lines.
- Add Fixed Asset Management feature.
- Stock Move : Add grid for sale order.
- SALEORDERLINE : stock move line list
- Add requested and allocated quantity in stock. This feature allows to reserve stocks that are currently not available, but will allocate the quantity when the stock is available.
- Add configuration to allocate the quantity in stock automatically on in stock move realization.
- Bank Reconciliation : Add Report for Unreconciled Move Lines & Bank Statement.
- Supplier Invoice : Alert when invoice is already created for supplier invoice number.
- Accounting Move : Calculate the Credit , Debit & Balance amount for selected MoveLines.
- Added mass payment on supplier invoices and generate account move on bank order validation.
- Added an option according to the payment mode to automatically consolidate the bank order lines per partner
- Created CostSheet report.Added button to print Cost sheet report from Manufacturing order form.
- Added management of subscription moves with MoveTemplate object
- Batch : Add 'Production Batch : compute work in progress valuation' and generate PDF in production module.
- Stock move: add tracking for cancel reason.
- STOCK MOVES : tracking number wizard new button "Display available tracking numbers" (fr: "Afficher les lots disponibles").
- App Stock : Add new m2o field 'ecomonomicArea'.
- STOCKMOVE : mass printing for conformity certificate report
- CUT-OFF : Managed accounting cut-off batch for supplier and customer deliveries.
- Stock Move Line : Allow to update filterOnAvailableProducts from Main StockMove Panel.

## Improvements
- Move project resource menu entry to project configuration menu.
- Refactor TargetList and Campaign views to follow guidelines.
- Add Partner balance as a new Accounting report.
- Rework opportunity view
- Remove all question marks (?) from boolean fields names.
- Message : Run the process of send an email in a separate thread to avoid thread blocking.
- Project: Add new task from project form is now done with pop-up.
- Add a panel with Shipping comments in the PackagingList Report.
- Remove unneeded required attribute for decimal in accounting situation
- Rework TeamTask UI
- Production processes: display status in grid view.
- Rework Ticket UI.
- Tax configuration: display only necessary fields for tax account management.
- Hide delivery panel depending on the type of product and if the delivery process is enabled in sale order line form view.
- Refactor purchase order view.
- Rework project view
- Set project on invoice and invoice line when generating it from sale order and stock move.
- Set project on purchase order and purchase order lines when generating it from a sale order.
- Improve account management views.
- Rework ManufOrder printing.
- Add new Origin field in stock moves and remove fields sale order and purchase order.
- Added nameColumn for ExpenseLine and ProjectPlanningTime.
- Change unique constraints in analytic account move line.
- ManufOrder: Change quantity also in stock move on updating planned quantity.
- ManufOrder report: larger "name" column on to consume product table.
- Project: Rename "Dashboard" menu entry to "My projects" and move it to "Reporting" menu entry.
- Project: Reorganization of Resource Booking form view.
- Tweaks on manuf order report.
- Rework Lunch voucher management view.
- Improve Expense form view.
- Rework CRM events view.
- Change purchase requests menu.
- Rework Purchase request view.
- Rework Target and Target configuration views in CRM.
- Improve CRM menu entries.
- Use editor for prod process line descriptions.
- On creating stock move line, get the price from orders instead of computing it.
- Improve exception handling in axelor-studio module.
- Improve organization of AppBuilder menu
- Manuf Order : Add new tab - Note.
- Account Move Report : Add reconcileGroup column in Account Move Lines.
- Update stock when modifying quantities in manufacturing order using wizard.
- Add App related data in Data Back & restore functionality.
- Data Backup : export the record sorted by Model fullName.
- Improve opportunity view.
- HR : Rename and reorder of timesheet menu entry and replace toolbar button to toolbar menu in grid.
- Contact : Add new large field - timeSlot.
- Use price from stock location when making an internal stock move.
- Add Partner fullName field in MRP (grid and report)
- Add a new option in Stock app to require to fill conformity for products ticked "Control on Receipt"
- Improve PickingStockMove report : add some static informations
- Partner : Modify actions to compute simpleFullName & fullName.
- Customer catalog: added option to enable/disable customer catalog management on sales app.
- Improve PurchaseOrder report : add company invoice and stock address, buyer, desired delivery date...
- Product : Improved StockLocationLine Form which is opened from Product Form.
- Production App : Add workshop configuration.
- Account management views : add CanEdit = false on some M2O fields (company, account, tax...) and allow to create new analytic distribution template directly.
- Purchase Order:Allow to cancel a validated purchase order.If a stock move is already generated, cancel it.
- PURCHASE ORDER: Updated Purchase Order Report using PDFMergeTool
- Product : add new boolean to manage stock in stockLocation.
- PARTNER : grid and card view
- Raw Material Requirement : Delete canEdit on field 'company' in raw-material-requirement-form.
- Added french translation for 'usable on sale order','usable on purchase order','usable on production'.
- PURCHASE ORDER : Supplier Box field and order date added and Printing on Purchase Order Report.
- Manufacturing Orders : Improvement in Manufacturing Order Report.
- Logistical form : allow to edit some field on state 'Carrier validated' and enable change tracking feature.
- Picking stock move : add two option to enable to display the tracking numbers and display the barcode column.
- Enable mass update feature on ProdProcess on stock location fields
- Allow to edit invoicing and delivery address on sale order being edited
- Enable mass update feature on Account object
- Add an option to display customer or supplier partner seq on invoice printing
- Allow to select an external stock location on purchase order
- Display origin and analytic template on move line grid
- Improve Stock move report : add note and partner seq
- Add an option to automatically confirm the bank orders generated from an invoice payment
- Use supplier invoice number instead of invoiceId on bank order automatically generated from an invoice payment on supplier invoice.
- Inventory:InventoryLine add Fields gap and gapValue and change default real quantity.
- Managed mass fields on 3 decimal digits at least, and improve mass conversion.
- Display estimated delivery dates on sale order printing.
- StockMove report : Add an option in Stock config to display the details of line or consolidate per product code, name and unit.
Also in case of aggregation, we have to concatenate the tracking number list and the description of lines
- StockMove report : Display rack of from and to stock location (for internal and ingoing stock move), display invoicing and delivery address for outgoing stock move.
Only display line where quantity is greater than 0.
- Add a specific not for invoice on company bank details
- Code improvements on creating a new purchase order.
- LOGISTICAL FORM : Add the stock location and filter elements on it. Also, use the stock location address in report header instead of company address
- PURCHASE ORDER : Allow to edit delivery informations on validated purchase order.
- Stock Move : Specify views for origin field.
- STOCK MOVE : Set avilableStatus in stockMoveLineList.
- Add 'stock unit' field on stock location line and add unit conversion.
- STOCK LOCATION : allow report to export in XLS
- JOB APPLICATION : Major updates in job application form view.
- JOB POSITION : Major updates in job position form view.
- SUBROGATION RELEASE : Add a link to the subrogation release on invoice in order to avoid to transmit many times the same invoice to the factor.
- Apply shipping coef on WAP price valuation for purchase stock move.
- Stock move mass invoicing: allow null values after the prompt.
- Improvement when updating stock move lines from product.
- Shipping coef: remove duplicated code.
- Sale Order: change confirmation date to datetime.
- Reservation date on stock move is now copied from confirmation date time on sale order.
- Tax report : Refactoring entirely the debit tax report (type 12) and display the move lines details.
- Accounting reports : disable page break interval to get only one tab on excel files
- EBICS partner : Add partner service for bank statement file format to manage specific bank codification.
- FIXED ASSET BATCH : Move Fixed asset Batch to Accounting batch.
- FILTER : Analytic distribution template filter by Company in Invoice, Sale Order, Purchase Order and Move.
- SUBROGATION RELEASE : allow to select a refund of an invoice already transmitted to the factor and validated after the transmission of the original invoice to the factor
- Remove planned stock move lines on cancel.
- WORKCENTER: add new field "Revaluation at actual prices" for machines duration in costsheets
- PRODUCT : New select 'Components valuation method' in Production Information.

## Bug Fixes
- Check duplicate : Apply manual filter before duplicate check and allow to open form view.
- Timesheet editor: Allows to delete a line when use unique product for the timesheet.
- Project Folder: The name is now display like title. Disable Project edit and new from Folder.
- Automatic mail default fields in stock move are now filled correctly on partner change.
- Project: Fix multiple bugs and improve views.
- Bill of material: remove duplicate toolbar button of 'Generate new version'.
- Event: Fix fill the "Guest" using email address when fill the partner or contact or lead.
- Studio: Fix email action.
- Project : Fix supplier orders and it's lines & invoices and it's lines link to the any project.
- Fix invoiced amount W.T update on sale and purchase order.
- Fix social network icons for employee
- Added new filter for invoice project (for SaleOrderLine, PurchaseOrderLine, and ExpenseLine o2m)
- Project: Fix financial report view.
- Fix stock move generation with pack lines from sale order
- Pack feature: Display only  pack line for partially invoicing and fix quantity for partially invoicing lines on pack lines.
- MRP: Fix concurrent modification exception.
- Business Project : Fix Financial report.
- Pack feature: Fix invoice subline generation and sequence.
- Pack feature: Fix type selection, pack type can be only selected based on product on Invoice and SaleOrder. Fixed invoice ventilation based on PackPriceSelect criteria.
- Project : Link project from purchase line while invoicing from stock move
- PROJECT : Fix team assignment on task creation.
- Sale order,purchase order : Fix demo data.
- Sale order,purchase order,invoice: Fix translation.
- Project : Make TimesheetLine and InvoiceLine readonly.
- Fix data backup heap size and crash issue.
- Studio: fix app builder and properties related issues.
- Fix data restore issue & Modify error log.
- PRODUCT : Fix pack line default quantity and forbid to select the parent product.
- Business Project : Fix discount compute when invoicing timesheet lines.
- Stock Move : Fix display email fields title.
- PROJECT : Fix translations.
- Sale Order, Sale Order Line : Fix margins with normal case and pack line case.
- Sale Order Line : Fix pack type translations.
- Sale Order : Fix NPE when we add a line and just enter the product name string field and enter unit price or qty.
- Stock Move : Fix when try to cancel an incoming realized stock move.
- Data backup : Fix binary field restore.
- Data backup : Include meta links.
- SaleOrder : Hide contactPartner if clientPartner is individual.
- Studio: Custom model editor: Fix some UI issues and forbid saving of model having relational fields without target model.
- SaleOrder : Add new selection field 'salemanSelect' to fill value of salemanUser.
- Manuf order: Fix sequence of operation details on report.
- Added missing translations for advanced exports in french version.
- Stock Rules : Fix enable change tracking on all fields.
- Supplychain App : Fix 'supplStockMoveMgtOnSO' field to 'supplStockMoveMgtOnPO'.
- StockMoveLine : specify Sequence while generating from Sale , Purchase , Manufacturing and Inventory.
- In manufacturing order, Stock move for produced product now uses the price from the product instead of from the costsheet.
- Manuf Order : Fix forbid to set a value equal to zero on wizard when we click on 'Updated planned quantities (Components and FP)' button.
- Manuf Order & ProdProcess : Added canEdit false on company and stockLocations fields and used widget SuggestBox for company.
- Invoice : fix fiscal position of account for partner account.
- Purchase Order : Fix minor form view corrections.
- Stock Move : Fix availability status language.
- PURCHASE ORDER : Division by zero
- PURCHASE ORDER : Completed Button french translation corrections and prompt added for it.
- Purchase Order : Fix move 'Generate control invoice' button from toolbar to sidebar panel on form view.
- Purchase ORDER : Fix issue when we click on Cancel receipt button and changed translation message for 'Cancel receipt'.
- Invoice Line : Fix client side crash when clear the product.
- Purchase Request:Set fields to canEdit false and Removed Field Delivered Quantity and changed cancel button accessible status.
- StockMove : Fix the 'back order' generation.
- PICKING STOCK MOVE :report  display the stockMoveLine.realQty instead of stockMoveLine.plannedQty
- PRODUCT : added field Country Of Origin in Packaging Panel and When we select manually a product on StockMoveLine fill CountryOfOrigin.
- SALEORDER : allow to change the payment condition and payment mode
- MRP : Fix Null pointer exception.
- Manufacturing Order:Unit price problem on consumedStockMoveLineList
- MANUF ORDER : NPE when no product is selected on producedStockMoveLineList line
- Disable average price update on cancelling stock move.
- Use real qty instead of planned qty on StockMove report.
- The current Manuf order are now displayed even if the end date is not filled.
- Fix reserved quantity field on generated stock move lines.
- Fix invoice generation from stock move.
- ACCOUNT : compute balance - id to load is required for loading
- Fixed bug where it would not be possible to close periods in the human resource module.
- Reset Bank order sender informations (date, reference, label, signatory user) when we merge some bank orders.
- Fixed issue on stock move printing when the same product and same tracking number is used in some lines. Now, we have removed the aggregation.
- Fixed issue with computation of undelivered qty on sale order lines.
- Added domain on company for saleOrderLineSet on MRP form
- MRP : Loop appears when we have a purchase proposal whith a supplier delivery duration that generate a propsal in the past, so excludes for new computation.
Now, In this case, we use the today date.
- MRP : When we select manually a sale order in filter, that doesn't have a stock location defined, we get a NPE.
So, now, we check if the saleOrder.stockLocation is the same stock location or one of sub stock location of the MRP stock location.
- Now keep the link to the sale order line or purchase order line when we split a stock move line.
- Now, the mass reconcile from move line list works for moveline without partner.
Moreover, the amount_remaining calculation on move line was wrong. Now we compute it when account is reconcile, not if the account is used for compute the partner balance.
- Fixed invoicing of partial deliveries in stock moves with several stock move lines with the same product.
- Added name attribute to all panels and buttons and also renamed.
- Panel rename issue
- Filter out direct order stock location in sale order with the boolean direct order false.
- Fixed some issues with analytic accounting management : manage currency, partial invoice, copy template, improve view management
- Stock Move Line : Hide Available stock display for virtual stock Location.
- STOCK MOVES : delete available status for supplier arrivals and customer returns
- MRP : disable sort on grid to avoid a misinterpretation of the result
- StockMoveLine : remove required constraint from trackingNumber if real qty not available.
- Manuf Order : Prevent Removing of order according to status & Archive it if status is Finished.
- Improve JPA cache management in batch bank statement.
- Product: use quantity to get shipping coef from supplier catalog.
- StockMove : stockMoveLine in readonly if company or fromStockLocation is null
- Partner: Address on grid view
- DataBackup : Handle Exception.

## [5.0.7] - 2018-12-13
## Features
- MRP : Display createdBy user and stockLocation.company in form and grid view.
- PRODUCT - Add new dashlet 'Where-used list' in 'Production information' tab.
- SaleOrder : Modify views to display company and stockLocation.
- PurchaseOrder : Modify views to display company and stockLocation.
- Invoice : Fill the 'companyBankDetails' from the 'Factor partner' if selected partner is 'Factorized customer' on sale invoice or refund.
- JOURNAL : Enable massUpdate feature
- SaleOrder & PurchaseOrder : Add prompt message on btn of "complete" manually sale or purchase order"
- Purchase Order Line : Removed field 'salePrice', 'saleMinPrice' and hilite done on it in  grid-view

## Improvements
- Show full name for products in Mrp report.
- Sale and Purchase order form: Remove edit from stockLocation field.
- Account: allow to have substitution & reverse charge on the same tax equivalence.
- PERIOD : allow to reopen a period if the fiscal year is not closed
- Currency conversion: allow to fetch today's rate on newly created conversion lines.
- Remove unecessary table in VAT on invoice report.
- Enable editable grid on AccountEquiv and added sort on AccountEquiv and TaxEquiv
- Added possibility to hide lines with currentQuantity and futureQuantity equal to 0 in stock locations.
- BankOrder : Specify limit for BankOrderLines.
- Purchase : Fix wrong translation of fields.
- PurchaseOrder : Disable edit button in Supplier-partner field
- Product : Fix hide the fields based on sellable and purchasable boolean fields on form view
- InvoiceLine : Added field product.code in grid 
- PARTNER : balance viewer
- SaleOrderLine : Added field product.code in grid
- Purchase order : Fill the product code and product name if a supplier catalog is defined but no tax is defined for the product
- Purchase order : Fill the tax line even if there is no supplier catalog
- Invoice : Fill the product code and product name even if tax or account are missing for the product
- FISCAL POSITION : When we select an toAccount, we should filter on the company of the fromAccount.
- INVENTORY : stock location filter
- INVOICE : Disabled canEdit attribute on paymentCondition,paymentMode,partner,saleOrder,contact,companyBankDetails,bankDetails,journal,partnerAccount.
- INVENTORY : Disabled canEdit attribute on Stock location, Product famlily, Product category,inventoryLineList.product
- StockMoveLine : Remove readonlyIf condition for tracking No. in form-view.
- GROUP : Enable massUpdate feature.
- Timesheet for manufacturing : display user in grid view, disable canEdit and canView and reorder the columns.
- Databackup : Include thread in backup & restore task.
- Use the char ';' for subrogation release export
- Allow to define large text for sale order and purchase order information fields on Partner.
- Replace Packaging with Packing in modules base, supplychain and stock.
- TALENT : View order of fields training and trainingSession altered in object Training Register.
- StockMove : checkExpirationDate At StockMove Realization done only if toStockLocation not virtual stock location except for inStockMove on ManufOrder.
- PRODUCT : Changed title of two fields(purchaseProductMultipleQtyList,saleProductMultipleQtyList) in advanced search and added fr translation for it.
- Production : Generate Unique Barcode on Duplicate Manufacture order.
- Factor : New Organization for debt recovery submenu.
- Inventory : Do not update average price when validating an inventory.
- Show delivery state in sale order grid view.
- Account: Reset fields on onchange action in Accounting Report. 
- Advance Export : Default value for selection translation.

## Bug Fixes
- Logistical Form : Fix display logo on report.
- Modify accounting export & path in demo-data & export to dms file if path not specified. 
- Accounting Export : Fix filter on move lines when click on 'See Move Lines' from 'Exported moves'.
- Fix manual invoice selection on subrogation release.
- Subrogation release: Fix the process on button 'Enter release in the accounts'. 
- Logistical Form : Fix the exception managed per TraceBack method when sequence is not defined.
- Fix accounting notification process.
- Subrogation Release : Fix the status reset and clear invoice set when copy.
- Subrogation Release : Fix throw exception and trace it when sequence is not defined.
- Fix demo data for configurator.
- MOVE : Fixed wrong message that is displayed when no sequence is defined for journal
- PERIOD : Get the right period per type (civil, payroll, fiscal)
- YEAR : sort per date DESC
- MOVE : display missing description field in moveLines
- MOVE : remove the wrong domain on company field that allow to select only the user active company
- BANKSTATEMENTAFB120 : DateTimeFormatter updated since we use java.date.time instead of joda.time
- Stock - FreightCarrierCustomerAccountNumber : Change index name of CarrierPartner.
- Fix menuitem's translations.
- Get correct stock location for intercompany (interco) orders.
- StockMove : make addresses updatable until it's not realized.
- Fix a bug where duplicated stock move line were shown in a dashlet.
- StockMove : Modify Locale for Picking Order report.
- StockMove : Fix partner to display on m2m grid view of mass invoicing of stock move.
- Fix @Transactional annotations refer 'javax.transaction' to 'com.google.inject.persist'.
- PurchaseOrder : Hide 'Completed' button conditionally.
- Fixed bad behaviour of discounts on sales/purchases/invoices, especially when coupled to currency changes or ati prices.
- Fix wrong sort on MRP list report. Now we have exactly the same sorting as the MRP process.
- Purchase Order:Sequence should be assigned when we click on button Requested only
- Fix wrong domain on AccountingReport (Analytic reports were linked to export menu instead of report menu)
- Use sign of General accounting move line to sign the analytic move line on analytic balance.
- Move : Fix generated move lines.
- Removed duplicate code in InvoiceLineServiceImpl that prevent to get product informations if tax or account is not defined on product.
- Account move : Fixed wrong evaluation on account move form to know if the daybook mode is enabled or not (use move.company instead of user.activeCompany expression). 
- Account move : removed unnecessary save on xml action after call an action-method with reload param
- ACCOUNT MOVE : mass daybook validation - manage JPA cache
- Tracking Number Configuration : Fix put requiredIf on 'Sequence' field.
- Reviewed completely Daybook process : Now, any account move generated automatically or manually are taken into account. Any filter on MoveLine have been updated (partner balances, reports, accounting process, views). 
When we update an existing move in daybook mode, we update the partner balances of the new version of move and for the previous version of move.
- Update customer account balances in real time (when we validate an account move) for total balance, instead of when we load the partner accounting situation. Also, enable the real time at the end of accounting batch, to avoid issue with recycled thread. 
- Validate all draft or daybook account moves when we close a period.
- Sequence : Fix fill automatically the company field and put it readonly when create sequence from another model.
- Sale Order Line : Fix calculate "Available stock" on onload.
- STOCK LOCATION : report for external stock location
- AnalyticMoveLine : Fill account & accountType from moveLine AND remove the rounding bug.
- AnalyticBalance report: Fix order by.
- Product: Removed 'shippingCoef' value set from onLoad.
- Alphabetical order on the table producedStockMoveLineList and consumedStockMoveLineList
- Schedulers: fix unclosed transaction errors over multiple runs with batch jobs.
- PARTNER : contact partner form view If the option generatePartnerSequence is false in Base App,allow to edit the field partnerSeq.
- MANUF ORDER : Exception Message tracking number not filled
- ICalendar: Fix Nullpointer Exception
- Fixed occasionnal scale rounding anomalies on unit conversions.
- Fixed issue on BankStatement computation of name when the bank statements are get directly from the bank server
- PRODUCTION : Update stockMoveLine onclick of splitByTrackingNo validation button.
- Base : fix Null Pointer error.
- Stock Move Line : remove action of make 'Tracking number' field required on product onchange.
- Account reconcile : use moveLine partner instead of move partner for mass lettering run from move line list on selected move lines.
- Account move reverse : use today date
- Fix NPE on opening sale order line form.
- ACCOUNTCONFIG : factor partner define form view and grid view
- INVOICEPAYMENT : company bank details filter
- Fixed a button and a field having the same name in user view.
- PACKING LIST : Use the external_reference instead of order number
- Now, the mass reconcile from move line list works for moveline without partner.
Moreover, the amount_remaining calculation on move line was wrong. Now we compute it when account is reconcile, not if the account is used for compute the partner balance.
- PURCHASE ORDER :StockMoveLine add field companyUnitPriceUntaxed and stock location average price update with company currency unit price.
- INVOICE : fix total decimal amount format on report
- Event : Fill correct partner when generate event from Customer.
- Base : Removed invoice binding in ClientSituation report for allowing all different invoices to show in report.
- ACCOUNTING EXPORT : sequence issue fix
- Accounting Export : Fix NPE.
- Configurator: add missing field in configurator sale order line formula form.
- MANUF. ORDER : don't allow to print if status = draft
- Tracking number search view fix.
- Schedulers: fix missing traceback.
- Debt recovery batch: fix error recovery.
- Base: Fix save issue on any change in AppBase record.
- Sale: fix Null Pointer error.
- Bank statement: fix status update for bank statement imports.
- Invoice line: fill product code on product change.
- Base : Fix save issue on any change in AppBase record.
- Mass stock move invoicing: fixed issue where the generated invoice could not be saved because the reference string was too long.
- TAX : copy. Active version of original tax is assigned to the new tax. It souldn't
- Taxline : fix suggestions in suggestbox.
- COPY OF A PRODUCT : avgPrice, startDate and endDate empty
- COST SHEET : fix wrong assignation of cost sheet group for human ressources
- MANUF. ORDER PRINTING : change the name of a table's column

## [5.0.6] - 2018-10-06
## Features
- Stock move : add hilite on stock move grid.
- Sale : add multiple sale order printing.

## Improvements
- Translate file name of manuf order printing.
- Translate event types.
- Stock Location Report : Modify Sorting field.

## Bug Fixes
- Removed 'cachable' from all extended apps.
- Vehicle : modify vehicleState from reference field to string and remove vehicleState model 
- Account : Modify Account Move Report
- Supplychain batch : Fix invoice all orders.
- StockLocation report: show right cost price when using average price.
- Stock move: Fix sort the record on stock move and stock move line grid view.
- Improve error message when trying to reserve too much quantity in stock.
- Duration : add translation to compute fullName.
- User : restrict active team selection to only teams that the user is already in.
- Partner : Fix add partner seq on card view.
- Purchase Order Line : Fix NPE when clear the 'Tax' field.


## [5.0.5] - 2018-09-19
## Features
- Add Data Backup and Restore
- Add unitary tests for password
- Module for mobile app added
- Vehicle Fuel Log : add columns vehicle name, plate no, purchasePartner in grid view
- Manufacturing orders can now be "permanent", quantity is set to 0. Regular orders cannot have quantity to 0 anymore.

## Improvements
- Fleet app: added FR demo data
- HR menu: added `order` for top menuitems
- Marketing app: added FR demo data
- Reworked ati sale orders/purchase orders/invoices.
- Advanced export : code refactor and optimize the performance.
- BPM workflow dashboard: Updated chart titles and fixed issue with per day status chart. 
- Configure BoM form: show sub bom list.
- Configurator: improve UI for indicators.
- Split formula list into formulas for product and formulas for sale order line in configurator creator.
- Add a column name to configurators, equal to the creator's name.
- General legder report: hide the balance when it is equal to 0.
- UserController: Improve exception handling.
- Update translations.
- Replace justification binary field in ExpenseLine with justificationMetaFile m2o MetaFile field
- Improve account management views.
- Add supplier invoice number in supplier invoices grid.
- Add column description in move line grid views.
- Make the boolean IsValid set to false when a line is changed in MoveTemplate.
- Can now select an ahead date in the Move From Template view wizard.
- Add a "Generate the pdf printing during sale order finalization" to configuration of Sale app.
- Allow manual creation of tracking number on stock move lines.
- Direct debit batch: filter out payment schedule lines with inactive partner bank details.
- Solved an issue linked to having a manufacturing order with a planned quantity of 0.
- Fix mass invoicing of stock moves sometimes opening up unrelated forms when trying to open a single stock move for more details.
- Improve exception handling in supplychain demo.
- Account: use partner name as default account name when creating from partner's account configuration screen
- Added labels to several buttons, especially in the opportunity views.

## Bug Fixes
- Fix on prod process report.
- Fix json field creator's issue of model change not reflected on fields. 
- Configurator export: add contextField to metaJsonField.
- Configurator creator: fill default attributes for both product and sale order line.
- Computation of value of a given stock location is now the same in the form and in the printing.
- BPM: Provide translations for some fields.
- Fix chart builder operator display issue.
- Portal: Fix ticket view on client portal.
- Fix wrong quantity on manuf order when generating prod product lines where bill of material quantity is different from 1.
- Fix new bug when checking type on configurator.
- BPM: Provide translation for Workflow dashboard.
- Ticket: fix NPE on click of 'Assign to me' button of ticket grid toolbar.
- Budget form: add missing tranlation.
- Invoice: reload the view after regenerating the printing.
- DuplicateObject : Perform Refactoring and optimisation of DuplicateObject controller and service.
- BPM: keep model read only if custom field is created from custom field creator.
- Fix % based discounts which didn't worked with decimal values on Sale, Purchase and Invoice.
- Fix the companyBankDetails field filling when SaleOrder is created from the Customer view.
- Fix groovy error due to a null list in action-budget-validate-exceed-line-amount.
- Event synchronization: fix some new events being archived.
- Password : Fix regex.
- Fix : error when computing the duration of a leave request without a company.
- Invoice : filter account depending on Invoice Company and Type & Account Type
- Fix stock move split by unit. 
- Tracking number configuration: Fix 'Tracking number order' selection and it's static variables.
- Tracking number configuration: display 'name' field on grid.
- Fixed a bug in the payroll preparation generation batch linked to using the old name of a renamed field.
- Fix generating an invoice from a stock move sometimes using wrong units, quantities and unit prices when product stock units were different than sale/purchase units.
- Removed editable from some reference fields in stock form views. 
- Now displays purchase orders in the Activity panel of a partner form for suppliers and carriers.
- Vehicle : rename driverContact to driverPartner & filter it.
- Vehicle : Card View - display vehicle company and driver
- Tracking Number Form view : automatically fill fields from stockMoveLine while creating new record from stockMoveLine. 
- Fix translation typo.
- Add xml escape in configurator export.
- StockRule : Sort Grid View according to Stock location, Product code, useCaseSelect ASC 
- Warning message on missing weight unit is now shown only on delivery stock move.
- Fixed unit conversion issues when calculating the cost price of a bill of material.
- Add missing translations.
- Tracking Number Form view : automatically fill fields from stockMoveLine while creating new record from stockMoveLine.
- Fixed wrong calculation of necessary scale for decimal result when inverting a unit convertion coefficient.
- Stock move lines can no longer be created from nothing (i.e not from a stock move or such) as it has no functionnal use and ensues anomalies.
- Data model fix: add missing mappedBy in operation order `inStockMoveLineList`.
- Manuf Order: fetch sequence using company.

## [5.0.4] - 2018-07-26
## Features
- User : Add a search filter to see the active users.
- Add support for multiple demo data config per app. 
- Configurator creator : Add demo data.

## Improvements
- Stock rules : change column name of code and name to product code and product name.
- Stock rules: new message template in demo data.
- Account : Create a new entry in menu configuration for Analytic journal types
- Apps management: improve layout of applications on the view
- Sale/purchase/invoice order lines of type "title" are now displayed in bold.
- Stock location tree: add missing french translation.
- Improved model studio by removing not required properties from different elements.
- Move lines: set automatically currency rate and amount in manual move lines.
- Stock location line: add the dotted field 'product.unit'.
- Stock move: make lines fully editable and removable on planned status.
- Harmonization of Sale order line, purchase order line and invoice line form views.
- Account : prefill employee/supplier/customer account creation form with default values from configuration.
- Timesheet on operation order: Compute operation order total duration so its always up to date.
- Improve partner form view.
- Address: rework coordinates updating.

## Bug Fixes
- Leads : Fix demo data according with 'isRecycled' new field.
- Remove useless dependencies in build.gradle files.
- Fix : Tax grid-view : sort records per code ASC
- MESSAGE WIZARD : Apply canEdit to 'false' on editable field 'company'. 
- Leads : Fix button 'Show all events' to see events when lead is converted or lost.
- EXPENSE TYPE : Remove button 'Catalog' and set general configuration to set correct number of digits for unit price for field 'salePrice'.
- Leads : Fix fill the 'Partner' when schedule an event from the lead.
- Prevents errors in configurator by temporary removing O2M selection for attributes and formula.
- Improve formula maximum length in configurator formula class.
- Hide configurator bill of material on sale configurator creator.
- Base: demo data, remove unused base_userInfo.csv
- Demo data: in base, "base_shipmentMode.csv" was deleted because present in stock, and correction of french demo data of "stock_shipmentMode" 
- Demo data, remove unused "base_scheduler.csv"
- Bill of material generation from configurator: fixed an exception on using a many-to-one from context in script.
- Production : Change menu title in french version from 'Ordres de Production' to 'Ordres de production'. 
- CANCEL REASON : put field "Name" manadatory.
- Studio: Fix datetime comparison for chart parameter.
- Cost price in manufacturing order: fix quantity in produced cost sheet line.
- Fix product last production price computation from manufacturing order.
- Configurator creator : Fix changes in import and export.
- Cost sheet group : Put field 'name' mandatory.
- Fix HR root menu access for all HR related apps. 
- Event : Fix domain on partner.
- Event: Fix create an event directly by clicking on the calendar.
- Partner price list : Put field "Label" mandatory.
- Demo data import : Fix issues in import demo data from excel demo file with specified configuration in excel file.
- Fix automatic project generation when confirming sale orders not knowing what type of generation to choose.
- Generating a project from a sale order: "project alone" renamed to "business project", generating a project with a "phase by line" or "task by line" generation type now automatically generates the elements.
- Employee: Fix always dirty form view.
- Fix translation : base, hr, project, business-project, accounting, bank-payment and studio.
- Added missing translations to the french version when generating a project from a sale order.
- Fixed bugs and updated printings of ATI sales/purchases/invoices. Also fixed generating a stock move from a sale/purchase order (would consider ati unit prices as if they were excluding taxes).
- Timesheet: Hide chart when imputing on manufacturing order.
- BUDGET : Display budget dustribution as editable and check order line amount and total of budget in purchase orderline and invoice line.
- Stock Rules: Fix npe on stock rule alert.
- Fix generated pack sale order lines not having their supply method correctly set.
- PRODUCT : Remove field 'ean13' which was unused.
- Model studio: Fix lost translation on field drag. 
- Purchase order: Fix hibernate exception when click on 'Generate suppliers purchase orders' button.
- Error message instead of NPE on operation order plan with a configuration error.
- Fix total amount reseting on save in bank orders.
- Configurator: fix using M2O in formula.
- Tracking number search: Fix stock location binding.

## [5.0.3] - 2018-07-06
## Improvements
- Add a panel with Shipping comments in the PackagingList Report.
- Permission assistant: use language configuration.
- User: validate password on change.
- Convert demo data excel file into zip file containing csv files ready to import.
- Use base configuration to set the scale of costPrice in ManufOrder.
- Production process: remove process line list from grid view and add company.
- Bill of materials: display company and status in grid view.
- Import demo data from excel demo file with specified configuration in excel file.
- Improve grid view for timesheets in operation order.
- Reduce padding of the default sequences of partner

## Bug Fixes
- Moved readonly behaviour of a button in sale order form from an attrs action directly onto the button to avoid potential future bugs.
- Event: fix recurrence configurations.
- Partner: fix opening client situation report.
- Opportunity: fix bugs of values being set wrongly when creating an opportunity from a partner or a lead.
- Production process and bill of material : fix errors generated when adding an element with a priority to a list for the first time.
- Production process: set as required the fields, "product", "qty", and "unit" to avoid NPE
- Fix payment method field of a purchase order not displaying any value in edit mode.
- Permission assistant: fix persisting import error.
- BANK - bank details type : update BBAN translation
- Invoice: fix trading name that was not set
- Remove the blank part to the right at PurchaseOrderLine
- Fix MRP : stock location filter. Only internal and external stock location should be able to use on MRP
- In CRM sale stage change "Nouveau" by "Nouvelle".
- Removed app service initialization exception. App records must not be required to initialize service. 
- Stock move: fix forbid a 'Planned' move and archived a 'Realized' move on delete.
- Printing a non-ventilated invoice no longer saves the generated printing and attaches it to the invoice.
- Production process : display the title of the field "name" on edit mode.
- Added missing translation and made product field of a production process required when the process is not authorized for multiple products.
- Production process: display the title of the field "name" on edit mode.
- Manufacturing order: don't compute a new sequence (appears when we plan a canceled manufacturing order) if a definive sequence has been computed before.
- Manufacturing order: fix quantity updating for manually added consumed products.
- Fix duplicate name field when editing or creating a "individual" partner.
- Logistical form: in M2O forwarder, we should be able to select carrier or supplier partner only.
- Partner demo data: fix import carrier partner sequence.
- Rework on menu 'Custom fields' of App builder.
- Manufacturing order: fix updating of quantities in stock locations when updating real quantities.
- When finishing a part of a manufacturing order, the newly generated out stock move gets now the correct stock location in the production process.
- Import history : change type of field log from text to MetaFile.
- Leads: Fix lost reason to readonly when lead is lost.
- Leads: Fix delete the 'Draft' button when lead is lost.
- Leads: Fix "Recycled" status
- Leads : while converting lead to partner if partner is not a customer then convert it as prospect.
- Add missing translation in alert message on timesheet line on manufacturing order.
- Fix domain issue that appears on Configurator BOM form view when we select a product.
- Partner : Display the partner balance only on existing records
- Opportunity: Fix priority on kanban by fill the 'orderByState' field on demo data.
- Fix 'Fields to select duplicate' to readonly in check duplicate wizard.
- Event :  when we create new event, set status 'Planned' by default.
- Opportunity: Fix set customer when create opportunity from lead.
- INVENTORY : fill the actual qty


## [5.0.2] - 2018-06-22
## Improvements
- Added the possibility to have production processes not limited to a single product and thus applicable to all bills of materials.
- New default behaviour for Mrp, proposal type is now based on the product's procurement method if no stock rule exists.
- Can now copy lines of a supplier catalog in partner view.

## Bug Fixes
- Partner: check whether another partner with the same email address exist or not on save.
- Supplier form: show button "create order" when isProspect or isCustomer, hidden if none of them
- Product: fix missing picture on product sheet.
- Minor change from "FromStock" option to "From stock" in sale order lines.
- Employee: fix encrypted fields.
- Fix rounding problem in HR batches calculations
- Type of stock move generated from manuf order is now correctly set to internal.
- Remove unused action.
- Fix columns of type reference in all tree view.
- Manufactoring Order: add rollback in operation order as it is in manufacturing order when click on finish
- Web service mobile: change the "create timesheet line" method to update it also, and have duration/hours updated
- Product : displaying quantities in stock with big numbers (until millions)
- Manufacturing order: fix NPE when adding manually an operation order, caused by missing work center


## [5.0.1] - 2018-06-18
## Improvements
- Rework accounting report for journal
- User: add default password pattern description.

## Bug Fixes
- Fix selection of sale order lines on MRP : Now we can select a sale order line whose product is storable, not excluded on MRP, and not delivered. 
Also, improve the sale order line grid and form views.
- Minor fixes to printing a sale/purchase order or an invoice.
- Sale order: fix NPE when adding a new line on pending orders.
- Fix error when trying to generate suppliers requests on a purchase order line under certain conditions
- Prevented bugs by changing the way the companySet field is set in partner form.
- Added missing translations on supplier catalog in partners.
- Fix translation of product full names.
- Fix error when trying to generate suppliers requests on a purchase order line under certain conditions
- Sale order : calculate fullname for draft orders while importing demo data.
- Sale order: fix copy problem by reseting field deliverState and deliveredQty, and others fields to null
- Event : fix an error on save that can occur if the organizer is not set.
- Stock move: hide button generate invoice if internal stock move
- Fix NPE happening in invoice lines.
- Financial account : Fix NPE(PersistanceException) while persistence of new account. 
- Opportunity: fix error in grid view when clicking on "Assign to me" button without having selected any lines.
- Fix Analytic balance report to take into account the company of the analytic journal.
- Fix line removal in confirmed sale order.
- Product: fix printing catalog of selected products.
- Product: fix missing pictures in catalog when not all products have pictures.
- Fix MRP : stock location filter. Only internal and external stock location should be able to use on MRP

## [5.0.0] - 2018-06-13
## Features
- User: configurable password pattern, generate random password, and send email upon password change
- Help added for fields.

## Improvements
- Improved the way to sort the accounting grand ledger per account, date, and moveLine ref.
- General data protection regulation: data export and anonymize feature added.
- Use general configurations to determine the number of digits displayed in purchase order, invoice and product catalog reports.
- Add sequence to sort stock move lines.
- Advanced export : Fix set limits before the wizard.

## Bug Fixes
- Leave Request : reset Leave Line after changing User
- Fix : wrong domain for the bill of material field in the 'add manuf order' popup form in a production order
- Fix : errors when trying to generate production orders from sale orders with products lacking a stock unit or bill of materials lacking a production process.
- Fix : error when add a manufacturing order lacking a production process to a production process.
- Fix : disable create and edit of saleorderline from sale order invoicing wizard.
- Fix account reconcilable issue when ventilate a refund invoice.
- Fix : On the Stock Move view -> saleOrder, invoice and purchase.. put readonly when status != draft
- Fix : On the Stock Move view -> saleOrder, invoice and purchase.. disable edition when status != draft
- Fix trading name form seemingly allowing to create new companies.
- Project Folder: The name is now display like title. Disable Project edit and new from Folder. 
- Fix missing address in Birt report for internal stock move + show it on the view
- Change domain in real operation order domain grid and calendar.
- Fix context for the creation of a template from a sale order
- Fix context for the creation of a sale order from a template
- Partner is readonly on purchase order if there are already lines.
- Partner is readonly on sale order if there are already lines.
- Accounting report : Reset some of the fields when copy accounting report and remove 'globalByPost' field.

## [5.0.0-rc3] - 2018-06-06
## Features
- Sale order: option to close linked opportunity upon confirmation.
- Calendar synchronization batch

## Improvements
- Sale order form : improve timetable and printing settings tabs.
- Employee : Removed social security number field in advanced search.
- Rework toolbar in Partner, PuchaseOrder and SaleOrder grid and cards views.
- Default configuration added for lead import. 
- Fill automatically the description of account move line for some accounting process : expense, doubtful, bank moves, account clearance, move template...
- Product : procurement method can now be both 'Buy and produce'. Stock rules creation now set a default refill method based on the product's procurement method.
- Product description is not copy to invoice line. Next version, a configuration will be added for this feature to sale/invoice/stock/purchase lines.
- Remove Meeting categories and use now generic Event categories for every event types.
- Modify 'Show all events' button on lead,partner and contacts form and make it like 'All tasks' button on project form.
- Improve calendar synchronization speed
- Advanced export : added warning and help for data export and fetch limits.
- Change phonebook title to Contact PhoneBook or Company PhoneBook in view and reports.
- Opportunity: move to proposition stage when creating a sale order.
- Add icons to reportings & configurations submenus and harmonize their order among all menus
- Accounting report : add domain filters on selection fields and resets to maintain consistency.
- Correctly use daybook moves in accounting reports if the configuration is daybook.
- Add validate date, ventilation date/user on Invoice.
- Show alert box instead readonly ventilate button when invoice date is in future. 
- Opportunity: move to proposition stage when creating a sale order.
- Change generated accounting report file name to be more explicit.
- Sale order: allow increasing quantity on delivered detail lines.
- Moved the Partner seq at the top of the Partner form views and set it as the first column in Partner grid views.
- Make Logistical form printable even in Provision status.
- Filter trackingNumber in stockMoveLine in function of fromStockLocation as it is done in tab detailsStockLocation of StockLocation
- Add a counter to limit iterations of while loop in YearService and BudgetService
- Create an interface for PeriodService

## Bug Fixes
- Product : buttons "real qty" and "future qty" will only be display if product is effectively created.
- Lead: Fix status on creation and on save.
- Fix timesheet editor duration, it will display duration in hours only. 
- Fix sale order titles on card and stream messages.
- Fix custom model editor breaking changes due to adk updates.
- Fix project planning editor breaking changes due to adk updates.
- Invoice : allow add/remove lines only on draft state.
- Timesheet editor: Remove line confirmation message translation fixed.
- Fix call to getInvoicingAddress of PartnerService in UMR form view.
- Fix NPE and wrong domain set  on price list (SaleOrder/PurchaseOrder/Invoice).
- Add missing translation for "Fullscreen Editor".
- Invoice demo data : Fix import address string field.
- Invoice demo data: Fix import draft sequence.
- Fix stack overflow error on duplicate city check. Refactored the code of duplicate check.
- Fix "Blockings" UI after $moment() helper was fixed, also fixed a bugged onSelect field on blocking.companies on edit mode.
- Fix NPE that happens when we invoice a stock move with a partner with no in/out payment mode.
- Add missing translation in Calendars
- Fix java.lang.NullPointerException by checking if product is null
- Sales : perform calculation of margin when we apply discount on sale order line.
- Fix permissions for role.manager and role.crm
- Fix line adding with having multiple project on timesheet editor.
- Add filter on the Fiscal year field in the Fiscal Period form view to select only Fiscal years.
- Event : When create new event from existing event bring values of some fields to new event like lead,type,calendar e.t.c. 
- Fix partner data for 'admin' and 'demo' users on demo data.
- Fix error on 'Import demo data' for apps without modules. 
- Advanced export: Fix crashing server,added parameters for fetch limit and maximum export limt
- Product : set format of description field as html in product report to support formatting of description.
- Sale: Add missing translation in subscription
- Production: fix nb of digits for BOM quantities configuration.
- Production order : New manufaturing order directly will not be created but with newly created popup wizard for Manufaturing order.
- Add missing translation for "Print production process"
- Fix NPE on automatic template message without a model.
- Fixed issue on amounts computation on general balance report and add a sort on account codes.
- Sort the general ledger report per account code and date
- Add missing sequence for analytic report in demo data, and add missing trace for exception in "save" method called in accounting report.
- Charts : rename action call that action has been renamed earlier.
- Fix date format in birt report in manufactoring order and operation order
- Fix some translations in account module.
- Permission assistant : Fix when import permissions with more than one role or group.
- Fix the opportunity field of a sale order being readonly
- Product : reset value of productVariantValue field when we change value of associated productVariantAttr field.
- Sale order: fix unremovable sale order line and editable invoiced sale order line.
- Fix set a prospect as the client of a sale order.
- Stock Move: select stock move with current invoice canceled in multiple sale and purchase invoicing form
- Set trading name automatically when creating or merging purchase order
- Fix trading names not being changeable in edit mode when partner was set in sale or purchase orders and invoice forms.
- Fix bad domain on permission assistant's user field.
- Fix interco invoice, sale and purchase order generation.
- Fix missing domains in production menu.
- Fix bank details default value in invoice form.
- Fix wrong calculation of unit price when create stock move from purchase order and sale order
- Switch Expense Birt report from portrait to landscape, fix and display the currency, set kilometric allowance to translatable and refactor elements positions.
- Product: fill information on sale/purchase order and invoice lines even when tax is missing.
- Fix trading name not being imported from opportunity on sale quotation generation
- Purchase order: fix doubled stock move total when generating supplier arrival.
- Remove companyName from Leads (fullName already has namecolumn attribute).
- Fix several errors when creating a production process with management of consumed products on phases.
- Bill of materials: fix filters using define sub-bill of materials
- Logistical form is now printed in customer language and not in user language.
- Timesheet editor: fix java.lang.NullPointerException in TimesheetServiceImpl when delete a line in editor
- Fix BillOfMaterial copy when creating a personalized BOM.
- Supplier request : added suggestion filters on selecting supplierPartner 

## [5.0.0-rc2] - 2018-05-09
## Features
- Partner: option to disable automatic partner sequence generation.

## Improvements
- sale order: Allow user to manually define a legal note that will be put on edited orders.
- sale invoice: Allow user to manually define a legal note that will be put on edited invoices.
- When we generate a message (using a template or not) from the wizrad, open the form view in edit mode directly and close automatically the wizard.
- All duration fields which are in 'integer', convert it to 'long' and calculation of duration.
- Barcode generator code formatting.
- Split sequences per module and remove unwanted sequences
- Improve multiple invoices printing.
- timesheet: Hide create button on if timsheet editor is disabled from configuration.
- Add en to fr translation in Barcode type configuration
- Mail Account: If the boolean "isValid" is true, allow user to disabled it 
- Product: If purchase module is not installed, hide boolean "Define the shipping coef by partner" and "Shipping Coef."
- Convert Lead: Removed separate panel of prospect and added prospect boolean into partner tab. Removed wrong field from opportunity tab. 
- Convert Lead: Removed opportunity and events conversion. Just partner and contact created from lead with events and opportunity linked.   
- Current user password should be required when updating user password 
- Replaced default grid by simple grid of event and opportunity in partner,contact and lead form. Simple grid does not contains partner,contact and lead field.
- Convert Lead: Fix translation and default values. 
- Advanced export wizard : Automatically download the export file on "Export" button and close the wizard.
- Message Template: Import demo data per module (or per app).
- Email Service: Default 'EmailAccount' will be used for adk mailing services or stream messages.
- Product form: Remove account app check from accounting tab to display account management o2m. 
- Add an advanced synchronization feature for event synchronization with external calendars.
- Add a monitoring on time differences on manufacturing orders.
- Renamed all reference to weight by mass.
- Refactor invoice payment list display in invoice-form and invoice-client-form views
- Check partner blocking on invoicing, direct debit and reimbursement. 
- Improved sale order status label.
- Timesheet editor: No group by on project when unique product is used.
- Invoice : Added domain filter for purchaseOrder field using supplierPartner and company.

## Bug Fixes
- invoice: fix hilighting for overdue bills. Warning hilite was always taking precedence over danger because of overlapping conditions.
- Compute the duration according to the start date and time and end date and time for ticket.
- Fix same image when duplicating products.
- invoice: fix bank address on birt template
- Fix BASE DEMO DATA import error
- Fix custom buttons imported with studio demo data. It will be only displayed if related app is installed.
- Custom model editor: Fix duplicating field property for relational json fields.
- timesheet-editor: Fix user value update on timesheet line.
- Fix Indicator generator language type field in fr demo data import
- Menu "Product variant attributes" displayed only if the boolean "manageProductVariants" is true in Base app
- Menus permissions adapted to v5 for demoCRM and demoERP users
- Fiscal Position interface reworked and moved from Account to Base module
- Fix empty partner list from batch report.
- Fix duplicate object wizard translation.
- Convert Lead: Fix type,call type and lead field of converted events. 
- Advanced Export: Fix export every fields of model.
- Advanced export : Fix input issue of selection field for export.
- Fix Advanced export object and wizard translation.
- Rename 'Replace duplicate' to 'Merge duplicated' and add translation.
- Fix pending payment display in Invoice payment total view
- Advanced export, add translation in CSV export + code improvement
- Fix readonly on date field in Fiscal year view form when creating a new record after having created a previous.
- Fix Forecasts : problem when including opportunities
- Fix default email account check for user. 
- Fix timesheet timer not computing durations.
- Fix sale order line form for 'title' type. 
- Timesheet editor: Fix blank editor, duration calcuation method, wrong total on group by.


## [5.0.0-rc1] - 2018-04-16
## Features
- New currency conversion API (ECB)
- Split accounting report VAT statement in two, VAT on amount received and VAT on invoicing
- Create sale order from partner and contact
- Multiple Project Gantt View with User and Project.
- Add a version management on Production process
- Added 'sale blocking' in Partner
- Added 'purchase blocking' in Partner
- Automatic mail on stock move realization
- ISPM15 standard for stock move
- Customs regulations for stock moves
- Payment schedules
- Cheque deposit slips
- Direct debit batches
- Logistical forms
- New subscription feature.
- Add support to dynamically set the number of decimal digit of BOM quantities
- If there is no template defined for the object, generate an empty draft message for MESSAGE : wizard
- Manage waste rate in bill of material and take it into account on cost sheet
- Partial manuf order realization
- Add a wizard to select a cancel reason and cancel a Stock move.
- Add button to open tasks and task kanban view in project module
- Manage shipment mode, freight carrier mode, incoterm, carrier partner, forwarder partner on Partner, Sale order, Stock move.
- New user option to allow notifications to be sent by email on desired entities.
- Add grid for easily regenerating and resending messages that were not sent.
- Add a process to force user to respect a quantity that is a multiple quantities on Sale order and Purchase order. 
- Add multiple default stock locations (receipt, fixup, component, finished products) in stock config instead of a single default stock location.
- Product sales and production configurator

## Improvements
- Label "hours" on Project and Task with the field totalPlannedHrs.
- Added filter on fields of 'Related Elements' in Project
- New report for InvoicingProject
- Added fullname in Sequence
- Generate sale order from Opportunity in edit mode directly
- Improved architecture of Message generation from a template, send email, and manage specific email account in a module without change the original behavior in the others
- A freight carrier is now a carrier partner
- Add purchase order line and sale order line import processes to compute tax related fields.
- Change the title "Delivery date" to "Estimated delivery date" in SaleOrder and PurchaseOrder
- EndPeriod on currency conversion api
- Allow to generate bank order file without using bic code in SEPA context.
- Remove the field supplierPartner from Event Object
- Upgrade functionality of advanced export feature
- Change dependency to base instead of CRM,HR and Project of helpdesk module.
- Update the SaleOrderLine form to look like InvoiceLine form.
- Update CRM & ICalendar events UI
- Removed extra links from tasks to other tasks. Kept only 'Predecessors tasks'.
- Allow to read the products from production, stock and crm modules
- Improve manufacturing order workflow.
- When we treat a component of a manuf order that is not loaded on MRP because there is no default BOM or because the component of manuf order is not a component of the bill of material, we add it with the level of manuf order product + 1.
- Replaced selection field for language on Partner per a M2O to Language object to be able to add new partner language easily.
- New select on product to choose if we want real or planned price for last product price or average price.
- Improve filter for supplier partner in sale order lines.
- MRP : manage the case where sale order line is partially delivered
- Moved Sale order Delivery state in Supplychain module
- If a service is selected on a sale order and we don't generate a stock move for services, the sale order line should be considered as delivered (or for the opposite).
- Allow to ventilate an invoice without product on the lines (mainly for purchase invoice).
- Improve timesheet form by adding time logging preferences.
- Rename "durationStored" to "hoursDuration" and "visibleDuration" to "duration".
- Add "show partner" button in lead form.
- Merge invoicing project sale order wizard with invoicing sale order wizard.
- New Inventory view
- New StockMove view
- Put buttons in hidden mode instead of readOnly in invoicing-project-form and put status to invoiced on generating invoice
- Add validation date in Inventory
- Add the number of components on Bill of material form view.
- Use services to get company bank details for better maintenability.
- Change open fullscreen timesheet editor from tab to popup, for update of lines o2m on close.
- Browser reload on install and uninstall of the app.
- Add base app install check on base module's menus.
- Improved TeamTask gantt view to support upgraded gantt view with colored user per task.

## Bug Fixes
- All StockMoveLines now appear in Produced products grid (ManufOrder)
- Fix the default amount on new invoice payment to use the amount remaining of the invoice.
- Fix demo data en and fr on AppSuplychain to set the correct value on the field supplStockMoveMgtOnSO
- Fix purchase order status in demo data.
- Fix different split methods in StockMove
- Fix event hide when we create new from calendar and set domain for my calendar and team calendar
- Fix default logo position
- Fix create event from contact m2m of partner
- Fix copy of manufacturing order
- Fix multiple NPE in CRM events
- Fix MRP calculation exception
- Fix manufacturing order stock move generation in unusual case.
- Fix default supplier in purchase order generation from sale order.
- Stock location is no more required if supplychain module is not enabled
- Compute the sale price and min sale price on purchase order line only if the product is saleable
- Fix hiding total(exTaxTotal or inTaxTotal) based on 'inAti' on sale and purchase orderline.
- Fix bulk install without demo data error. 
- Fix language of parent app on child app installation. Now it will install all parent (if not installed) with child app's language. 
- Fix timesheet and project planning editor according to changes in related models and fields. 
- Fix custom model form view with latest from adk.
- Fix resource management from project, removed unwanted menus and views related to it. 


## [4.2.5] - 2018-10-06
### Improvements
- Replace justification binary field in ExpenseLine with justificationMetaFile m2o MetaFile field
- Hide Ebics user full name in grid view.
- Add a new button on bank order grids to display the bank order lines.
- Bank orders can now be deleted when their status is "draft" or "canceled".
- Improve bank order printing layout.
- BANK ORDER : receiver address management for internationnal transfer

### Bug Fixes
- Fix NPE in BankOrder generation on missing bank name.
- Timesheets : use the timesheet user to filter the projects/tasks instead of the connected user.
- Cannot create a new bank order from the menu entry "awaiting signature".

## [4.2.4] - 2018-07-12
### Improvements
- Fiscal Position interface reworked and moved from Account to Base module
- Accounting export, use 1000 for administration and 1001 for FEC
- Move every method of mobile service in HumanRessourceMobileController + fix some and change parameters
- Web service mobile, create getKilometricAllowParam
- Add a new bank order type for existing file transfer
		
### Bug Fixes
- Fix readonly on date field in Fiscal year view form when creating a new record after having created a previous.
- Filter on values selection
- Fix translation in base module, add traceback on checkPlanning method in WeeklyPlanningController
- MoveLine, show tab of reconcile credit or debit
- Human ressource, remove french title in Employee, employee-filters + add translation
- Invoice, replace empty line on pending total by a color line (blue, info-text)
- In MoveLineExportServiceImpl, always have ignoreInAccountingOk = false
- Only "Active" EbicsUsers can be selected as signatoryEbicsUser in BankOrder form views.

## [4.2.3] - 2018-02-28
4.1.3 with axelor-process-studio modules

## [4.1.3] - 2018-02-28
### Improvements
- New assistant in expense form to select payment mode
- Leave management reset batch now creates a new line with negative quantity
  instead of clearing old lines
- Password encryption for ICALENDAR and SMTP account.
- Title "IBAN" became "IBAN / BBAN" in bankdetails

### Bug Fixes
- Permission change in most HR form
- Find kilometric allowance rate per company
- Remove filter on company for historic timesheet
- More fixes in expense form
- Show time unit for the right user in timesheet lines
- Hide button in leave request instead of making it readonly
- Fix count tags in hr menus
- Remove the wrong process to create an useless move for excess payment on refund invoice ventilation
- Generate a signature user certificate in EBICS TS mode for Transport User
- LEAD : convert wizard
- On Invoice payment, if it's due to an invoice or a refund, payment mode become null and hidden
- On Invoice payment, fix NPE by requiring paymentMode
- Change menu leave.request.root.leave.calender to leave.request.root.leave.calendar

## [4.1.2] - 2018-02-05
### Improvements
- Close pay period automatically when all payroll preparation are exported
- KilometricExpenseLineList are no more duplicated in ExpenseLineList. ExpenseLineList is renamed into GeneralExpenseLineList.
- The distinction between round-trip and one way ticket in kilometric expenses is now only informative.
- Reconcile invoice with related refund
- New boolean field "available to users" in expense type

### Bug Fixes
- Fix demo data en and fr on General config to set the correct value of the fields custStockMoveMgtOnSO and supplStockMoveMgtOnSO.
- Fixes in invoicing timetable in sale order
- Fix payment voucher report.
- Check ICS number on direct debit sepa file generation
- Fix receiver bank details filter in BankOrder
- Years can have the same code as long as the company/type differ.
- Fixes in expense form


## [4.1.1] - 2018-01-10
- Ebics TS improvements
- Bank order improvements
- HR improvments
- Some fixes


## [4.1.0] - 2017-06-19
- Ebics TS implementation
- Bank ordre implementation
- Lunch voucher management
- Employee bonus management
- Expense improvements (multi user, kilometric compute with some rates)
- Leave request improvements


## [4.0.2] - 2018-01-09
- BOM Componants tab order by priority
- Set all field of all locationline view in readonly
- Copy fix ICalendar and MailAccount
- Fix NPE when generating an invoice from a stock move.
- Fix currency conversion
- Update translations
- Fix google map api with api key
- Toolbar does not show in special split popup anymore
- Popup now closes after special split
- Fixed multiple bug reported
- Fix irrecoverable getInvoiceList request
- Replace all __user__ context by __user__ directly on sql
- Fix bad domain
- STOCKMOVE - report with tracking number
- Remove seeMonth Button on Project/MyPlanning
- Timesheet lines and related elements are sorted by date DESC
- Catch exception in TraceBack
- Manage duplicate move lines in reports
- CURRENCY CONVERSION : WS
- Update license


## [4.0.1] - 2017-06-19


## [4.0.0] - 2017-01-30
### Improvements
- Default partner type
- Remove sublines of SOlines : SubLine will be replaced per a title line to group some lines.
- Rename saleOrder status and field :
On saleOrder object :
the "confirm" status must be rename to "finalize".
the "validate" status must be rename to "orderConfirm"
the "validationDate" field must be rename to "confirmationDate"
the "validatedByUser" field must be rename to "confirmedByUser"
- Add sale order version management :
Versions will allow users to modify an order already finalyse :
when switching to finalyse status, save the order's PDF as an attachment. If it is the first version, only use order sequence for the name of the PDF. For later version we have to suffix order's PDF name by "-V" + numversion
Add new button called "New version" displayed in finalize status. This button will go back to draft status, and increment the version number.
Add checkbox in general settings to know if the feature must be used or not into the application
- Add cancel reason on saleOrder : When clicking on cancel sale order button, add intermediate popup to specify the cancel reason. Select a cancel reason must be required.
- SaleOrder : modify stockMove generation management.
Management of stockMove generation button display :
This button must be displayed only on "orderConfirm" status
By clicking on the button, a new StockMove will be created with all saleOrder's line and with "Planned" status. If a stockMove was already generated, an information message must appeared specifying that an other stock move can't be generated.
- A new pointed field must be added into stockmove to know his invoicing state. A stockmove is linked to only one invoice, so we can based on invoice status to know if a stockmove is invoiced or not.
- New status of delivery in sale order :
Add new column status named "delivery state" containing 3 values :
not delivered
partially delivered
delivered
When the stockMove will be realized, the delivery status will change to "delivred" if there isn't any back order and "partially delivered" if there is one.
- Removed definitively organisation module (and axelor-account-organisation) and added two new module axelor-human-resource and axelor-project
- PARTNER : manage all addresses in a new tab with some attributes (Invoicing address, Delivery address, default address)
- Invoice: the M2O to project is now defined per lines
- SALEORDER : Message for inform user that there is a special process on order
- PURCHASEORDER : Message for inform user that there is a special process on order
- SALEORDER : Added the management of subscriptions
- EXCEPTION : We don't support any more different kind of prefix for exception message depending of module.
- SMTP Default port : Now the default port is taken into account when we change security access:
Default: 25, SSL: 465, TLS: 587
- MESSAGE : copy support
- EVENT : Reschedule on Event
- OPPORTUNITY : Transform opportunity in SaleOrder
- INVOICE : Create from stockMove
- FISCAL POSITION : added filter
- SALEORDER: Management of models
- CUSTOMERCREDITLINE : Manage the rules
- PURCHASE ORDER : version management
- PURCHASE ORDER : add generation from selected sale order lines
- PRICELIST : Manage two price lists per partner (sale and purchase)
- BATCH : added mail Batch
- PURCHASE ORDER : merge purchase orders
- PurchaseOrder : status modification and workflow modification with supplier arrivals and invoice
Remove receipt status
Add new field receipt state with 3 possible values :
not received
partially received
received
Add 2 new checkboxes in general settings : 1 to know if supplier arrivals will be use in the application. And another one to know if the generation will be manual or automatic. If not, a button will be available
If an active supplier arrival already exists for a purchase order (ie status must be different from cancel)
In case of manual generation, an information message will be displayed to specify that an active supplier arrival already exists (in case of no auto-generation).
In case of auto generation, no new supplier arrival will be generated automatically (no impact for the user)
Add M2O into invoice to purchase order
Add M2O into invoice line to purchase order line (like sale order)
Use the checkbox "manageAmountInvoiceByLine" in general settings to know if the invoice amount management is done by line or not (as for sale order)
Automatically update remaining amount to invoice for purchase order :
if the M2O on invoice to sale order (purchase order) is not null, then use it
if it is null, then use the M2O on invoice line to sale order line (purchase order line)
this auto-update must be called during :
invoice ventilation
invoice cancellation
- SaleOrder : manage objects generation depending on general settings parameters
Add 6 new checkboxes in general settings to know if the generation process must be triggered automatically or not :
2 for stock move generation : 1 to know if it we want to use stock moves in the application. And another one if the first is checked to know if the generation will be manual or automatic
2 for purchase order generation : 1 to know if it we want to use purchase orders in the application. And another one if the first is checked to know if the generation will be manual or automatic
2 for stock production order : 1 to know if it we want to use production orders in the application. And another one if the first is checked to know if the generation will be manual or automatic
In case of auto generation, this 3 generation will be triggered during sale order confirmation
In case of manual generation, display a button and open the generated object after click on it :
Generate stock move button if sale order status is “order confirmed”
Generate purchase order button if sale order status is “finalise” or “order confirmed”
Generate production order button if sale order status is “order confirmed”
Add new checkbox to know if it is possible to generate invoice directly from sale order
- SaleOrder and PurchaseOrder : change remaining amount to invoice
- SaleOrder : show invoices
- B2B and B2C : manage the amount in ATI and WT
- INVOICE : Manage the possibility to hide discount
- DISCOUNTS : For discounts, the sign is changed, it means that when you enter a discount like 10%, this is really a discount of 10% on the price. Before, you had to put -10% if you wanted to have a lower price.
Now, if you put a discount of -10%, this is not a discount, it is an addition.
- SALEORDER : Duration of validity
- PARTNER : CustomerTypeSelect and SupplierTypeSelect are now some booleans
- BUSINESS FOLDER : Create a new saleOrder
- PRICELIST : 3 new ways to manage priceLists
in admin general, there is now a select "Compute Methode for Discounts" with 3 values:
* Compute Discount Separately : this is the default value, we don't put the discount in the unit price, we have an other field "price discounted" which will display the unit price discounted.
* Include Discount in unit price only for replace type : we replace the unit price with the unit price discounted and we don't show that there is a discount. Only if in the priceList we have the type "Replace"
* Include Discount in unit price : we replace the unit price with the unit price discounted and we don't show that there is a discount for all the types of discount.
- SALEORDER : Invoice
If no lines are selected, the button "Generate invoice" should generate an invoice for all lines.
If some lines are selected, the button "Generate invoice" should generate an invoice for the selected lines only.
- GENERAL : Added some parameters to enabled/disabled some features
- SEQUENCE :
Added four M2O to sequences object in account config :
- sequence for customer refund
- sequence for customer invoice
- sequence for supplier refund
- sequence for supplier invoice
By the way, we can use the same sequence for invoice and refund.
- INVOICE : draft sequence
- SALEORDER : cancel wizard
- ICAL integration
- EVENT (TICKET) : removed the panel "Followers/Comments"
- Enabled the change tracking on some objects
- JOURNAL : copy
- SEQUENCE : copy
- Cancelling of a payment input
- Added Cash Management module
- Change ProdResource object into WorkCenter
- PRODUCTFAMILY : dashlet to display the categories
- SALEORDERLINE : order the lines (drag&drop)
- INVOICELINE : order the lines (drag&drop)
- PURCHASEORDERLINE : order the lines (drag&drop)
- PARTNER: industrySectorSelect
- PRODUCT : fullname
- PRODUCT : implement cards view
- PRODUCT : Indicator for real quantity and futur quantity
- PRODUCT : Indicator for number of variant
- UNIT PRICE CONFIGURATION : Allowed the possibility to have an unit price on 10 decimal digits.
- PRODUCT : unit conversion :
Management of 3 units for a product
- stock unit
- sale unit
- purchase unit
The conversion is done with conversion unit lines or with a formule on Product fields (using StringTemplate).
- Company bank details management to use different bank details on invoice.
- INVOICE : payment list maangement and directly register a payment on invoice
- PARTNER : Manage a list of bankdetails
- OPPORTUNITY : Kanban view
- WORKCENTER : Charge per machine per day
- BILLOFMATERIAL : Rename "Remains" into "Residual products"
- OPERATION ORDER : change machine
- PARTNER : card view
- COSTSHEET management on Bill of materials
- added Methods for mobile app's web services
- Kilometric allowance rate management
- TASK : Kanban view
- client-portal module
- EVENT : recurrence management for events
- INVOICE : proforma printing
- COMPANY : use the logo as application logo
- MRP management
- SALEFORECAST that can be take into account for MRP
- Manage user preferences
- COMMUNE : renamed into CITY
- FISCAL YEAR : copy
- INVOICE : cancel or ventilate warning
- PURCHASE ORDER : Printing for requested purchase order
- SALEORDER : button to show all stockMove
- TIMESHEET : added Start&Stop on timesheet
- MOVELINE : reconcile rename
- BILLOFMATERIAL : manage version
- LEAD : mass updates
- MANUFACTURING ORDER : take into account the machine on scheduling
- TRACKING NUMBER : colors to alert if warranty or peremption date is near
- Added some sum or average on Aggregate on all grid views
- USER : create partner
- EMAIL : disabled the email sending
- STOCK MOVE : cancel a stock move and update the sale order or purchase order


## [3.0.3] - 2017-02-28
### Bug Fixes

- Fixed issue on the method to get the next period
- Changed display of CFONB field, by using nested, on AccontConfig
- Restore client view wizard action
- Updated translations of Invoice and SaleOrder report
- Fixed issue on sequence assignment on SaleOrder
- CRM Calls dashboard fix
- Unused actions removed
- Sequence onNew fix: nextNum removed


## [3.0.2] - 2015-09-09
### Bug Fixes
- Domain on Partner account in Invoice
- JPA context of the project during generation of invoice
- Removed unused selection for social network
- Fixed issue on Paybox WS
- Fill the language on partner/contact when we convert lead
- Improve Business, Project and Task views
- Fixed some issue on Target management
- Fixed some issue on Expense
- Manufacturing dashboard
- Reset value on AccountEquiv
- Fixed some index name
- Fixed issue with sequence called twice on SaleOrder
- Fixed some issue with conversion of lead
- Contact dashboard

### Improvements
- Sequence management
- Management of number of decimal for unit price
- Company logo became a MetaFile instead of a path
- MoveLine and move is generated during ventilation of invoice only if the amount on line or invoice is not null.
- Controle on sequence and date for customer invoice only
- Improve translations for printing (Purchase order) and Lead
- Attribute 'x-show-titles' updated in 'editor' according ADK improvement
- Change management of manageCustomerCredit field
- No check of account config if amount if null on a line of invoice during ventilation
- Per default, translation doesn't contains the context, according ADK improvement

## [3.0.1] - 2015-05-13
### Bug Fixes
- Fixed somes issues

### Improvements
- Sequence management
- Message management


## [3.0.0] - 2015-01-21
Fully responsive mobile ready views, gradle based build system and much more.

### Features
- migrated to gradle build system
- fully responsive mobile ready views
- Split object per modules
- Customer Relationship Management
- Sales management
- Financial and cost management
- Human Resource Management
- Project Management
- Inventory and Supply Chain Management
- Production Management
- Multi-company, multi-currency and multi-lingual

[Unreleased 5.1.1]: https://github.com/axelor/axelor-business-suite/compare/v5.1.0...dev
[5.1.0]: https://github.com/axelor/axelor-business-suite/compare/v5.0.7...v5.1.0
[5.0.7]: https://github.com/axelor/axelor-business-suite/compare/v5.0.6...v5.0.7
[5.0.6]: https://github.com/axelor/axelor-business-suite/compare/v5.0.5...v5.0.6
[5.0.5]: https://github.com/axelor/axelor-business-suite/compare/v5.0.4...v5.0.5
[5.0.4]: https://github.com/axelor/axelor-business-suite/compare/v5.0.3...v5.0.4
[5.0.3]: https://github.com/axelor/axelor-business-suite/compare/v5.0.2...v5.0.3
[5.0.2]: https://github.com/axelor/axelor-business-suite/compare/v5.0.1...v5.0.2
[5.0.1]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0...v5.0.1
[5.0.0]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc3...v5.0.0
[5.0.0-rc3]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc2...v5.0.0-rc3
[5.0.0-rc2]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc1...v5.0.0-rc2
[5.0.0-rc1]: https://github.com/axelor/axelor-business-suite/compare/4.2-dev...v5.0.0-rc1
[4.2.5]: https://github.com/axelor/axelor-business-suite/compare/v4.2.4...v4.2.5
[4.2.4]: https://github.com/axelor/axelor-business-suite/compare/v4.2.3...v4.2.4
[4.2.3]: https://github.com/axelor/axelor-business-suite/compare/v4.1.3...v4.2.3
[4.2.2]: https://github.com/axelor/axelor-business-suite/compare/v4.1.2...v4.2.2
[4.2.1]: https://github.com/axelor/axelor-business-suite/compare/v4.1.1...v4.2.1
[4.1.3]: https://github.com/axelor/axelor-business-suite/compare/v4.1.2...v4.1.3
[4.1.2]: https://github.com/axelor/axelor-business-suite/compare/v4.1.1...v4.1.2
[4.1.1]: https://github.com/axelor/axelor-business-suite/compare/v4.1.0...v4.1.1
[4.1.0]: https://github.com/axelor/axelor-business-suite/compare/v4.0.2...v4.1.0
[4.0.2]: https://github.com/axelor/axelor-business-suite/compare/v4.0.1...v4.0.2
[4.0.1]: https://github.com/axelor/axelor-business-suite/compare/v4.0.0...v4.0.1
[4.0.0]: https://github.com/axelor/axelor-business-suite/compare/v3.0.3...v4.0.0
[3.0.3]: https://github.com/axelor/axelor-business-suite/compare/v3.0.2...v3.0.3
[3.0.2]: https://github.com/axelor/axelor-business-suite/compare/v3.0.1...v3.0.2
[3.0.1]: https://github.com/axelor/axelor-business-suite/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/axelor/axelor-business-suite/compare/0f38e90dcd9126079eac78c1639a40c728e63d94...v3.0.0

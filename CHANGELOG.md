# Changelog
## [Unreleased 5.0.5]	
##Features
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
- Allow manual creation of tracking number on stock move lines.
- Direct debit batch: filter out payment schedule lines with inactive partner bank details.
- Solved an issue linked to having a manufacturing order with a planned quantity of 0.
- Fix mass invoicing of stock moves sometimes opening up unrelated forms when trying to open a single stock move for more details.
- Improve exception handling in supplychain demo.
- Reorganised the logistical forms view and made buttons more explicit as to whne they can or cannot be clicked.

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
- Production process and bill of material: fix errors generated when adding an element with a priority to a list for the first time.
- Production process: set as required the fields, "product", "qty", and "unit" to avoid NPE
- Fix payment method field of a purchase order not displaying any value in edit mode.
- Permission assistant: fix persisting import error.
- BANK - bank details type: update BBAN translation
- Invoice: fix trading name that was not set
- Remove the blank part to the right at PurchaseOrderLine
- Fix MRP: stock location filter. Only internal and external stock location should be able to use on MRP
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


## [Unreleased 4.x]

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
- Accounting export, fix problem on export FEC

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


[Unreleased 5.0.5]: https://github.com/axelor/axelor-business-suite/compare/v5.0.4...dev
[5.0.4]: https://github.com/axelor/axelor-business-suite/compare/v5.0.3...v5.0.4
[5.0.3]: https://github.com/axelor/axelor-business-suite/compare/v5.0.2...v5.0.3
[5.0.2]: https://github.com/axelor/axelor-business-suite/compare/v5.0.1...v5.0.2
[5.0.1]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0...v5.0.1
[5.0.0]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc3...v5.0.0
[5.0.0-rc3]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc2...v5.0.0-rc3
[5.0.0-rc2]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc1...v5.0.0-rc2
[5.0.0-rc1]: https://github.com/axelor/axelor-business-suite/compare/4.2-dev...v5.0.0-rc1
[Unreleased 4.x]: https://github.com/axelor/axelor-business-suite/compare/v4.2.3...4.2-dev
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

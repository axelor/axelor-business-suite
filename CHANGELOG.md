# Changelog

## [Unreleased 5.0.0-rc3]
## Features
- Sale order: option to close linked opportunity upon confirmation.

## Improvements
- Sale order form : improve timetable and printing settings tabs.
- Employee : Removed social security number field in advanced search.
- Default configuration added for lead import. 
- Fill automatically the description of account move line for some accounting process : expense, doubtful, bank moves, account clearance, move template...
- Product : procurement method can now be both 'Buy and produce'. Stock rules creation now set a default refill method based on the product's procurement method.
- Product description is not copy to invoice line. Next version, a configuration will be added for this feature to sale/invoice/stock/purchase lines.
- Copy account type from account in analytic move line.
- Modify 'Show all events' button on lead,partner and contacts form and make it like 'All tasks' button on project form.
- Advanced export : added warning and help for data export and fetch limits.
- Change phonebook title to Contact PhoneBook or Company PhoneBook in view and reports.
- Opportunity: move to proposition stage when creating a sale order.
- Add icons to reportings & configurations submenus and harmonize their order among all menus
- Accounting report : add domain filters on selection fields and resets to maintain consistency.
- Correctly use daybook moves in accounting reports if the configuration is daybook.
- Add validate date, ventilation date/user on Invoice.
- Show alert box instead readonly ventilate button when invoice date is in future. 

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
- Event : When create new event from existing event bring values of some fields to new event like lead,type,calendar e.t.c. 
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
- Target : now we can create target manually(without using batch) and also can be directly created from target configuration.

## [5.0.0-rc2] - 2018-05-09
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

[Unreleased 5.0.0-rc3]: https://github.com/axelor/axelor-business-suite/compare/v5.0.0-rc2...dev
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

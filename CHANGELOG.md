# Changelog

## [Unreleased 5.x]
- New currency conversion API (ECB)
- Create sale order from partner and contact
- Multiple Project Gantt View with User and Project.
- Label "hours" on Project and Task with the field totalPlannedHrs.
- Add a version management on Production process
- Pack Price Select and Qty update from packLine to subLine.
- Implement PackLine and SubLine for InvoiceLine and StockMoveLine.

## Improvements
- Added 'sale blocking' in Partner
- Added filter on fields of 'Related Elements' in Project
- New report for InvoicingProject
- Added fullname in Sequence
- Generate sale order from Opportunity in edit mode directly

## Bug Fixes
- All StockMoveLines now appear in Produced products grid (ManufOrder)
- Fix the default amount on new invoice payment to use the amount remaining of the invoice.
- Fix demo data en and fr on AppSuplychain to set the correct value on the field supplStockMoveMgtOnSO

## [Unreleased 4.x]
### Improvements

### Bug Fixes
- Fix demo data en and fr on General config to set the correct value of the fields custStockMoveMgtOnSO and supplStockMoveMgtOnSO.


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


[Unreleased 5.x]: https://github.com/axelor/axelor-business-suite/compare/dev...wip
[Unreleased 4.x]: https://github.com/axelor/axelor-business-suite/compare/v4.1.1...dev
[4.1.1]: https://github.com/axelor/axelor-business-suite/compare/v4.1.0...v4.1.1
[4.1.0]: https://github.com/axelor/axelor-business-suite/compare/v4.0.2...v4.1.0
[4.0.2]: https://github.com/axelor/axelor-business-suite/compare/v4.0.1...v4.0.2
[4.0.1]: https://github.com/axelor/axelor-business-suite/compare/v4.0.0...v4.0.1
[4.0.0]: https://github.com/axelor/axelor-business-suite/compare/v3.0.3...v4.0.0
[3.0.3]: https://github.com/axelor/axelor-business-suite/compare/v3.0.2...v3.0.3
[3.0.2]: https://github.com/axelor/axelor-business-suite/compare/v3.0.1...v3.0.2
[3.0.1]: https://github.com/axelor/axelor-business-suite/compare/v3.0.0...v3.0.1
[3.0.0]: https://github.com/axelor/axelor-business-suite/compare/0f38e90dcd9126079eac78c1639a40c728e63d94...v3.0.0

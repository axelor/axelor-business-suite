
# Changelog
## [Unreleased 5.2.5]
## Features
- ACCOUNTING REPORT : group by and subtotal of analyticDistributionTemplate.
- INVOICE : created new field payment date in invoice in order to use it in advance search.

## Improvements
- STOCK RULE : add comment field
- Sale Order: Desired delivery date is used to generate stock move if estimated date is empty.
- BILL OF MATERIAL : display product field before the production process field.
- UNIT CONVERSION : used large width for unit conversion form view.
- LEAVE REQUEST : change in email template.
- EBICSPARTNER : mass update on testMode

## Bug Fixes
- MANUF ORDER : Display sale order comment in manufacturing order printing.
- Invoice payment: fix issue in invoice payment form when invoice due date is empty.
- AppServiceImpl: Fix open resource with try-with-resources
- ObjectDataExportServiceImpl: Fix open resource with try-with-resources
- ImportAccountChart: Fix open resource with try-with-resources
- DataBackupCreateService: Fix open resource with try-with-resources
- MRP: Desired delivery date in sale/purchase orders is used when estimated date is empty.
- EMPLOYEESERVICE: Fix computation of working days.
- StockMove : Fix issue of generated invoice with empty invoiceLines
- DEBT RECOVERY : Don't create debt recovery line if no email address in debtRecovery.
- SaleOrderInvoicing : impossible to InvoiceAll if one invoice has been already generated
- Invoice: fix error on ventilation when sequence reset is per year.
- PROJECT : Replace required attribute on code field with readOnly if generateProjectSequence is true
- Stock Move: Do not modify wap when generating a new line in customer return linked to an order.
- REPORTS: Fix issue for reports which split the report on many tab on excel.
- PRODUCT : display button 'Update stock location' only for storable and stock managed products.
- ADDRESS : addressL4 is emptied when zip is filled
- INVOICE Report : Fixed issue in displaying proforma invoice comment from grid button

## [5.2.4] - 2020-02-05
## Improvements
- BankOrder: Display of Signatory ebics user and Sending date time in report.
- ACCOUNTING REPORT: new filter for analytic distribution.
- Timesheet: alert to check manufOrder is finished or not on timesheetLine.
- PaymentMode: Add sequence field on account settings grid view.
- Stock Move Line: store purchase price in stock move line to use this information in the declaration of exchanges.
- INVOICE: add specific note of company bank details on invoice report.
- Message: Improved performance when generating mail messages from templates.
- ACCOUNTING CUT OFF: display warning message when batch has been already launched with the same move date.
- BANKPAYMENT: Update condition to display field ics number.
- PURCHASE REQUEST: add new tab to see related purchase orders.
- ANALYTIC MOVE LINE: add id and move line to analytic move line grid.
- Subrogation release: improved visibility of unpaid invoices.
- INVOICE: Filling number of copies for invoice printing is now required.
- Stock Move: stock reservation management without sale order.
- Manuf Order: manage stock reservation from stock move.
- Invoice: Add control to avoid cancelation of ventilated invoice.
- BALANCE TRANSLATION: Translate "Balance" in french by "Solde".
- EXPENSE: add new printing design.
- Invoice printing: remove space between invoice lines without description.
- INVOICE: Add translation for "Canceled payment on" and "Pending payment" and update list of payment viewer in invoice form.
- Configurator: generate bill of material when creating a sale order line from a configurator.

## Bug Fixes
- INVOICE: Fixed payment mode on mass invoicing refund.
- MESSAGE: correction of sending a message and correctly update status of message.
- BankOrder: change translation of partnerTypeSelect field.
- Account: Add missing translations.
- BankOrder: Fix domain issue of signatoryEbicsUser field.
- INVENTORY: Fix issue of realQty when copying inventory.
- TIMESHEET: Remove leave days and holidays when changing end date.
- CAMPAIGN: Fix filter value each time changes while Generating targets from TargetList.
- Configurator: Fix sale order line not being created from a configurator.
- Configurator: Generate bill of material on generating a sale order line from a configurator.
- SaleOrderLine: Hide qty cell in SaleOrder report when the sale order line is a title line.
- INVOICE: now the date verification of the ventilation process depends of invoices of the same company.
- MOVE: corrected sequence generation, now use correctly the date of the move and not the date of validation.
- Stock Move partial invoicing: manage correctly the invoicing status when we refund the invoice.
- ANALYTIC REPORT: fix issue where wrong reports were printed.
- ACCOUNTING REPORT: improved analytic general ledger.
- Subrogative release: corrected the possibility to create two subrogation transmitted or accounted with the same invoices.
- Configurators: fix issue in configurator menu with latest AOP version.
- Stock Move: Do not modify wap when generating customer return.
- ADDRESS: Fix error message when clicking on ViewMap Btn of a new address.
- Configurator BOM: Correctly make the difference between components and sub bill of material.
- ACCOUNTING REPORT : corrected several issues with values on the summary of gross values and depreciation report.
- ACCOUNTING REPORT : in the summary of gross values and depreciation report corrected the problem of the apparition of line with an acquisition date after the report dates.

## [5.2.3] - 2020-01-23
## Features
- INVOICING PROJECT: consolidation of invoicing project.
- INVOICING BATCH: consolidation of phases.

## Improvements
- Invoice: Removal of companyBankDetails comment in invoice form.
- BANKSTATEMENT: import multiple records in a single line.
- Opportunity: set sale order defaults on new.
- Typos in PurchaseRequestLine domain file.
- PurchaseRequestLine: cacheable removed for this entity.
- BANKSTATEMENT: on copy reset statusSelect.
- STOCK: fromAddress in stock-move supplier arrival is now required.
- CARD VIEWS: Display non square images with the right proportions.
- InvoiceLine: filter on taxLine.
- EBICS : Support of ARKEA bank:
Defined the KeyUsage attribute as critical on self-signed certificate.
Defined the KeyUsage attribute with KeyEncipherment on Encryption certificate.
- EBICS : Support bank statements of LA BANQUE POSTALE bank:
This bank return the code `EBICS_OK` instead of the correct code `EBICS_DOWNLOAD_POSTPROCESS_DONE` when we fetch a file (HPD, FDL...)
In this case, the file is correctly retrieved from the bank server, but not saved in database. Now we don't throw an exception when the return code is `EBICS_OK`.
- Add checks in services on app configuration, now more services are only called if the corresponding app is enabled.

## Bug Fixes
- Sale Order Invoicing: take in consideration refund invoice when checking the invoiced amount.
- LEAVE REQUEST: Updated calendar filter.
- Forecast recap: Displaying selected value's title instead of value on error message.
- MOVE REMOVE SERVICE: corrected error that occurred when several lines were found.
- BANK ORDER: the date field is now again read-only on bank order generated automatically.
- Advanced Import: Fix config line import.
- MOVE LINE: removed the possibility to delete a move line in a move when the move line is reconciled.
- SUBROGATION RELEASE / INVOICE: corrected npe apearring in log when opening a new subrogation release.
- FIXED ASSET: set Deprecation Date in Move generated from FixedAsset and fix last Day Of Month FixedAsset.
- Invoice: Fix wrong attribute name used in grid views.
- Ebics Partner: set editable for bank order services list.
- INVOICE: Fixed invoice refund ventilation issue.
- EBICS BANK: Fixed typo issue on form view on the X509 fields.
- DataBackup: DataBackup non-persistable class issue Fix.
- FIXED ASSET: correction of prorata temporis.
- MULTI INVOICING: add control on generateMultiInvoiceBtn when invoice has already been created.
- INVOICE PAYMENT: fix an issue where an invoice payment is taken into account twice.
- GEONAME: fix city import.

## [5.2.2] - 2020-01-09
## Features
- TOOLS: added utility class for interacting with SFTP.
- PAYROLL PREPARATION: add new Payroll Preparation Export type "SILAE".
- Advance data import: Add action apply support.

## Improvements
- COMPANY: mass update enabled for some fields.
- BANKDETAILS: mass update enabled for currency and active field.
- PRODUCT: added tracking on code and name fields.
- EBICS USER : Group and sort by bank and partner in grid view.
- INVOICE: Display PFP validator and status in invoice supplier refund grid.
- EBICSBANK: Set tracking for all fields on update.
- METASCHEDULE : added batchServiceSelect option for Contract Batch.
- INVOICING PROJECT: provide menu for invoicing project grid for mass invoicing.
- EBICS USER: Add field serial number (CORP).
- EmployeeFile: Add new date field to store the date of latest upload.
- SALE ORDER/PURCHASE ORDER: add button "Back to confirmed order" and "Back to validated order" respectively.
- Budget: Addition of new value for periodDurationSelect.
- Stock move invoicing: when generating an invoice, the user can now only select quantity not present in generated invoices.
- SALE ORDER: change title "Description to display" of field 'description'.
- ACCOUNTING REPORT: display popup message on click of 'exportBtn'.
- INVOICING PROJECT: added field "teamTask" in timesheet line form related to project.
- FIXES ASSET: add analytic distribution template.
- FIXES ASSET CATEGORY: add analytic distribution template.
- STOCK CORRECTION: Change status to draft on copy.
- EBICS BANK: now X509 Extensions for auto signed certification are managed independently.
- STOCK CORRECTION: change error message on validate.
- EBICS USER: replacing Listener object with ImporterListener for EbicsUser Import.
- STOCK MOVE: Add default supplier partner in mrp line grid.
- STOCK MOVE: Maximized pop up of projected stock and counter.
- STOCK MOVE: store invoicing status in database.

## Bug Fixes
- Ebics User: resolve error getting on export and modify import config and export template to include BankOrderList and BankStatementList of EbicsPartner.
- BankOrder: Fix NPE on click of confirm for International transfer.
- BATCH: empty link to batch on copy for BankPaymentBatch and ContractBatch.
- Invoice Payment: resolve invoice amount due update when the generate accounting move option is not active.
- BANK ORDER: corrected the possibility to generate the same move twice.
- BANK ORDER: corrected the behavior of bank order, now bank order moves can be generated on validation or realization.
- BUSINESS PROJECT: fix negative refund in financial report.
- INVOICE: now comment on invoices is made from the concatenation of comment from partner and comment from company bank details.
- SALE ORDER: now on invoice generation from sale order action the generated invoices have their comment made from the concatenation of comment from partner and comment from company bank details.
- Mass invoicing stock move: fix generate one invoice from multiple stock moves.
- SALE ORDER: Fixed accounting situation not being set from the partner when generating the order from a partner form.
- USER: fix NPE on user creation when active team is null.
- Purchase Order: Fix NPE on copy of purchaseOrder when it has an empty purchaseOrderLineList.
- Contract: correct the translation of 'Fiscal position'.
- MRP: Do not show mrp lines from other MRPs when not displaying products without proposals.
- LEAVE REQUEST: No longer displays an error message when saving a leave request.
- EBICSUSER EXPORT: Fix for "Cannot get property 'code' on null object" error.
- Bank Payment: fix translation.
- OPPORTUNITY: On copy, clear sale order list.
- Fixed Asset: Fix issue of infinite value of depreciation rate.
- Campaign: Fix campaign form view.
- Purchase order: remove M2O invoice field.
- STOCKMOVE: display qty per tracking number and not total available qty for tracking number.
- HR: Fix typo.
- INVOICING PROJECT: Filter the records including deadlineDate.
- MRP calculation: fix NPE on calculation.
- MOVE LINE: Bank reconciliation amount is now read-only.
- MRP forecast: Reset status on copy.
- INVOICE: corrected the generation of comment with null display.
- Purchase Order: Fix NPE when company is null.
- SALE ORDER LINE: Rename "sale.order.line.type.select" selection to "line.type.select" and move it to base module.
- Stock Move mass invoicing: improve performance when selecting stock moves in wizard.
- Cut-off batch: filter already invoiced stock move to improve batch performance.

## [5.2.1] - 2019-12-16
## Features
- ACCOUNTING REPORT: add new report, bank reconciliation statement.

## Improvements
- INVOICE: new mandatory labeling: Head office address.
- Company: Add tree view for companies.
- AdvancedExportLine: Add translation for field orderByType.
- PURCHASE REQUEST: add new columns in purchase request grid view.
- MOVE: changed position of reconciliation tag in move form.
- BANK STATEMENT: add caption under bank statement line grid in bank statement form in order to explain the colors are used in bank statement line grid.
- PRODUCT: update translation for "Service" and "Product".
- STOCK MOVE: empty reservation date time on duplicate stock move.
- STOCK MOVE: Update stock move's form view.
- SALE ORDER PRINTING: rename title "Sale order" to "Order Acknowledgement" of report on condition.
- MOVE: Improved messages when there is an exception on trying to remove an accounting move.
- Partner Form: change the french translation of "Create sale quotation".
- STOCK MOVE: empty to and from stock location set on company change.
- STOCK MOVE: hide reserved qty when it is a supplier arrival or a customer return.
- STOCK MOVE: rename title of stock-move-form buttons related to PFP.
- STOCK MOVE: update pfp tags on stock move form.
- Invoicing project: unit conversion for "Duration adjust for customer".
- ACCOUNTING REPORT: change the title of "General ledger 2" from the selection.
- TAX: Show type select in grid view.
- Sale order/quotation: fix tab title when generating a quotation from an opportunity.

## Bug Fixes
- ANALYTIC: analytic journal in analytic line is now required.
- REFUND: avoid blocking message when ventilating the invoice.
- MOVE: fix display of status tag in move form.
- Manuf Order: fix real quantity not updating when a new line in consumed products is created.
- INVOICE PAYMENT CANCELLATION: corrected error when boolean allow removal validate move in account configuration is true.
- INVOICE: stopped the creation of invoice payment when a reconciliation is made with accounts not used in partner balance.
- User: find user by email using partner email address.
- Invoice: fix exception during passed for payment validation.
- Resolve NPE on stockMoveLines while displaying archived records.
- StockMove: set readonly to/fromStockLocation if status != Draft.
- INVOICE: remove the possibility for the user to manually link a stockMove to an invoice.
- PURCHASE ORDER LINE: isFilterOnSupplier is always true by default and can be set to false manually.
- INVOICE: Fix error on merging two invoices.
- Invoice: fix payment button visibility issue.
- HR: update insert leave method for mobile app.
- INVOICE: Fix printing of unit price when invoiceLine type is title.
- MOVE LINE: fix amount paid display in move line form.
- STOCK: ProductWorth computation fixed in ABCAnalysis.
- BASE: ABC Analysis Line with qty and worth equal to 0 are now removed.
- Fix Issues on EBICS user and partner form.
- Purchase Order: fix view budgetDistributionListPanel of purchaseOrderLine.
- Weighted Average Price: Fix computation issue causing an error in wap price.
- STOCK MOVE: fix the problem of partially invoiced qty in invoicing wizard form.
- STOCK CORRECTION: fixed error when qty is negative by reversing toStockLocation and fromStockLocation of created stock move.
- MASS INVOICING STOCK MOVE: fix error when selecting stock move to invoice.

## [5.2.0] - 2019-11-29
## Features
- Migration to Axelor Open Platform 5.2.
- Axelor-Business-Support: Addition of new module and app 'axelor-business-support'.
- Employee: added DPAE.
- TeamTask: added Frequency to be able to repeat tasks on given points in time.
- Employee: added wizard to create a new employee.
- SaleOrder: added possibility to invoice via generated task.
- Project: add support for project version.
- Project: add new form to create announcement for a given project.
- New menu and form stock correction allowing to fix a quantity in stock.
- User Form: Provide step wise view to create a user.
- TeamTask: Complete rework of team task model and views.
- Project: Complete rework of project model and views.
- Base: Addition of new object 'MailingListMessage' along with views, parent menu and sub-menus.
- InvoicingProject: Added new report 'InvoicingProjectAnnex.rptdesign' and attach to object on generating invoice.
- HR: Add CSV export support for Employment Contracts from its view and HR batch.
- Quality: Major improvements in axelor-quality module.
- QUALITY CONTROL: New report to print.
- AppBase: Add new configuration allowing to disable multi company support.
- OPPORTUNITY: Create event from opportunity.
- PROJECT: Add new feature allowing to create a template for a project.
- Invoice & PurchaseOrder: Added a budget overview.
- Advanced Import: Add feature to import data with advanced configurations.
- BPM: More than one workflow support for a same object.
- Sale Order/Sale Invoice: allow to change customer in existing invoice or order.
- MESSAGE TEMPLATE: added the management of additional contexts (groovy evaluation) in order to allow the use of Json fields.
- FORECAST RECAP: Add support of printing report in 'xls' and 'ods' format.
- PERIOD: Add a new popup when closing period to allow user to check and validate move lines.
- Stock Move: Add support for partial invoicing.
- ABC ANALYSIS: Add support for printing report in 'xls' type.
- Business Project: Add 'Project invoicing assistant' batch to update tasks and generate invoicing projects.
- Project Folder: Add two printings to display all project elements linked to the folder.
- Distance travelled calculation of kilometric expense line with Open street map.
- Timesheet: add timesheet reporting.
- Stock: Add stock history view for a given product, company and stock location.
- Production: Provide menu for Machine Planning.
- Company: Add employee Phonebook.
- Base: Addition of fields 'height' and 'width' in Company to change logo's dimension in Reports.
- Add a payment validator in supplier invoices.

## Improvements
- SaleOrder/Partner: adding new fields for comments on invoices, sale orders, purchase orders and deliveries.
- Timesheet: assign Task to lines when generating from Realise Planning.
- Timesheet: mark timesheet line as to be invoiced while generating it from Realise Planning.
- TeamTask: Add button to enter spent time.
- LogisticalForm: if config enabled, send an email on first save.
- MANUF. APP: new config to hide cost sheet group.
- Purchase Request: Add the possibility to select purchase orders.
- Production: Addition of two dummy fields to calculate sum of planned and real duration of operation orders.
- MOVE: improve reversion process.
- SaleOrder: task by product and task by line invoicing.
- SALE ORDER: Update in 'Quotations template' working process and view.
- DataBackup: Add possibility to restore date fields from a given relative date.
- PURCHASE ORDER LINES/INVOICE LINES: New fields related to budget.
- WEEKLY PLANNING: Add a type and minor changes.
- BULK UNIT COST CALCULATION: new way to compute all unit costs using BOM. Allow to compute cost using BOM level sequence.
- MRP: Generate new sequence on save.
- DataBackup: update importId when its null with format IDDDMMYYHHMM.
- Generating supplier order from partner form.
- Mobile App: Add configuration for quality app on mobile app.
- INVOICE: new process to print.
- PERMISSIONS: Display dashlets for groups/roles/users using the permission in permission and meta permission form views.
- OPPORTUNITY: Rename "opportunity type" to "type of need".
- INVOICE: Change form view's organization to match the SaleOrder view.
- PARTNER: Add link to employee form.
- PRICE LIST: Add dashlets to display partner.
- MRP: add boolean to exclude product without proposal in the result dashlet.
- HR: TRAINING - Optimization of the menu by adding filters
- MRP: Group proposals per supplier
- HR: rework training and recruitment menus.
- Data import: in the import file, add the possibility to fill selects either by values, title or translated titles.
- Action Builder: Add feature to create templates using json models to send email.
- FORECAST RECAP: sequence feature added for ForeCastRecap.
- Advance data import: Add new config to check if new configuration is added on file and do import according to file.
- UNIT: allows to translate name and label.
- STOCK RULE: Add template on StockRule and StockConfig used for mail notifications.
- CONFORMITY CERTIFICATE: display external reference on printing.
- TIMESHEET: new config to display line numbers.
- Advanced Import: Add support of imported records removal.
- SALE ORDER: Recompute unit price when hideDiscount is true.
- OPPORTUNITY: Auto fill sale-order form and cancel linked sale orders on 'closed lost' status.
- OBJECT DATA CONFIG: UX improvements and translations and change in export type.
- ANALYTIC MOVE LINE: project field title changed and domain filter added.
- CONTRACT: Set invoice date with newly added options for invoicing moment.
- Inventory: Manage different type of inventory: yearly or cycle turning.
- Address: Street have now a dedicated object.
- SALE ORDER / PURCHASE ORDER / INVOICES: Lines panel height set to 30.
- MOVE: add the possibility to choose a date while generating reverse move.
- Contract: Add analytic information to contract lines.
- MESSAGE TEMPLATE: help to suggest use of separator between email addresses.
- PRODUCTION ORDER: user can define manuf order's planned end date while creating production order from product form.
- PARTNER: new HTML field on partner for proforma.
- Contract: added button to manually close contract if termination date was set in the future.
- ContractLine: hide `isConsumptionLine` if not activated in Contract config.
- Employee: Add a creation workflow and allow to automatically create or link a user.
- TimesheetLine: Add reference to TeamTask and add time to charge field.
- Timesheet: Change tab title.
- Studio: Allowing to export all data without selecting any app builder.
- Studio: Custom model editor - Added title property for model and removed required condition for AppBuilder.
- MENUS: new organisation in CRM and Sales modules.
- Mobile: Add new app setting for 'Task'.
- JobPosition: Cannot open a position when status in on hold.
- Purchase Order: remove IPurchaseOrder deprecated class.
- Event: Allowing to suppress unsynchronized events.
- Employee: Add birth department and city of birth in employee.
- Employment Contract: Set form width to large.
- Contract: partner/project filters improved.
- APP BUILDER: Remove JsonCreator object.
- ContractBatch: Set default bankDetails of partner to created invoice bankDetails.
- PRICE LIST: hide historized price lists in pop-up view.
- MARKETING: Precise domain for model in message template.
- Change titles for productFamily.
- CONTRACT: Set project on generated invoices.
- ACCOUNT MOVE REVERSE: Selected reverse move date must not be after the date of the day
- ACCOUNT MOVE REVERSE: add the possibility to choose to hide or not the move lines (origin and reverse) in bank reconciliation
- Change google-plus icon by google one.
- Sale Order Line: Replacing 'price' with 'priceDiscounted' in a Grid View along with PurchaseOrderLine and InvoiceLine
- SUPPLY CHAIN: delete boolean manageInvoicedAmountByLine.
- FUNCTION: new object function on Sales and CRM and new M2O on partner
- CITIZENSHIP: Add new object citizenship on base and new M2O in country and employee
- EMPLOYEE: Files management added domains and demo data
- Inventory: Added calendar-view
- change term "Description" to "Comment" in english and "Commentaire" in french
- WEEKLY PLANNING: Days can be reordered and created.
- PROJECT / REPORTINGS: Addition of new dashboard - "Planned charge".
- INVENTORY: adding ODS file format in report selection type.
- Quality Control: Set default printing setting and update translation.
- INVOICE: Configure BIRT to generate an Excel and a Word file
- KEYWORD: remove Keyword model.
- Advanced Import: trim data before import.
- ACCOUNTING: year and accountSet fields are set empty when company is changed.
- ACCOUNTING CONFIGURATION AND REPORTED BALANCE: add a new journal parameter in accounting configuration named reported balance journal and this new parameter defines the journal used in the reported balance move line creation.
- PARTNER: Checks the consistency of the address list of a partner.
- EXCEPTION: Removing deprecated interface IException.
- PORTAL CLIENT: Add config to choose the type of connection (from ABS or external).
- DEMO DATA: Rename field data.
- ACCOUNTING BATCH: alert when the closing annual accounts batch already ran.
- CLIENT PORTAL: update the client form view.
- USER: Mass generation of random passwords.
- Advance data import: Set sequence of filetab and removed temporary file at the end which is created during the process.
- ACCOUNTING REPORT: add ODS file formate in report export type.
- FORECAST RECAP: reset fields while creating duplicate record.
- PICKING ORDER: new comment field for picking order on partner.
- INVOICE: Add field 'language' to change report language in company.
- QUALITY CONTROL: Add send email option when status is finished.
- EMPLOYEE: add emergency contact relationship.
- INVOICE PAYMENT: additionnal informations
- ACCOUNT CONFIG: update demo data for account config and 'is print invoices in company language' boolean is now at true by default.
- Contract: change filter on project field.
- TEAMTASKS: Creation from Same Order.
- PROJECT: show sub-menu project list when project type is empty.
- Financial Report: calculate total costs (per line) in chart and totals.
- ACCOUNTING REPORT: add Analytic general ledger.
- TIMETABLE: reworked timetables to have them work based on percentage of the order rather than on a per product basis. So far only for sale orders. Purchases to come.
- HRconfig: moved fields 'Health service' and 'Health service address' to HR module.
- STOCKMOVE: update stock move form view.
- MetaScheduler: fix MetaScheduler form-view in axelor-base module.
- AccountingReport: set configuration to display opening accounting moves default value to true.
- SUPPLYCHAIN: auto-complete sale order when it has been completely invoiced.
- Accounting situation: added two fields to manage the credit insurance.
- BoM: added a menu showing personalized bills of materials.

## Bug Fixes
- Studio: Fix import app without image.
- Generation of Project/Phase from SaleOrder.
- Contract: Fix issue of not saving currentContractVersion fields in form view.
- ProductTaskTemplate: fix button display issue for edit and remove buttons on tree view.
- Employee: Fix issue of not saving each phase of creation process.
- Marketing: Fix error when trying to generate an event per target.
- Contract: Fix import error in data-init.
- BUSINESS PROJECT: Report printing Division by zero.
- UnitCostCalculation: Fixed the date format in the csv export name file.
- BONUS MGT: Fix formula variable error of human resource when computing amounts.
- INVOICE: Hide Due amount in report while printing "Original invoice / Updated copy".
- PurchaseOrder: Fill order date with sale order creation date when generating puchase order from sale order with interco.
- Purchase Order: Rename field 'priceDisplay' to 'displayPriceOnQuotationRequest'.
- INVOICE: Reduce font size of tax table in Invoice printing.
- PURCHASE ORDER PRINTING: display buyer email and phone as in sale order printing.
- ACCOUNT MOVE REVERSE: add translation.
- Transactionnal: correction and standardisation of rollback.
- CONFIGURATOR BOM: product, qty and unit are displayed in the grid-view.
- SALE ORDER LINE: fixed error when selecting a product.
- Studio: Fix readonly fields are enabled when imported an application.
- Studio: Fix error when click on wkf buttons.
- Studio: Fix export app.
- Advance Data Import: Fix indexOutOfBound Exception.
- Advanced Data Import: Specify truncated value to sampleLines for large string.
- REPORTED BALANCE: corrected abnormal amount in reported balance move lines if there was no partner associated to it.
- REPORTED BALANCE BATCH: the case where reported balance date on fiscal year is missing is now correctly managed.
- PURCHASE ORDER: fix issue in 'discountTypeSelect' of Purchase Order Line Form.
- Advanced Data import:  fix issue of not generating info in log file when error occurred on import.
- Studio: fix m2o to metafile's widget property, display image,binarylink option.
- Advance data import: fix search issue, changed default import type of relational field to New and add import if condition for required Fields.
- Advance data import: Fix Data import error when there is same object in two different sheets.
- INVOICE: fix issue of invoice copy.
- TEAMTASK: Add fullname in demo data.
- Timesheet line: Duration label issue when created from mobile app.
- Purchase: Corrected translation of purchase not configured.
- SALEORDERLINE: Issue when Production module isn't installed.
- Invoice: replace field "irrecoverablestatusSelect" to "irrecoverableStatusSelect".
- SALE ORDER: fix error generating project without salemanUser.
- Advance Import: Resolve ArrayIndexOutOfBound exception.
- STOCK: Link back order with saleOrder or purchaseOrder.
- ADDRESS: when there is one address on partner it is treated as a default address.
- LEAVE REQUEST: Add error when leave reason has no unit.
- LEAVE REQUEST: Set duration value 0 if day planning of selected weekly planning is empty

[Unreleased 5.2.5]: https://github.com/axelor/axelor-open-suite/compare/v5.2.4...dev
[5.2.4]: https://github.com/axelor/axelor-open-suite/compare/v5.2.3...v5.2.4
[5.2.3]: https://github.com/axelor/axelor-open-suite/compare/v5.2.2...v5.2.3
[5.2.2]: https://github.com/axelor/axelor-open-suite/compare/v5.2.1...v5.2.2
[5.2.1]: https://github.com/axelor/axelor-open-suite/compare/v5.2.0...v5.2.1
[5.2.0]: https://github.com/axelor/axelor-open-suite/compare/v5.1.13...v5.2.0

# Changelog
## [Unreleased 5.3.4]
## Improvements
- USER: fix filter for activeCompany
- COST SHEET: in batch computing work in progress valuation, compute cost for ongoing manuf orders at the valuation date.
- ACCOUNTING BATCH : add start and end date of realizing fixed asset line
- Improve consistency of grid and card view menus.
- SALE ORDER : add possibility to choose a user to assign task generated from sale order form

## Bug Fixes
- WORKSHOP STOCK LOCATION : Fix config being ignored
- PURCHASE : Fix fields where negative values shouldn't be allowed
- SALES : Fix fields where negative values shouldn't be allowed
- Manuf Order: add missing translations.
- STOCK MOVE : Product translation in birt.
- Invoice Line: Fix hidden price field in form.
- Account: Fix incomplete sequences in english data init.
- INVOICELINE: fix hideIf for discountAmount.
- Product form: fix typo in help message.
- Production : add missing translations
- Studio: Fix custom model's 'formWidth' default value.

## [5.3.3] - 2020-04-14
## Improvements
- PAYROLL PREP: Net salary and social charges fields are now editable.
- MESSAGE: Changed the order of fields Language & Template when sending a message from an object.
- STOCK LOCATION LINE: now shows wap dashlet when accessing line from a product.
- CONTROL POINT: change french translation of name field.
- INVOICE: less margins between the customer name/address zone and the invoice lines table on Birt printout.
- AnalyticMoveLine: Validate total percentage.

## Bug Fixes
- Manuf Order: fix issue when printing multiple manufacturing orders.
When printing multiple manufacturing orders, operations from all orders were printed for each one.
- Purchase Request: Add missing translation.
- Purchase Request Line: fix product domain.
- Availability request: do not ask to allocate stock if the product is not managed in stock.
- Bank statement: fix on import the problem of random bank details chosen by request.
- SUPPLIER INVOICE: supplier invoices to pay can now be selected when activate passed for payment config is disabled.
- Company: Add missing translations.
- OPPORTUNITY: Fix address being incorrect when creating a partner from a lead.
- CostSheet: Add exception when purchase currency is needed in computation and missing in product.
- INVENTORY: Fix NPE on change of product field.
- Partner: Fix customer situation report display value of contact partner jobTitle.
- SaleOrder - PurchaseOrder: generate correct quotation's title according to its status.
- EmailAddress: Fix email address pattern.
- BANK ORDER REPORT: fix the problem of empty report if bank order lines sequences are too big.
- COST SHEET: properly take purchase unit into account.
- Partner: fix view marked as dirty when an archived partner exists with the same name.
- INVENTORY: Fixed an issue whith tracking number where the currrent quantity was not based on the tracking number.
- INVOICE: Company currency is now visible on new invoice.
- Cost sheet: Fix print button being readonly.
- BANK STATEMENT LINE AFB 120: Fix wrong order by in bank statement dashlet.
- Opportunity: Fix email not being duplicated when creating a partner from a lead.
- LEAD: fix function not being displayed in readonly mode.
- PRODUCT: fix position of Variant button.
- Project: fix some fields not being hidden properly.
- PARTNER: hide panels related to invoice when invoice app is disabled.
- EMPLOYEE : Fixed contactPartner fullName compute.

## [5.3.2] - 2020-04-01
## Improvements
- CLIENT-PORTAL: Chart now only shows invoices corresponding the client partner.
- PARTNER : add url widget for website.
- MrpFamily: make 'name' required field.
- LEAD: new design for view form.
- TARGET CONFIGURATION: end off date must now be superior to start date.
- TAX LINE: run off date must now be superior to start date.
- OPPORTUNITY: removed help in buttons from form view.
- DURATION: remove question mark in type selection title and add translation.
- Sale Order Invoicing Wizard: When searching for already invoiced lines, only takes customer invoices.
- ACCOUNTING REPORT: improved bank statement report.
- Product: set sequence while creating copy.
- MARKETING: end off date must now be superior to start date.
- Improve convert lead wizard form view layout.
- Target Configuration: improve naming & translation.
- Target: improve naming & translation.
- Fixed asset: add EU and US prorata temporis.
- DEMO DATA: add analytic journals to demo data.
- Accounting Report: add the possibility to filter the ledger report to only see not completely lettered move lines.
- LEAD: company name is now more highlighted.
- LEAD: fill region and country automatically on change of city in lead.

## Bug Fixes
- ORDER LINE: add missing french translation for "freeze fields".
- ORDER/STOCK: add missing translation for ISPM15.
- FORECAST RECAP: add translation for Forecast Recap line Type(s).
- SALE ORDER: Fix NPE when interco sale order is being finalized.
- SALE CONFIG: Fixed "Action not allowed" error when we try to update customer's accepted credit.
- TIMESHEET: Fix auto-generation of leave days not generating the first day.
- CLIENT-PORTAL: fixed the NPE when the user does not correspond to any partner.
- Partner: Invoice copy number selection field is now displayed when the partner is a supplier.
- MANUF ORDER: add missing translation.
- STOCK CORRECTION: Add missing translations.
- LEAD: Fix form view of primaryCity.
- EXCEPTION ORIGIN: Split selection values per module.
- PURCHASE REQUEST: Fix new product name not showing on line grid view.
- INVENTORY: add missing translations.
- SALEORDER: Fixed NPE when trying to select a customer with a company with no linked partner.
- BANK RECONCILIATION: corrected error with bank statement load where no other statements were loaded.
- LEAD: Fix "action does not exist" error on LEAD convert.
- MOVE TEMPLATE: Add missing translation.
- STOCK LOCATION: Add missing translation.
- MRP FAMILY: Fix wrong case in views title.
- INVOICELINE: Fix account not filtered depending on fixedAssets boolean.
- CONTACT: fix for Main Company not set for Contact from Partner.
- Account Config: display correct form view on clicking products.
- Stock Move invoicing: Fix NPE on opening invoicing wizard when a line has no product.
- Product: prevent the update of salePrice if autoUpdateSalePrice is disabled.
- Logistical Form: Fix NPE when computing volume.
- WORK CENTER: Fix fields not set to null for specific types of work center.
- MOVE TEMPLATE: Move templates without an end of validity date are now appearing in wizard.
- Fix a french word in an english message file.
- Fixed asset: corrected calculation of amortization.
- Production Order: fix typo in french translation.
- Accounting Situation: fix computation of balance due debt recovery.
- Stock Move: Fix 'Invoiced' tag displaying in internal stock moves and stock move lines.
- Stock Move: empty all references to orders and invoices on copy.
- MANUFACTURING ORDER: On consumed product, no longer display tracking numbers if available quantity equals 0.
- MOVE: Add missing translation.
- Sale Order Report: fix title being shown above address when there is only one address.
- LEAD: Fix display issue for description field on lead event grid view.
- User: Add domain filter on icalendar field in user preferences form view.
- LEAD: Fix error on import demo data.
- LEAD: Fixed the blank pdf when printing.
- BudgetLine: Resolve NPE when trying to validate an invoice with budget lines missing a date.
- EMPLOYEE CONFIG: Export code is now only visible and required if we choose to include the record in the export. Change made for EmployeeBonusType, LeaveReason, and ExtraHoursType.
- TRADING NAME: Fill default company printing settings if trade name printing setttings is not there in company.
- EMPLOYEE: set maidenName visibility by sexSelect select.
- TIMETABLE: Already invoiced timetable are now readonly.
- APP LEAVE: remove unused boolean overtimeManagement.
- EBICS CERTIFICATE: Fix serial number not saved.

## [5.3.1] - 2020-03-16
## Improvements
- InvoiceLine: add company and status fields in advanced search.
- LEAVE REQUEST: Allow sending a leave request in the past.
- CUSTOMER INFORMATIONS: Indicate that payment delay is in days.
- INVOICES DASHBOARD: Turnover is now calculated using both sales and assets.
- PRODUCT: Quantity field digits length is now based on nbDecimalDigitForQty in base config.
- TIMESHEET PRINTING: Manage visibility for task and activity column.
- STUDIO: Add panel on custom model demo data.
- ACCOUNTING REPORT: add account filter to summary and gross value report.
- Accounting Config: clarifying the field lineMinBeforeLongReportGenerationMessageNumber.
- Stock Move Line: Do not allow user to remove allocated stock move line.
- STUDIO: Set default value for form width to large.
- Block the creation of duplicate accounts.
- EBICSPARTNER: mass update on testMode field.
- PURCHASE REQUEST: translate "Purchase Request Lines" in french "Ligne de demandes d'achat".
- Ebics user: Display associated user in list view.
- STOCK CONFIG: Add three boolean fields to configure the display of product code, price, order reference and date in stock move report.
- SaleOrderLine/PurchaseOrderLine: Add transient boolean field to freeze price, qty, productName.
- HR BATCH: set email template for batch 'Email reminder for timesheets'.
- Workflow: Add support to select real status fields.
- QUALITY CONTROL: update the quality control report.
- USER: Add user's email signature, an html text field.
- MESSAGE TEMPLATE: New possibility to add an email signature from a user directly or an email account with a formula.
- SUPPLYCHAIN: In menu stock details by product, company field now autofills with the user's active company.

## Bug Fixes
- INVOICE LINE: add grid view and form view of budgetDistributionListPanel in form.
- PURCHASE ORDER REPORT: Fixed value of payment condition from PurchaseOrder's payment condition instead of using partner.
- EMPLOYEE: update the employee records in demo data so the creation process is finished.
- CAMPAIGN: add exception message on partner and lead at invalid domain in target list.
- SALEORDER: fixed bug causing the margins to be rounded to the unit.
- Fix exception happening when a timesheet reminder batch is run.
- Stock Move Line reservation: correctly set qty requested flag when generated from a sale order line.
- Stock Move: Delete empty date field in form view.
- Advance data import: Fix search issue on main object to import.
- LEAD: removed non persistable field wrongly appearing on the form view.
- LEAVEREQUEST: Fix the NPE when no leave request is selected to be edited.
- Project: Resolve issue in computation of time spent.
- EBICS: Display correctly hash code in certificates EBICS.
- Move: Fix exception message when saving a new record.
- SUPPLIER INVOICE: fix the problem of amount not updated in supplier invoice after use of mass invoice payment function.
- CLIENT PORTAL: Take user permissions into account for TeamTask counters.
- TimesheetLine: Fill duration on data import
- MRP: Fixed issue when user try to copy an existing MRP record.
- LEAVE REQUEST: corrected error when trying to change user.
- Base Batch: Fix the issue when user run calendar synchronization batch.
- LOGIN: Fixed js code page redirection.
- Fix exception happening in sale order line form when group is empty in user.
- DEBT RECOVERY: rollback debt recovery process if to recipients is empty or not in generated message.
- PROJECT: Fix NPE when generate Business project with SaleOrderTypeSelect as title.
- PROJECT: Fix NPE when generate Business project with projectGeneratorType Task by line and Task by product.
- MRP: do not copy sequence on MRP copy.
- TEAM TASK: Fixed issue on copying line from project view.
- INVOICE: Fix quantity and discount not displayed on printing.
- SALE ORDER: Fix unit code not displayed on printing.

## [5.3.0] - 2020-02-25
## Features
- Add Pack Feature in sale order.
- Remove Pack Feature from Product.
- FLEET: Manage rental cars and minor fixes.
- Studio: New features - Label with color, multiline string, grid column sequence, form width, spacer and order by properties.
- Add DMS Import.
- FORECAST RECAP LINE TYPE : create new object ForecastRecapLineType
- JSON-MODEL-FORM : add tracking on json fields
- Export studio app: email action - email template
- Export Studio app: export actions created with meta-action-from
- STOCK RULE: New boolean alert when orderAlertSelect is not alert and stockRuleMessageTemplate added.
- Studio : Added validIf property for custom field.
- Studio: MetaAction and MetaSelect menus with group by on app.
- META-MODEL-FORM: add tracking on json fields.
- CITY: Import automatically from Geonames files.
- MRP: Freeze proposals after manually modifying them.
- Added a global configuration to base app to define number of digits for quantity fields.
- Address: Addition of boolean 'isSharedAddress' in base config to check addresses are shared or not.
- BANK STATEMENT LINE: order by operation date and sequence in AFB120 grid view.
- BANK DETAILS: add search button on bank-details-bank-order-company-grid.

## Improvements
- JOURNAL: new viewer to display the balance.
- SALE ORDER LINE: Display availability status on sale order line grid view if sale order status is 'Confirmed'.
- Map: Filter out the data with empty address.
- Studio: sidebar option for panel.
- Studio: Tab display for panel tab.
- Studio: group by application on json model grid view.
- JSON FIELD FORM: add tracking in form fields.
- ExtraHoursLine: Add new field 'Type' referencing new domain ExtraHoursType.
- Account: Remove DirectDebitManagement.
- MENU BUILDER: Add selection support for icon and iconBackground.
- Custom Model: Hide menu panel and allows to create menu from menubuilder only.
- English language: Correction of errors in english words and change gender job word to genderless job word.
- Action Builder: Added option to update or use json field from real model.
- STUDIO: add 'attrs' for User.
- Studio: Added colSpan,title for the label and  visibleInGrid option for button.
- Studio: Added restriction for model and model field names, allowed only alphanumberic characters.
- Studio: Disable 'Visible in grid' option for spacer.
- STOCK MOVE LINE: display invoiced status at same place as available tag.
- Company: Replace the M2M bankDetailsSet with O2M.
- BANKDETAILS: Add tree and card view for bank details and balance viewer on company bank details.
- BANK STATEMENT: update automatically balance and date of bank details concerned by the bank statement when imported.
- ACTIONBUILDER: Update filter on valueJson and metaJsonField fields.
- MetaJsonField: show sequence and appBuilder field in json-field-grid.
- ACTIONBUILDER: Allow to add a condition at start in generated action-script.
- SEQUENCE: enable tracking for most fields.
- BANK ORDER: Bank order workflow pass from draft to validated when automatic transmission is not activated in payment mode.
- INVOICE: add specific note of company bank details on invoice report.

## Bug Fixes
- Studio: Fix access to json fields of base model in chart builder form.
- Fix "could not extract ResultSet" Exception on finalizing a sale order.
- Studio: Fixed display blank when you click on a field which is out of a panel.
- Studio: Fixed selection filter issue and sequence issue.
- StockMoveLine: Fixed empty popup issue while viewing stock move line record in form view.
- STOCK MOVE LINE: fix $invoiced tag displayed twice.
- LEAVE TEMPLATE: changed field fromDate and toDate name to fromDateT and toDateT.
- MRP: Fix error while generating all proposals.
- UI: Addition of onClick attributes in buttons.
- Sales dashboard: Fix chart not displayed.
- PRODUCT: Fix economicManufOrderQty displayed twice.


[Unreleased 5.3.4]: https://github.com/axelor/axelor-open-suite/compare/v5.3.3...dev
[5.3.3]: https://github.com/axelor/axelor-open-suite/compare/v5.3.2...v5.3.3
[5.3.2]: https://github.com/axelor/axelor-open-suite/compare/v5.3.1...v5.3.2
[5.3.1]: https://github.com/axelor/axelor-open-suite/compare/v5.3.0...v5.3.1
[5.3.0]: https://github.com/axelor/axelor-open-suite/compare/v5.2.5...v5.3.0

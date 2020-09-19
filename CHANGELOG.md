# Changelog
## [Unreleased 5.4.1]
## Features
## Improvements
- SaleOrder : Set team according to sale config.

## Bug Fixes
- Move : Fix NPE when changing date.
- MOVE LINE : add missing required tax line depending of account.

## [5.4.0] - 2020-09-16
## Features
- Add global tracking log feature.
- Update to Axelor Open Platform 5.3.
- Update to Gradle 5.6.4.
- HR: Added a leave line configuration menu in leave management.
- Move template: Add boolean to validate automatically the wizard generated moves.
- Move template: Add journal field to wizard.
- Move template: Add new field description.
- Move Template: Add totals for amount type.
- Move template: Add details button to grid view to display fields.
- Move template: Wizard dataInputList is now an editable grid.
- Move template: Add change track on update.
- Move template: Add demo data.
- Add CSRF protection for timesheet line editor and project planning editor.
- SUPPLIER PORTAL: Creation of supplier portal.
- Reports: Manage locale, date format and timezone following company configuration.
- Product: add products per company to configure different prices depending on the company.
- Studio: Add CSRF protection for every request header.
- Studio: Add support of menu creation for custom model and BPM node.
- Studio: Selection creation/update support.
- Studio: Added a selection builder to update existing selection or to create a new one.

## Improvements
- Invoice: Change buttons color.
- User: add field in user-form to force a password change for the user.
- QUALITY TAG: add field to configure the color of a quality tag.
- LEAD, TICKET, OPPORTUNITY: hide 'Take charge' button in grid view if the assigned user is the current user.
- MRP: hide generate proposal button on mrp line grid when the line is not a proposal.
- PRODUCT: Add json field "productAttrs" displayed in main product form view.
- HRConfig: Import formula variables in demo data.
- Product: add product image in grid view.
- INVOICE : Added the possibility to add a watermark to the printings.
- BPM: Add overview of the result of the wkf.
- MRP: add configuration to ignore end date on incoming mrp line type.
- Bank details: Add new fields journal and bank account.
- EMPLOYEE: set seniority date to hire date by default when hire date is filled.
- DURATION: add new field applicationType to know on what record the duration is used.
- USER: Default User language is based on application.locale from application.properties.
- BASE: Cache memory performance improved by not stocking geographical entities anymore.
- Accounting move line: When creating a new line the default debit or credit is set in order to balance the move.
- Accounting Move Line: When debit/credit is filled the other field is set to 0 instead of being set to a readonly mode.
- Invoice/Orders: The printing filename has been changed to show the id of the printed order/invoice.
- Employee: renamed dateOfHire, timeOfHire, endDateContract, dateOfBirth fields to hireDate, hireTime, contractEndDate, birthDate.
- Removed block permission from demo data.
- SaleOrder/Invoice/PurchaseOrder Line: Unit is now required.
- TeamTask: add new field categorySet to link multiple categories to a team task.
- Studio: Make app builder optional.
- Invoice/Sale OrderReport : Fix unit translation.
- Forecast Recap: default value set to today for from date field.
- Product: Added possibility to add a color to the product variant value tag with the field displayColor.
- Partner: Deleting partner will not remove linked employee.
- Journal: Improve balance calculation.
- Invoice: Addition of new field to display deliveryAddress on form and in report.
- EBICS PARTNER: added tracking to the fields of the ebics partner object.
- Move: It is now possible to change date until the move is validated.
- Employee: added a view to the employee's main contract company in the top right in view form.
- EbicsUser: Enable searching on requestLog dashlet.

## Bug Fixes
- HR: A leave-line cannot be saved whitout employee or leave-reason.
- Lead: Fix city name and state name issue in report printing.
- Studio: Add and fixed attributes of model and fields for import and export app.
- Bank reconciliation: add management of case of several account management for account domain, journal domain, auto change of journal and cash account fields and now account and journal from bank details are prioritized.
- Invoice: Fix NullPointerException when the product is not filled in invoice line.

[Unreleased 5.4.1]: https://github.com/axelor/axelor-open-suite/compare/v5.4.0...dev
[5.4.0]: https://github.com/axelor/axelor-open-suite/compare/v5.3.12...v5.4.0

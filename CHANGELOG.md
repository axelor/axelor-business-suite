# Changelog
## [Unreleased 5.4.0]
## Features
- Add global tracking log feature.
- Update to Gradle 5.6.4
- Update to Axelor Open Platform 5.3
- Bank details : Add new fields journal and bank account.
- Move template : Add journal field to wizard
- Move template : Add new field description
- HR : Added a leave line configuration menu in leave management
- Move Template : Add totals for amount type
- Move template : Add details button to grid view to display fields
- Move template : Add change track on update
- EMPLOYEE : set seniorityDate by hireDate
- Studio : Add CSRF protection for every request header.
- Leave request : Now handling the expected number of available days at a future date

## Improvements
- USER : Default User language is based on application.locale from application.properties
- BASE : Cache memory performace improved by not stocking geographical entities anymore
- Accounting Move Line : When debit/credit is filled the other field is set to 0 instead of being set to a readonly mode
- Invoice/Orders : The printing filename has been changed to show the id of the printed order/invoice
- Employee : renamed dateOfHire, timeOfHire, endDateContract, dateOfBirth fields to hireDate, hireTime, contractEndDate, birthDate
- Removed block permission from demo data.
- SaleOrder/Invoice/PurchaseOrder Line : Unit is now required
- TeamTask: add new field categorySet.

## Bug Fixes
- HR : A leave-line cannot be saved whitout employee or leave-reason.

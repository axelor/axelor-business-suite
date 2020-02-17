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
package com.axelor.apps.businessproject.exception;

/**
 * Interface of Exceptions. Enum all exception of axelor-account.
 *
 * @author dubaux
 */
public interface IExceptionMessage {

  static final String FOLDER_TEMPLATE = /*$$(*/ "You must add a sale order template" /*)*/;
  static final String INVOICING_PROJECT_EMPTY = /*$$(*/
      "You haven't select any element to invoice" /*)*/;
  static final String INVOICING_PROJECT_PROJECT = /*$$(*/ "You must select a project/task" /*)*/;
  static final String INVOICING_PROJECT_PROJECT_PARTNER = /*$$(*/
      "There is no customer for this project/task" /*)*/;
  static final String INVOICING_PROJECT_PROJECT_PRODUCT = /*$$(*/
      "You haven't select a product to invoice for the task %s" /*)*/;
  static final String INVOICING_PROJECT_PROJECT_COMPANY = /*$$(*/
      "You haven't select a company on the main project" /*)*/;
  static final String SALE_ORDER_NO_PROJECT = /*$$(*/ "No Project selected" /*)*/;
  static final String SALE_ORDER_NO_LINES = /*$$(*/ "No Line can be used for tasks" /*)*/;
  static final String SALE_ORDER_NO_TYPE_GEN_PROJECT = /*$$(*/
      "No type of generation project has been selected" /*)*/;
  static final String SALE_ORDER_BUSINESS_PROJECT = /*$$(*/
      "The project is configured to be alone" /*)*/;
  static final String JOB_COSTING_APP = /*$$(*/ "Job costing" /*)*/;
  String FACTORY_NO_FOUND = /*$$(*/ "Factory not found this type of generator" /*)*/;
  String FACTORY_FILL_WITH_PROJECT_ALONE = /*$$(*/
      "You can't fill a project with the strategy Project Alone." /*)*/;

  static final String NO_PROJECT_IN_CONTEXT = /*$$(*/ "No project found in context" /*)*/;
  static final String LINES_NOT_SELECTED = /*$$(*/ "Please select lines" /*)*/;

  static final String SALE_ORDER_GENERATE_FILL_PROJECT_ERROR_1 = /*$$(*/
      "Products must be Service type and Method of Supply Produce." /*)*/;
  static final String SALE_ORDER_GENERATE_FILL_PROJECT_ERROR_2 = /*$$(*/
      "Please complete the order lines with at least one product type 'Service' and the supply mode 'Produce'" /*)*/;

  static final String INVALID_EXCLUDE_TASK_FILTER = /*$$(*/
      "Invalid exclude task for invoicing filter" /*)*/;

  static final String BATCH_TASK_UPDATION_1 = /*$$(*/ "Task %s" /*)*/;

  static final String BATCH_TASK_UPDATION_2 = /*$$(*/ "Tasks updation completed : " /*)*/;

  static final String BATCH_TIMESHEETLINE_UPDATION_1 = /*$$(*/ "Timesheet line %s" /*)*/;

  static final String BATCH_INVOICING_PROJECT_1 = /*$$(*/ "Project %s" /*)*/;

  static final String BATCH_INVOICING_PROJECT_2 = /*$$(*/ "Generated invoicing project" /*)*/;

  static final String COMPANY_LOGO = /*$$(*/ "Please add a logo in your active company." /*)*/;
}

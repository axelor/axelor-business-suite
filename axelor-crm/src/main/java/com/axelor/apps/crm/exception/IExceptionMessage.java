/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
/**
 *
 */
package com.axelor.apps.crm.exception;

/**
 * @author axelor
 *
 */
public interface IExceptionMessage {

	/**
	 * Target service
	 */
	static final String TARGET_1 = /*$$(*/ "Objective %s is in contradiction with objective's configuration %s" /*)*/;
	static final String TARGET_GENERATE = /*$$(*/ "Target generated." /*)*/;

	/**
	 * Batch event reminder
	 */
	static final String BATCH_EVENT_REMINDER_1 = /*$$(*/ "Event reminder %s" /*)*/;
	static final String BATCH_EVENT_REMINDER_2 = /*$$(*/ "Event's reminder's generation's reporting :" /*)*/;
	static final String BATCH_EVENT_REMINDER_3 = /*$$(*/ "Reminder(s) treated" /*)*/;

	/**
	 * Batch event reminder message
	 */
	static final String BATCH_EVENT_REMINDER_MESSAGE_1 = /*$$(*/ "Reminder(s) treated" /*)*/;

	/**
	 * Batch target
	 */
	static final String BATCH_TARGET_1 = /*$$(*/ "Event reminder %s" /*)*/;
	static final String BATCH_TARGET_2 = /*$$(*/ "Objectives' generation's reporting :" /*)*/;
	static final String BATCH_TARGET_3 = /*$$(*/ "Treated objectives reporting" /*)*/;

	/**
	 * Convert lead wizard controller
	 */
	static final String CONVERT_LEAD_1 = /*$$(*/ "Lead converted" /*)*/;
    static final String CONVERT_LEAD_MISSING = /*$$(*/ "Parent lead is missing." /*)*/;
    static final String CONVERT_LEAD_ERROR = /*$$(*/ "Error in lead conversion" /*)*/;

	/**
	 * Event controller
	 */
	static final String EVENT_1 = /*$$(*/ "Input location please" /*)*/;
	static final String EVENT_SAVED = /*$$(*/ "Please save the event before setting the recurrence" /*)*/;
	
	
	/**
	 * Lead controller
	 */
	static final String LEAD_1 = /*$$(*/ "Please select the Lead(s) to print." /*)*/;
	static final String LEAD_4 = /*$$(*/ "No lead import configuration found" /*)*/;
	static final String LEAD_5 = /*$$(*/ "Import lead" /*)*/;

	/**
	 * Opportunity
	 */
	static final String LEAD_PARTNER = /*$$(*/ "Please select a path" /*)*/;

	/**
	 * Configuration
	 */
	static final String CRM_CONFIG_1 = /*$$(*/ "Please configure informations for CRM for company %s" /*)*/;
	static final String CRM_CONFIG_USER_COMPANY = /*$$(*/ "User %s must have an active company to use templates" /*)*/;
	static final String CRM_CONFIG_TEMPLATES = /*$$(*/ "Please configure all templates in CRM configuration for company %s" /*)*/;
	static final String CRM_CONFIG_TEMPLATES_NONE = /*$$(*/ "No template created in CRM configuration for company %s, emails have not been sent" /*)*/;
	
	
	/*
	 * Recurrence
	 */
	static final String RECURRENCE_RECURRENCE_TYPE = /*$$(*/ "You must choose a recurrence type" /*)*/;
	static final String RECURRENCE_PERIODICITY = /*$$(*/ "Periodicity must be greater than 0" /*)*/;
	static final String RECURRENCE_DAYS_CHECKED = /*$$(*/ "You must choose at least one day in the week" /*)*/;
	static final String RECURRENCE_REPETITION_NUMBER = /*$$(*/ "The number of repetitions must be greater than 0" /*)*/;
	static final String RECURRENCE_END_DATE = /*$$(*/ "The end date must be after the start date" /*)*/;

}

/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2014 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;

import com.axelor.apps.base.db.Duration;
import com.axelor.apps.base.db.repo.DurationRepository;


public class DurationServiceImpl extends DurationRepository implements DurationService  {
	
	
	public LocalDate computeDuration(Duration duration, LocalDate date)  {
		
		if(duration == null)  {  return date;  }
			
		switch (duration.getTypeSelect()) {
		
		case TYPE_MONTH:
			
			return date.plusMonths(duration.getValue());
			
		case TYPE_DAY:
			
			return date.plusDays(duration.getValue());

		default:
			
			return date;
		}
		
		
	}
	
	public BigDecimal computeDurationInDays(DateTime startDate, DateTime endDate){
		return new BigDecimal(new Interval(startDate,endDate).toDuration().toStandardDays().getDays());
	}
	
}

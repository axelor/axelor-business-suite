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
package com.axelor.apps.crm.service;

import java.math.BigDecimal;

import javax.persistence.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.axelor.apps.base.db.ITarget;
import com.axelor.auth.db.User;
import com.axelor.apps.crm.db.IEvent;
import com.axelor.apps.crm.db.IOpportunity;
import com.axelor.apps.crm.db.Target;
import com.axelor.apps.crm.db.TargetConfiguration;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.db.repo.OpportunityRepository;
import com.axelor.apps.crm.db.repo.TargetRepository;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.team.db.Team;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class TargetService {
	
	@Inject
	private EventRepository eventRepo;
	
	@Inject
	private OpportunityRepository opportunityRepo;
	
	@Inject
	private TargetRepository targetRepo;
	
	public void createsTargets(TargetConfiguration targetConfiguration) throws AxelorException  {
		
		if (targetConfiguration.getPeriodTypeSelect() == ITarget.NONE) {
			Target target = this.createTarget(targetConfiguration, targetConfiguration.getFromDate(), targetConfiguration.getToDate());
			
			this.update(target);
		} else {
			
			LocalDate oldDate = targetConfiguration.getFromDate();
			LocalDate date = oldDate ;
			while(date.isBefore(targetConfiguration.getToDate()) || date.isEqual(targetConfiguration.getToDate()))  {
		
				date = this.getNextDate(targetConfiguration.getPeriodTypeSelect(), date);
				Target target2 = targetRepo.all().filter("self.user = ?1 AND self.team = ?2 AND self.periodTypeSelect = ?3 AND self.fromDate >= ?4 AND self.toDate <= ?5 AND " +
						"((self.callEmittedNumberTarget > 0 AND ?6 > 0) OR (self.meetingNumberTarget > 0 AND ?7 > 0) OR " +
						"(self.opportunityAmountWonTarget > 0.00 AND ?8 > 0.00) OR (self.opportunityCreatedNumberTarget > 0 AND ?9 > 0) OR (self.opportunityCreatedWonTarget > 0 AND ?10 > 0))", 
						targetConfiguration.getUser(), targetConfiguration.getTeam(), targetConfiguration.getPeriodTypeSelect(), targetConfiguration.getFromDate(), targetConfiguration.getToDate(),
						targetConfiguration.getCallEmittedNumber(), targetConfiguration.getMeetingNumber(),
						targetConfiguration.getOpportunityAmountWon().doubleValue(), targetConfiguration.getOpportunityCreatedNumber(), targetConfiguration.getOpportunityCreatedWon()).fetchOne(); 
				
				if (target2 == null) {
					Target target = this.createTarget(targetConfiguration, oldDate, (date.isBefore(targetConfiguration.getToDate())) ? date.minusDays(1) : targetConfiguration.getToDate());
				
					this.update(target);
				
					oldDate = date;
				} else {
					throw new AxelorException(targetConfiguration, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, I18n.get(IExceptionMessage.TARGET_1), target2.getCode(), targetConfiguration.getCode());
				}
			}
		}
	}
	
	
	public LocalDate getNextDate(int periodTypeSelect, LocalDate date)   {
		
		switch (periodTypeSelect) {
		case ITarget.NONE:
			return date;
		case ITarget.MONTHLY:
			return date.plusMonths(1);
		case ITarget.WEEKLY:
			return date.plusWeeks(1);
		case ITarget.DAILY:
			return date.plusDays(1);

		default:
			return date;
		}
	}
	
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public Target createTarget(TargetConfiguration targetConfiguration, LocalDate fromDate, LocalDate toDate)  {
		Target target = new Target();
		target.setCallEmittedNumberTarget(targetConfiguration.getCallEmittedNumber());
		target.setMeetingNumberTarget(targetConfiguration.getMeetingNumber());
		target.setOpportunityAmountWonTarget(targetConfiguration.getOpportunityAmountWon());
		target.setOpportunityCreatedNumberTarget(targetConfiguration.getOpportunityCreatedNumber());
		target.setOpportunityCreatedWonTarget(targetConfiguration.getOpportunityCreatedWon());
//		target.setSaleOrderAmountWonTarget(targetConfiguration.getSaleOrderAmountWon());
//		target.setSaleOrderCreatedNumberTarget(targetConfiguration.getSaleOrderCreatedNumber());
//		target.setSaleOrderCreatedWonTarget(targetConfiguration.getSaleOrderCreatedWon());
		target.setPeriodTypeSelect(targetConfiguration.getPeriodTypeSelect());
		target.setFromDate(fromDate);
		target.setToDate(toDate);
		target.setUser(targetConfiguration.getUser());
		target.setTeam(targetConfiguration.getTeam());
		target.setName(targetConfiguration.getName());
		target.setCode(targetConfiguration.getCode());
		return targetRepo.save(target);
	}
	
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void update(Target target)  {
		User user = target.getUser();
		Team team = target.getTeam();
		LocalDate fromDate = target.getFromDate();
		LocalDate toDate = target.getToDate();
		
		LocalDateTime fromDateTime = null;
		LocalDateTime toDateTime = null;
		if (fromDate != null) {
			fromDateTime = fromDate.atStartOfDay();
		}
		
		if (toDate != null) {
			toDateTime = toDate.atTime(23, 59);
		}

		if(user != null)  {
			Query q = JPA.em().createQuery("select SUM(op.amount) FROM Opportunity as op WHERE op.user = ?1 AND op.salesStageSelect = ?2 AND op.createdOn >= ?3 AND op.createdOn <= ?4 ");
			q.setParameter(1, user);
			q.setParameter(2, IOpportunity.STAGE_CLOSED_WON);
			q.setParameter(3, fromDateTime);
			q.setParameter(4, toDateTime);
					
			BigDecimal opportunityAmountWon = (BigDecimal) q.getSingleResult();
			
			Long callEmittedNumber = eventRepo.all().filter("self.typeSelect = ?1 AND self.user = ?2 AND self.startDateTime >= ?3 AND self.endDateTime <= ?4 AND self.callTypeSelect = 2",
					IEvent.CALL, user, fromDateTime, toDateTime).count();
			
			target.setCallEmittedNumber(callEmittedNumber.intValue());
			
			Long meetingNumber = eventRepo.all().filter("self.typeSelect = ?1 AND self.user = ?2 AND self.startDateTime >= ?3 AND self.endDateTime <= ?4",
					IEvent.MEETING, user, fromDateTime, toDateTime).count();
			
			target.setMeetingNumber(meetingNumber.intValue());
			
			
			target.setOpportunityAmountWon(opportunityAmountWon);
			
			Long opportunityCreatedNumber = opportunityRepo.all().filter("self.user = ?1 AND self.createdOn >= ?2 AND self.createdOn <= ?3",
					user, fromDateTime, toDateTime).count();
			
			target.setOpportunityCreatedNumber(opportunityCreatedNumber.intValue());
			
			Long opportunityCreatedWon = opportunityRepo.all().filter("self.user = ?1 AND self.createdOn >= ?2 AND self.createdOn <= ?3 AND self.salesStageSelect = ?4",
					user, fromDateTime, toDateTime, IOpportunity.STAGE_CLOSED_WON).count();
			
			target.setOpportunityCreatedWon(opportunityCreatedWon.intValue());
		}
		else if(team != null)  {
			
			Query q = JPA.em().createQuery("select SUM(op.amount) FROM Opportunity as op WHERE op.team = ?1 AND op.salesStageSelect = ?2  AND op.createdOn >= ?3 AND op.createdOn <= ?4 ");
			q.setParameter(1, team);
			q.setParameter(2, IOpportunity.STAGE_CLOSED_WON);
			q.setParameter(3, fromDateTime);
			q.setParameter(4, toDateTime);
					
			BigDecimal opportunityAmountWon = (BigDecimal) q.getSingleResult();
			
			Long callEmittedNumber = eventRepo.all().filter("self.typeSelect = ?1 AND self.team = ?2 AND self.startDateTime >= ?3 AND self.endDateTime <= ?4 AND self.callTypeSelect = 2",
					IEvent.CALL, team, fromDateTime, toDateTime).count();
			
			target.setCallEmittedNumber(callEmittedNumber.intValue());
			
			Long meetingNumber = eventRepo.all().filter("self.typeSelect = ?1 AND self.team = ?2 AND self.startDateTime >= ?3 AND self.endDateTime <= ?4",
					IEvent.MEETING, team, fromDateTime, toDateTime).count();
			
			target.setMeetingNumber(meetingNumber.intValue());
			
			
			target.setOpportunityAmountWon(opportunityAmountWon);
			
			Long opportunityCreatedNumber = opportunityRepo.all().filter("self.team = ?1 AND self.createdOn >= ?2 AND self.createdOn <= ?3",
					team, fromDateTime, toDateTime).count();
			
			target.setOpportunityCreatedNumber(opportunityCreatedNumber.intValue());
			
			Long opportunityCreatedWon = opportunityRepo.all().filter("self.team = ?1 AND self.createdOn >= ?2 AND self.createdOn <= ?3 AND self.salesStageSelect = ?4",
					team, fromDateTime, toDateTime, IOpportunity.STAGE_CLOSED_WON).count();
			
			target.setOpportunityCreatedWon(opportunityCreatedWon.intValue());
		}
		
		targetRepo.save(target);
		
	}
	
	
}

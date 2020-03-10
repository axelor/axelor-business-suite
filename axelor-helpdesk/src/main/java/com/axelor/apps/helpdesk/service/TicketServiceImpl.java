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
package com.axelor.apps.helpdesk.service;

import com.axelor.apps.base.db.AppHelpdesk;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.AppHelpdeskRepository;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.base.service.publicHoliday.PublicHolidayService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.helpdesk.db.Sla;
import com.axelor.apps.helpdesk.db.Ticket;
import com.axelor.apps.helpdesk.db.repo.SlaRepository;
import com.axelor.apps.helpdesk.db.repo.TicketRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public class TicketServiceImpl implements TicketService {

  @Inject private SequenceService sequenceService;

  @Inject private AppHelpdeskRepository appHelpdeskRepo;

  @Inject private TicketRepository ticketRepo;

  @Inject private SlaRepository slaRepo;

  @Inject private PublicHolidayService publicHolidayService;

  @Inject private WeeklyPlanningService weeklyPlanningService;

  private LocalDateTime toDate;

  /** Generate sequence of the ticket. */
  @Override
  public void computeSeq(Ticket ticket) {

    if (Strings.isNullOrEmpty(ticket.getTicketSeq())) {
      String ticketSeq = sequenceService.getSequenceNumber(SequenceRepository.TICKET, null);
      ticket.setTicketSeq(ticketSeq);
    }
  }

  /**
   * Finding SLA, apply on ticket & Calculate the deadline of the ticket based on days & hours which
   * is defined in SLA.
   */
  @Override
  public void computeSLA(Ticket ticket) {

    AppHelpdesk helpdesk = appHelpdeskRepo.all().fetchOne();

    if (helpdesk.getIsSla()) {

      Sla sla =
          slaRepo
              .all()
              .filter(
                  "self.team = ?1 AND self.prioritySelect = ?2 AND self.ticketType = ?3",
                  ticket.getAssignedToUser() == null
                      ? null
                      : ticket.getAssignedToUser().getActiveTeam(),
                  ticket.getPrioritySelect(),
                  ticket.getTicketType())
              .fetchOne();

      if (sla == null) {
        sla =
            slaRepo
                .all()
                .filter(
                    "self.team = ?1 AND self.prioritySelect = ?2 AND self.ticketType = null OR "
                        + "(self.team = null AND self.prioritySelect = ?2 AND self.ticketType = ?3) OR "
                        + "(self.team = ?1 AND self.prioritySelect = null AND self.ticketType = ?3)",
                    ticket.getAssignedToUser() == null
                        ? null
                        : ticket.getAssignedToUser().getActiveTeam(),
                    ticket.getPrioritySelect(),
                    ticket.getTicketType())
                .fetchOne();
      }
      if (sla == null) {
        sla =
            slaRepo
                .all()
                .filter(
                    "self.team = ?1 AND self.prioritySelect = null AND self.ticketType = null OR "
                        + "(self.team = null AND self.prioritySelect = ?2 AND self.ticketType = null) OR "
                        + "(self.team = null AND self.prioritySelect = null AND self.ticketType = ?3)",
                    ticket.getAssignedToUser() == null
                        ? null
                        : ticket.getAssignedToUser().getActiveTeam(),
                    ticket.getPrioritySelect(),
                    ticket.getTicketType())
                .fetchOne();
      }
      if (sla == null) {
        sla =
            slaRepo
                .all()
                .filter(
                    "self.team = null AND self.prioritySelect = null AND self.ticketType = null")
                .fetchOne();
      }
      if (sla != null) {
        ticket.setSlaPolicy(sla);
        try {
          this.computeDuration(ticket, sla);
        } catch (AxelorException e) {
          e.printStackTrace();
        }
      } else {

        ticket.setSlaPolicy(null);
      }
    }
  }

  /**
   * Calculate deadline date & time of the ticket.
   *
   * @param ticket
   * @param sla
   * @throws AxelorException
   */
  protected void computeDuration(Ticket ticket, Sla sla) throws AxelorException {

    if (sla.getIsWorkingDays()
        && ticket.getAssignedToUser() != null
        && ticket.getAssignedToUser().getActiveCompany() != null
        && ticket.getAssignedToUser().getActiveCompany().getWeeklyPlanning() != null
        && ticket.getAssignedToUser().getActiveCompany().getPublicHolidayEventsPlanning() != null) {

      if (sla.getDays() > 0) {
        LocalDateTime fromDate = ticket.getStartDateT().plusDays(1);
        this.calculateWorkingDays(
            fromDate, ticket.getAssignedToUser().getActiveCompany(), sla.getDays());
        ticket.setDeadlineDateT(toDate.plusHours(sla.getHours()));

      } else {
        ticket.setDeadlineDateT(ticket.getStartDateT().plusHours(sla.getHours()));
      }
    } else {
      this.calculateAllDays(ticket, sla);
    }
  }

  /**
   * Calculate deadline date & time based on all days if workingDays field in SLA is not checked.
   *
   * @param ticket
   * @param sla
   */
  protected void calculateAllDays(Ticket ticket, Sla sla) {
    LocalDateTime localDateTime = ticket.getStartDateT().plusDays(sla.getDays());
    localDateTime = localDateTime.plusHours(sla.getHours());
    ticket.setDeadlineDateT(localDateTime);
  }

  /**
   * Calculate deadline date & time based on only working days if workingDays field in SLA is
   * checked.
   *
   * @param fromDate
   * @param employee
   * @param days
   * @throws AxelorException
   */
  protected void calculateWorkingDays(LocalDateTime fromDate, Company company, int days)
      throws AxelorException {

    if (weeklyPlanningService.getWorkingDayValueInDays(
                company.getWeeklyPlanning(), fromDate.toLocalDate())
            == 0
        || publicHolidayService.checkPublicHolidayDay(
            fromDate.toLocalDate(), company.getPublicHolidayEventsPlanning())) {

      fromDate = fromDate.plusDays(1);
      this.calculateWorkingDays(fromDate, company, days);

    } else {
      toDate = fromDate;
      days--;
      if (days != 0) {
        fromDate = fromDate.plusDays(1);
        this.calculateWorkingDays(fromDate, company, days);
      }
    }
  }

  /** Check if SLA is completed or not. */
  @Override
  public void checkSLAcompleted(Ticket ticket) {

    if (ticket.getSlaPolicy() != null) {
      LocalDateTime currentDate = LocalDateTime.now();
      LocalDateTime deadlineDateT = ticket.getDeadlineDateT();

      ticket.setIsSlaCompleted(
          ticket.getStatusSelect() >= ticket.getSlaPolicy().getReachStageSelect()
              && (currentDate.isBefore(deadlineDateT) || currentDate.isEqual(deadlineDateT)));
    }
  }

  /** Ticket assign to the current user. */
  @Override
  @Transactional
  public void assignToMeTicket(Long id, List<?> ids) {

    if (id != null) {
      Ticket ticket = ticketRepo.find(id);
      ticket.setAssignedToUser(AuthUtils.getUser());
      ticketRepo.save(ticket);

    } else if (!ids.isEmpty()) {

      for (Ticket ticket : ticketRepo.all().filter("id in ?1", ids).fetch()) {
        ticket.setAssignedToUser(AuthUtils.getUser());
        ticketRepo.save(ticket);
      }
    }
  }
}

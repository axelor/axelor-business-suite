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
package com.axelor.apps.hr.service;

import com.axelor.apps.base.db.DayPlanning;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.hr.db.EmploymentAmendment;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.db.Template;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.db.repo.TemplateRepository;
import com.axelor.apps.message.service.MessageService;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class EmploymentAmendmentServiceImpl implements EmploymentAmendmentService {

  @Inject protected TemplateMessageService templateMessageService;
  @Inject protected MessageService messageService;
  @Inject protected HRConfigService hrConfigService;
  @Inject protected EmploymentContractValvitalService employmentContractService;

  @Inject protected TemplateRepository templateRepo;
  @Inject protected UserRepository userRepo;
  @Inject protected EmailAddressRepository emailAddressRepo;
  @Inject protected RoleRepository roleRepo;

  @Override
  public void sendEmailOnActive(EmploymentAmendment employmentAmendment) throws Exception {
    Set<EmailAddress> emailAddressSet =
        employmentContractService.getEmailAddress(
            "?1 MEMBER OF self.roles OR ?2 MEMBER OF self.roles",
            roleRepo.findByName("Valvital Admin"),
            roleRepo.findByName("Paie"));
    if (ObjectUtils.isEmpty(emailAddressSet)) {
      return;
    }
    Template template =
        hrConfigService
            .getHRConfig(employmentAmendment.getPayCompany())
            .getValidationDemandAmendmentTemplate();
    if (template != null) {
      Message message = templateMessageService.generateMessage(employmentAmendment, template);
      message.setToEmailAddressSet(emailAddressSet);
      messageService.sendMessage(message);
    }
  }

  @Override
  public void sendEmailOnValidate(EmploymentAmendment employmentAmendment) throws Exception {
    Set<EmailAddress> emailAddressSet =
        employmentContractService.getEmailAddress(
            "?1 MEMBER OF self.roles OR ?2 MEMBER OF self.roles",
            roleRepo.findByName("Valvital Admin"),
            roleRepo.findByName("Paie"));
    if (ObjectUtils.isEmpty(emailAddressSet)) {
      return;
    }
    Template template =
        hrConfigService
            .getHRConfig(employmentAmendment.getPayCompany())
            .getOverBudgetAmendmentTemplate();
    if (template != null) {
      Message message = templateMessageService.generateMessage(employmentAmendment, template);
      message.setToEmailAddressSet(emailAddressSet);
      messageService.sendMessage(message);
    }
  }

  @Override
  public void sendEmailOnRefuse(EmploymentAmendment employmentAmendment) throws Exception {
    Set<EmailAddress> emailAddressSet =
        employmentContractService.getEmailAddress(
            "self.group.code IN ?1", Arrays.asList("ADM-V", "PAI-V"));
    if (ObjectUtils.isEmpty(emailAddressSet)) {
      return;
    }
    Template template =
        hrConfigService
            .getHRConfig(employmentAmendment.getPayCompany())
            .getValidationDenialAmendmentTemplate();
    if (template != null) {
      Message message = templateMessageService.generateMessage(employmentAmendment, template);
      message.setToEmailAddressSet(emailAddressSet);
      messageService.sendMessage(message);
    }
  }

  protected EmailAddress getEmailAddress(String recipient) {
    EmailAddress emailAddress = emailAddressRepo.findByAddress(recipient);
    if (emailAddress == null) {
      emailAddress = new EmailAddress();
      emailAddress.setAddress(recipient);
    }

    return emailAddress;
  }

  @Override
  public BigDecimal computeWeeklyDuration(EmploymentAmendment amendment) {
    Set<WeeklyPlanning> weeklyPlannings = amendment.getWeeklyPlanning();
    if (weeklyPlannings.size() == 0) {
      return BigDecimal.ZERO;
    }
    int weekCount = CollectionUtils.isNotEmpty(weeklyPlannings) ? 0 : 1;
    int minutes = 0;
    for (WeeklyPlanning weeklyPlanning : weeklyPlannings) {
      weekCount += 1;
      for (DayPlanning dayPlanning : weeklyPlanning.getWeekDays()) {
        LocalTime temps;
        if (dayPlanning.getMorningFrom() != null && dayPlanning.getMorningTo() != null) {
          temps =
              dayPlanning
                  .getMorningTo()
                  .minusHours(dayPlanning.getMorningFrom().getHour())
                  .minusMinutes(dayPlanning.getMorningFrom().getMinute());
          minutes += temps.getHour() * 60 + temps.getMinute();
        }
        if (dayPlanning.getAfternoonFrom() != null && dayPlanning.getAfternoonTo() != null) {
          temps =
              dayPlanning
                  .getAfternoonTo()
                  .minusHours(dayPlanning.getAfternoonFrom().getHour())
                  .minusMinutes(dayPlanning.getAfternoonFrom().getMinute());
          minutes += temps.getHour() * 60 + temps.getMinute();
        }
      }
    }
    if (weekCount == 0) {
      return BigDecimal.ZERO;
    }
    minutes = minutes / weekCount;
    int hours = minutes / 60;
    minutes = minutes % 60;
    float min = (float) minutes * 10 / 600;
    return new BigDecimal(hours + min);
  }
}

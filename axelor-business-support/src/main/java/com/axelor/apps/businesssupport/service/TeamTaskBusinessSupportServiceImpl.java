/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.businesssupport.service;

import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businessproject.service.TeamTaskBusinessProjectServiceImpl;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import java.time.LocalDate;

public class TeamTaskBusinessSupportServiceImpl extends TeamTaskBusinessProjectServiceImpl {

  @Inject
  public TeamTaskBusinessSupportServiceImpl(
      TeamTaskRepository teamTaskRepo,
      PriceListLineRepository priceListLineRepository,
      PriceListService priceListService) {
    super(teamTaskRepo, priceListLineRepository, priceListService);
  }

  @Override
  protected void setModuleFields(TeamTask teamTask, LocalDate date, TeamTask newTeamTask) {
    super.setModuleFields(teamTask, date, newTeamTask);

    // Module 'business support' fields
    newTeamTask.setAssignment(TeamTaskRepository.ASSIGNMENT_PROVIDER);
  }

  @Override
  protected void updateModuleFields(TeamTask teamTask, TeamTask nextTeamTask) {
    super.updateModuleFields(teamTask, nextTeamTask);

    // Module 'business support' fields
    nextTeamTask.setAssignment(TeamTaskRepository.ASSIGNMENT_PROVIDER);
    nextTeamTask.setIsPrivate(teamTask.getIsPrivate());
    nextTeamTask.setTargetVersion(teamTask.getTargetVersion());
  }
}

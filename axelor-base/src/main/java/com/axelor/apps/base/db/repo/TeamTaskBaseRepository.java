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
package com.axelor.apps.base.db.repo;

import com.axelor.apps.base.db.Repetition;
import com.axelor.apps.base.service.TeamTaskService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import javax.persistence.PersistenceException;

public class TeamTaskBaseRepository extends TeamTaskRepository {

  @Override
  public TeamTask save(TeamTask teamTask) {
    TeamTaskService teamTaskService = Beans.get(TeamTaskService.class);

    if (teamTask.getDoApplyToAllNextTasks()
        && teamTask.getNextTeamTask() != null
        && teamTask.getHasDateOrRepetitionChanged()) {
      // remove next tasks
      teamTaskService.removeNextTasks(teamTask);

      // regenerate new tasks
      teamTask.setIsFirst(true);
    }

    Repetition repetition = teamTask.getRepetition();
    if (repetition != null && teamTask.getIsFirst() && teamTask.getNextTeamTask() == null) {
      if (teamTask.getTaskDate() != null) {
        if (teamTask.getRepetitionEndDate().isBefore(teamTask.getTaskDate())) {
          throw new PersistenceException(
              I18n.get("Repetition end date cannot be before task date."));
        }
      } else {
        throw new PersistenceException(I18n.get("Please fill in task date."));
      }

      teamTaskService.generateTasks(teamTask, repetition);
    }

    if (teamTask.getDoApplyToAllNextTasks()) {
      teamTaskService.updateNextTask(teamTask);
    }

    teamTask.setDoApplyToAllNextTasks(false);
    teamTask.setHasDateOrRepetitionChanged(false);

    return super.save(teamTask);
  }
}

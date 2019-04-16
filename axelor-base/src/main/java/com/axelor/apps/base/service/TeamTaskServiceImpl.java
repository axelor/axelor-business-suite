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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Repetition;
import com.axelor.apps.base.db.repo.RepetitionRepository;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.inject.Beans;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TeamTaskServiceImpl implements TeamTaskService {

  protected TeamTaskRepository teamTaskRepo;

  @Inject
  public TeamTaskServiceImpl(TeamTaskRepository teamTaskRepo) {
    this.teamTaskRepo = teamTaskRepo;
  }

  @Override
  @Transactional
  public void generateTasks(TeamTask teamTask, Repetition repetition) {
    List<LocalDate> taskDates =
        Beans.get(RepetitionService.class)
            .getDates(repetition, teamTask.getTaskDate(), teamTask.getRepetitionEndDate());
    taskDates.sort(LocalDate::compareTo);

    taskDates.removeIf(date -> date.equals(teamTask.getTaskDate()));

    // limit how many TeamTask will be generated at once
    Integer limitNumberTasksGenerated =
        Beans.get(AppBaseService.class).getAppBase().getLimitNumberTasksGenerated();
    if (taskDates.size() > limitNumberTasksGenerated) {
      taskDates = taskDates.subList(0, limitNumberTasksGenerated);
    }

    TeamTask lastTask = teamTask;
    for (LocalDate date : taskDates) {
      TeamTask newTeamTask = teamTaskRepo.copy(teamTask, false);
      setModuleFields(teamTask, date, newTeamTask);
      teamTaskRepo.save(newTeamTask);

      lastTask.setNextTeamTask(newTeamTask);
      teamTaskRepo.save(lastTask);
      lastTask = newTeamTask;
    }
  }

  protected void setModuleFields(TeamTask teamTask, LocalDate date, TeamTask newTeamTask) {
    newTeamTask.setIsFirst(false);
    newTeamTask.setHasDateOrRepetitionChanged(false);
    newTeamTask.setDoApplyToAllNextTasks(false);
    newTeamTask.setRepetition(
        Beans.get(RepetitionRepository.class).copy(teamTask.getRepetition(), false));
    newTeamTask.setTaskDate(date);
    newTeamTask.setTaskDeadline(date);
    newTeamTask.setNextTeamTask(null);
  }

  @Override
  @Transactional
  public void updateNextTask(TeamTask teamTask) {
    TeamTask nextTeamTask = teamTask.getNextTeamTask();
    if (nextTeamTask != null) {
      updateModuleFields(teamTask, nextTeamTask);

      teamTaskRepo.save(nextTeamTask);
      updateNextTask(nextTeamTask);
    }
  }

  protected void updateModuleFields(TeamTask teamTask, TeamTask nextTeamTask) {
    nextTeamTask.setName(teamTask.getName());
    nextTeamTask.setTeam(teamTask.getTeam());
    nextTeamTask.setPriority(teamTask.getPriority());
    nextTeamTask.setStatus(teamTask.getStatus());
    nextTeamTask.setTaskDuration(teamTask.getTaskDuration());
    nextTeamTask.setAssignedTo(teamTask.getAssignedTo());
    nextTeamTask.setDescription(teamTask.getDescription());
  }

  @Override
  @Transactional
  public void removeNextTasks(TeamTask teamTask) {
    List<TeamTask> teamTasks = getAllNextTasks(teamTask);
    teamTask.setNextTeamTask(null);
    teamTask.setHasDateOrRepetitionChanged(false);
    teamTaskRepo.save(teamTask);

    for (TeamTask teamTaskToRemove : teamTasks) {
      teamTaskRepo.remove(teamTaskToRemove);
    }
  }

  /** Returns next tasks from given {@link TeamTask}. */
  public List<TeamTask> getAllNextTasks(TeamTask teamTask) {
    List<TeamTask> teamTasks = new ArrayList<>();

    TeamTask current = teamTask;
    while (current.getNextTeamTask() != null) {
      current = current.getNextTeamTask();
      teamTasks.add(current);
    }

    for (TeamTask tt : teamTasks) {
      tt.setNextTeamTask(null);
      teamTaskRepo.save(tt);
    }

    return teamTasks;
  }
}

/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.hr.service.project;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectActivity;
import com.axelor.apps.project.db.ProjectTask;
import com.axelor.apps.project.db.repo.ProjectActivityRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.apps.project.db.repo.ProjectStatusRepository;
import com.axelor.apps.project.db.repo.ProjectTaskSectionRepository;
import com.axelor.apps.project.service.ProjectActivityServiceImpl;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ProjectActivityHRServiceImpl extends ProjectActivityServiceImpl
    implements ProjectActivityHRService {

  @Inject
  public ProjectActivityHRServiceImpl(
      ProjectActivityRepository projectActivityRepo,
      ProjectRepository projectRepo,
      ProjectStatusRepository projectStatusRepo,
      ProjectTaskSectionRepository projectTaskSectionRepo) {
    super(projectActivityRepo, projectRepo, projectStatusRepo, projectTaskSectionRepo);
  }

  @Override
  public void createTimesheetLineProjectActivity(Map<String, Object> dataMap) {
    TimesheetLine timesheetLine = getBean(dataMap, TimesheetLine.class);
    Project project = timesheetLine.getProject();
    ProjectActivity projectActivity = getDefaultTimesheetActivity(dataMap, project, timesheetLine);
    if (projectActivity != null) {
      projectActivity.setRecordTitle(getFullName(timesheetLine));
      projectActivityRepo.save(projectActivity);
    }
  }

  protected String getFullName(TimesheetLine timesheetLine) {
    if (timesheetLine.getFullName() == null) {
      User user = JPA.find(User.class, timesheetLine.getUser().getId());
      return user.getFullName()
          + " "
          + timesheetLine.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    return timesheetLine.getFullName();
  }

  protected ProjectActivity getDefaultTimesheetActivity(
      Map<String, Object> dataMap, Project project, TimesheetLine timesheetLine) {
    String activity = getTimesheetActivity(dataMap, timesheetLine);
    if (StringUtils.isBlank(activity)) {
      return null;
    }
    ProjectActivity projectActivity = getDefaultActivity(project, timesheetLine);
    projectActivity.setActivity(activity);
    return projectActivity;
  }

  protected String getTimesheetActivity(
      Map<String, Object> newDataMap, TimesheetLine timesheetLine) {
    if (timesheetLine.getId() == null) {
      return createTimesheetLineActivity(
          Mapper.toBean(TimesheetLine.class, newDataMap), timesheetLine.getTimesheet());
    }

    Mapper mapper = Mapper.of(timesheetLine.getClass());
    Map<String, Object> oldDataMap = Mapper.toMap(timesheetLine);
    for (Map.Entry<String, Object> me : newDataMap.entrySet()) {
      String key = me.getKey();
      Property property = mapper.getProperty(key);
      if (oldDataMap.containsKey(key) && getFieldNames().contains(property.getName())) {
        Object oldValue = oldDataMap.get(key);
        Object newValue = toProxy(property, me.getValue());
        if (!isEqual(oldValue, newValue)) {
          return createTimesheetLineActivity(
              Mapper.toBean(TimesheetLine.class, newDataMap), timesheetLine.getTimesheet());
        }
      }
    }

    return null;
  }

  protected String createTimesheetLineActivity(TimesheetLine timesheetLine, Timesheet timesheet) {
    StringBuilder activity = new StringBuilder();
    BigDecimal duration = timesheetLine.getDuration();
    activity.append(
        timesheetLine.getDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + " ");
    if (timesheetLine.getProject() != null) {
      activity.append(
          JPA.find(Project.class, timesheetLine.getProject().getId()).getFullName() + " - ");
    }

    activity.append(getDurationWithUnit(timesheet, duration));
    activity.append(getTaskOrActivity(timesheetLine));

    if (StringUtils.notBlank(timesheetLine.getComments())) {
      activity.append("\n" + timesheetLine.getComments());
    }
    return activity.toString();
  }

  protected String getTaskOrActivity(TimesheetLine timesheetLine) {
    StringBuilder activity = new StringBuilder();
    if (timesheetLine.getProjectTask() != null) {
      Long taskId = timesheetLine.getProjectTask().getId();
      ProjectTask task = JPA.find(ProjectTask.class, taskId);
      activity.append(" (#" + taskId);
      activity.append(" (" + task.getStatus().getName() + "): ");
      activity.append(task.getName() + ")");
    } else if (timesheetLine.getProduct() != null) {
      Long productId = timesheetLine.getProduct().getId();
      activity.append(" (#" + productId);
      activity.append(" " + JPA.find(Product.class, productId).getFullName() + ")");
    }
    return activity.toString();
  }

  protected String getDurationWithUnit(Timesheet timesheet, BigDecimal duration) {
    String durationWithUnit = "";
    if (timesheet != null) {
      String timeLoggingPreferenceSelect = timesheet.getTimeLoggingPreferenceSelect();
      if (timeLoggingPreferenceSelect == null) {
        return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " hours" : duration + " hour";
      }
      switch (timeLoggingPreferenceSelect) {
        case TimesheetRepository.TIME_LOGGING_DAYS:
          return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " days" : duration + " day";
        case TimesheetRepository.TIME_LOGGING_HOURS:
          return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " hours" : duration + " hour";
        case TimesheetRepository.TIME_LOGGING_MINUTES:
          return duration.compareTo(BigDecimal.ONE) == 1
              ? duration + " minutes"
              : duration + " minute";
      }
    } else {
      Employee employee = AuthUtils.getUser().getEmployee();
      if (employee == null) {
        return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " hours" : duration + " hour";
      }
      switch (employee.getTimeLoggingPreferenceSelect()) {
        case EmployeeRepository.TIME_PREFERENCE_DAYS:
          return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " days" : duration + " day";
        case EmployeeRepository.TIME_PREFERENCE_HOURS:
          return duration.compareTo(BigDecimal.ONE) == 1 ? duration + " hours" : duration + " hour";
        case EmployeeRepository.TIME_PREFERENCE_MINUTES:
          return duration.compareTo(BigDecimal.ONE) == 1
              ? duration + " minutes"
              : duration + " minute";
      }
    }
    return durationWithUnit;
  }

  protected List<String> getFieldNames() {
    return ImmutableList.of("date", "project", "projectTask", "duration", "comments");
  }
}

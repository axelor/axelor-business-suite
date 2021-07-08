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
package com.axelor.apps.base.service.unit.testing;

import com.axelor.apps.base.db.GroupTest;
import com.axelor.apps.base.db.GroupTestLine;
import com.axelor.apps.base.db.UnitTest;
import com.axelor.apps.base.db.repo.UnitTestRepository;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityTransaction;

public class GroupTestServiceImpl implements GroupTestService {

  protected GroupTestLineService groupTestLineService;
  protected UnitTestRepository unitTestRepository;

  @Inject
  public GroupTestServiceImpl(
      GroupTestLineService groupTestLineService, UnitTestRepository unitTestRepository) {
    this.groupTestLineService = groupTestLineService;
    this.unitTestRepository = unitTestRepository;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public void generate(GroupTest groupTest) {
    for (GroupTestLine groupTestLine : getSortedGroupTestLines(groupTest)) {
      groupTestLineService.generateScript(groupTestLine);
    }
  }

  @Override
  public String execute(GroupTest groupTest) {
    String result = executeInRollbackMode(groupTest);
    save(groupTest);
    return result;
  }

  protected String executeInRollbackMode(GroupTest groupTest) {
    Map<String, String> resultDataMap = new LinkedHashMap<>();
    final EntityTransaction transaction = JPA.em().getTransaction();
    if (!transaction.isActive()) {
      transaction.begin();
    }
    for (GroupTestLine groupTestLine : getSortedGroupTestLines(groupTest)) {
      String name = groupTestLine.getUnitTest().getName();
      String result = groupTestLineService.execute(groupTestLine);
      resultDataMap.put(name, result);
    }
    transaction.rollback();
    return processResult(resultDataMap);
  }

  @Transactional(rollbackOn = Exception.class)
  protected GroupTest save(GroupTest groupTest) {
    for (GroupTestLine groupTestLine : groupTest.getGroupTestLineList()) {
      UnitTest unitTest = groupTestLine.getUnitTest();
      UnitTest unitTestUpdated = unitTestRepository.find(unitTest.getId());
      unitTestUpdated.setTestRunOn(unitTest.getTestRunOn());
      unitTestUpdated.setTestResult(unitTest.getTestResult());
      unitTestRepository.save(unitTestUpdated);
    }
    return groupTest;
  }

  protected List<GroupTestLine> getSortedGroupTestLines(GroupTest groupTest) {
    List<GroupTestLine> groupTestLineList = groupTest.getGroupTestLineList();
    groupTestLineList.sort(Comparator.comparingInt(GroupTestLine::getSequence));
    return groupTestLineList;
  }

  protected String processResult(Map<String, String> resultDataMap) {
    StringBuilder resultBuilder = new StringBuilder();
    resultBuilder.append("<ul>");
    for (Map.Entry<String, String> entry : resultDataMap.entrySet()) {
      resultBuilder.append("<li>");
      String name = String.format("<b>%s</b><br/>", entry.getKey());
      String result = String.format("<span>%s</span>", entry.getValue());
      resultBuilder.append(name);
      resultBuilder.append(result);
      resultBuilder.append("</li><br/>");
    }
    resultBuilder.append("</ul>");
    return resultBuilder.toString();
  }
}

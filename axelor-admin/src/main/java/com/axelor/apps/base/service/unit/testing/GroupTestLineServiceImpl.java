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
import com.axelor.apps.base.db.repo.GroupTestLineRepository;
import com.axelor.apps.base.db.repo.UnitTestRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;

public class GroupTestLineServiceImpl implements GroupTestLineService {

  protected UnitTestService unitTestService;
  protected UnitTestRepository unitTestRepo;
  protected GroupTestLineRepository groupTestLineRepo;

  @Inject
  public GroupTestLineServiceImpl(
      UnitTestService unitTestService,
      UnitTestRepository unitTestRepo,
      GroupTestLineRepository groupTestLineRepo) {
    this.unitTestService = unitTestService;
    this.unitTestRepo = unitTestRepo;
    this.groupTestLineRepo = groupTestLineRepo;
  }

  @Override
  @Transactional
  public GroupTestLine create(GroupTest groupTest, UnitTest unitTest) {
    GroupTestLine testLine = new GroupTestLine();
    testLine.setUnitTest(unitTest);
    testLine.setGroupTest(groupTest);
    return groupTestLineRepo.save(testLine);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void generateScript(GroupTestLine groupTestLine) {
    UnitTest unitTest = groupTestLine.getUnitTest();
    String testScript = unitTestService.generateTestScript(unitTest);
    unitTest.setTestScript(testScript);
    unitTestRepo.save(unitTest);
  }

  @Override
  public String execute(GroupTestLine groupTestLine) {
    UnitTest unitTest = groupTestLine.getUnitTest();
    String testResult = unitTestService.executeTestScript(unitTest);
    unitTest.setTestResult(testResult);
    unitTest.setTestRunOn(LocalDateTime.now());
    groupTestLine.setUnitTest(unitTest);
    return testResult;
  }
}

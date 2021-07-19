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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.GroupTest;
import com.axelor.apps.base.db.repo.GroupTestRepository;
import com.axelor.apps.base.db.repo.UnitTestRepository;
import com.axelor.apps.base.service.unit.testing.GroupTestLineService;
import com.axelor.apps.base.service.unit.testing.GroupTestService;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.List;
import java.util.Map;

public class GroupTestController {

  private static final String FIELD_TEST_RESULT = "testResult";

  public void generate(ActionRequest request, ActionResponse response) {
    try {
      GroupTest groupTest = request.getContext().asType(GroupTest.class);
      groupTest = Beans.get(GroupTestRepository.class).find(groupTest.getId());
      Beans.get(GroupTestService.class).generate(groupTest);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void execute(ActionRequest request, ActionResponse response) {
    try {
      GroupTest groupTest = request.getContext().asType(GroupTest.class);
      groupTest = Beans.get(GroupTestRepository.class).find(groupTest.getId());
      String result = Beans.get(GroupTestService.class).execute(groupTest);
      response.setValue(FIELD_TEST_RESULT, result);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  @SuppressWarnings("unchecked")
  public void addUnitTests(ActionRequest request, ActionResponse response) {
    List<Map<String, Object>> unitTestSetMap =
        (List<Map<String, Object>>) request.getContext().get("unitTestSet");
    GroupTest groupTest = request.getContext().asType(GroupTest.class);
    groupTest = Beans.get(GroupTestRepository.class).find(groupTest.getId());
    UnitTestRepository unitTestRepo = Beans.get(UnitTestRepository.class);
    GroupTestLineService groupTestLineService = Beans.get(GroupTestLineService.class);
    for (Map<String, Object> map : unitTestSetMap) {
      Long id = Long.valueOf(map.get("id").toString());
      groupTestLineService.create(groupTest, unitTestRepo.find(id));
    }
    response.setReload(true);
  }
}

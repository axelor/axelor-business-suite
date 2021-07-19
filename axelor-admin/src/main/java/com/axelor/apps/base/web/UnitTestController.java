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

import com.axelor.apps.base.db.UnitTest;
import com.axelor.apps.base.db.repo.UnitTestRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.apps.base.service.app.AppService;
import com.axelor.apps.base.service.unit.testing.UnitTestExporterService;
import com.axelor.apps.base.service.unit.testing.UnitTestImporterService;
import com.axelor.apps.base.service.unit.testing.UnitTestService;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Throwables;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UnitTestController {

  private static final String FIELD_TEST_RUN_ON = "testRunOn";
  private static final String FIELD_TEST_SCRIPT = "testScript";
  private static final String FIELD_TEST_RESULT = "testResult";

  public void generate(ActionRequest request, ActionResponse response) {
    try {
      UnitTest test = request.getContext().asType(UnitTest.class);
      test = Beans.get(UnitTestRepository.class).find(test.getId());
      String testScipt = Beans.get(UnitTestService.class).generateTestScript(test);
      response.setValue(FIELD_TEST_SCRIPT, testScipt);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void execute(ActionRequest request, ActionResponse response) {
    String result = null;
    try {
      UnitTest test = request.getContext().asType(UnitTest.class);
      test = Beans.get(UnitTestRepository.class).find(test.getId());
      result = Beans.get(UnitTestService.class).executeTestScriptWithRollback(test);
    } catch (Exception e) {
      result =
          "<span style='color:red'><pre>" + Throwables.getStackTraceAsString(e) + "</pre></span>";
      TraceBackService.trace(e);
    } finally {
      response.setValue(FIELD_TEST_RESULT, result);
      response.setValue(FIELD_TEST_RUN_ON, LocalDateTime.now());
    }
  }

  @SuppressWarnings("unchecked")
  public void importTests(ActionRequest request, ActionResponse response) {
    try {
      Map<String, Object> metaFileMap = (Map<String, Object>) request.getContext().get("metaFile");
      final Long id = Long.valueOf(metaFileMap.get("id").toString());
      MetaFile importFile = Beans.get(MetaFileRepository.class).find(id);
      Beans.get(UnitTestImporterService.class).importTests(importFile);
      response.setFlash(I18n.get(IExceptionMessages.UNIT_TEST_IMPORT_SUCCESS));
      response.setCanClose(true);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  @SuppressWarnings("unchecked")
  public void exportTests(ActionRequest request, ActionResponse response) {
    try {
      List<Long> idsList = (List<Long>) request.getContext().get("_ids");

      if (ObjectUtils.isEmpty(idsList)) {
        response.setError(I18n.get(IExceptionMessages.UNIT_TEST_EXPORT_RECORD_NOT_SPECIFIED));
        return;
      }

      UnitTestRepository unitTestRepo = Beans.get(UnitTestRepository.class);
      List<UnitTest> testList = unitTestRepo.all().filter("self.id in ?1", idsList).fetch();

      File exportFile = Beans.get(UnitTestExporterService.class).exportTests(testList);
      String relativePath =
          Paths.get(Beans.get(AppService.class).getDataExportDir())
              .relativize(exportFile.toPath())
              .toString();
      response.setExportFile(relativePath);
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}

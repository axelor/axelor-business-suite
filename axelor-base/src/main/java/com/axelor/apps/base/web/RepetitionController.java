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
package com.axelor.apps.base.web;

import com.axelor.apps.base.db.Repetition;
import com.axelor.apps.base.service.RepetitionService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;

public class RepetitionController {

  public void computeSummary(ActionRequest request, ActionResponse response) {
    Repetition repetition = request.getContext().asType(Repetition.class);
    response.setValue("summary", Beans.get(RepetitionService.class).computeSummary(repetition));
  }

  public void updateFrequencyWord(ActionRequest request, ActionResponse response) {
    Repetition repetition = request.getContext().asType(Repetition.class);
    response.setValue(
        "$frequencyWord", Beans.get(RepetitionService.class).getFrequencyWord(repetition));
  }

  public void sortDaySelect(ActionRequest request, ActionResponse response) {
    Repetition repetition = request.getContext().asType(Repetition.class);
    response.setValue(
        "daySelect", Beans.get(RepetitionService.class).sort(repetition, "daySelect"));
  }

  public void sortDayOfMonthSelect(ActionRequest request, ActionResponse response) {
    Repetition repetition = request.getContext().asType(Repetition.class);
    response.setValue(
        "dayOfMonthSelect",
        Beans.get(RepetitionService.class).sort(repetition, "dayOfMonthSelect"));
  }

  public void sortMonthSelect(ActionRequest request, ActionResponse response) {
    Repetition repetition = request.getContext().asType(Repetition.class);
    response.setValue(
        "monthSelect", Beans.get(RepetitionService.class).sort(repetition, "monthSelect"));
  }
}

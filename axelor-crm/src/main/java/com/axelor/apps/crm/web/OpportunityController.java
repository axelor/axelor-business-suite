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
package com.axelor.apps.crm.web;

import com.axelor.apps.base.service.MapService;
import com.axelor.apps.crm.db.Opportunity;
import com.axelor.apps.crm.db.OpportunityStatus;
import com.axelor.apps.crm.db.repo.OpportunityRepository;
import com.axelor.apps.crm.db.repo.OpportunityStatusRepository;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.crm.service.OpportunityService;
import com.axelor.auth.AuthUtils;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaStore;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class OpportunityController {

  @SuppressWarnings("rawtypes")
  public void assignToMe(ActionRequest request, ActionResponse response) {

    OpportunityService opportunityService = Beans.get(OpportunityService.class);
    OpportunityRepository opportunityRepository = Beans.get(OpportunityRepository.class);
    if (request.getContext().get("id") != null) {
      Opportunity opportunity = opportunityRepository.find((Long) request.getContext().get("id"));
      opportunity.setUser(AuthUtils.getUser());
      opportunityService.saveOpportunity(opportunity);
    } else if (ObjectUtils.notEmpty(request.getContext().get("_ids"))) {
      for (Opportunity opportunity :
          opportunityRepository
              .all()
              .filter("id in ?1", request.getContext().get("_ids"))
              .fetch()) {
        opportunity.setUser(AuthUtils.getUser());
        opportunityService.saveOpportunity(opportunity);
      }
    } else {
      response.setNotify(com.axelor.apps.base.exceptions.IExceptionMessage.RECORD_NONE_SELECTED);
      return;
    }

    response.setReload(true);
  }

  public void showOpportunitiesOnMap(ActionRequest request, ActionResponse response) {
    try {
      response.setView(
          ActionView.define(I18n.get("Opportunities"))
              .add("html", Beans.get(MapService.class).getMapURI("opportunity"))
              .map());
    } catch (Exception e) {
      TraceBackService.trace(e);
    }
  }

  public void createClient(ActionRequest request, ActionResponse response) {
    try {
      Opportunity opportunity = request.getContext().asType(Opportunity.class);
      opportunity = Beans.get(OpportunityRepository.class).find(opportunity.getId());
      Beans.get(OpportunityService.class).createClientFromLead(opportunity);
      response.setReload(true);
    } catch (Exception e) {
      TraceBackService.trace(e);
      response.setError(e.getMessage());
    }
  }

  public void setStageNew(ActionRequest request, ActionResponse response) {
    try {
      OpportunityStatus status =
          findStatusByTypeSelect(OpportunityStatusRepository.TECHNICAL_TYPE_NEW);
      response.setValue("opportunityStatus", status);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void setStageNegotiation(ActionRequest request, ActionResponse response) {
    try {
      OpportunityStatus status =
          findStatusByTypeSelect(OpportunityStatusRepository.TECHNICAL_TYPE_NEGOTIATION);
      response.setValue("opportunityStatus", status);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void setStageClosedWon(ActionRequest request, ActionResponse response) {
    try {
      OpportunityStatus status =
          findStatusByTypeSelect(OpportunityStatusRepository.TECHNICAL_TYPE_CLOSED_WON);
      response.setValue("opportunityStatus", status);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  public void setStageClosedLost(ActionRequest request, ActionResponse response) {
    try {
      OpportunityStatus status =
          findStatusByTypeSelect(OpportunityStatusRepository.TECHNICAL_TYPE_CLOSED_LOST);
      response.setValue("opportunityStatus", status);
    } catch (AxelorException e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }

  private OpportunityStatus findStatusByTypeSelect(Integer technicalTypeSelect)
      throws AxelorException {
    final String selectionName = "crm.opportunity.status.technical.type.select";
    OpportunityStatus status =
        Beans.get(OpportunityStatusRepository.class).findByTechnicalTypeSelect(technicalTypeSelect);
    if (status == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.OPPORTUNITY_STATUS_NOT_FOUND),
          MetaStore.getSelectionItem(selectionName, Integer.toString(technicalTypeSelect))
              .getLocalizedTitle());
    }
    return status;
  }
}

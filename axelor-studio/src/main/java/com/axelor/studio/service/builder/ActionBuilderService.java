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
package com.axelor.studio.service.builder;

import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.studio.db.ActionBuilder;
import com.axelor.studio.db.ActionBuilderView;
import com.axelor.studio.db.repo.ActionBuilderRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionBuilderService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private ActionViewBuilderService actionViewBuilderService;

  @Inject private ActionScriptBuilderService actionScriptBuilderService;

  @Inject private ActionEmailBuilderService actionEmailBuilderService;

  @Transactional
  public MetaAction build(ActionBuilder builder) {

    if (builder == null) {
      return null;
    }

    log.debug("Processing action: {}, type: {}", builder.getName(), builder.getTypeSelect());

    if (Arrays.asList(
                ActionBuilderRepository.TYPE_SELECT_CREATE,
                ActionBuilderRepository.TYPE_SELECT_UPDATE)
            .contains(builder.getTypeSelect())
        && (builder.getLines() == null || builder.getLines().isEmpty())) {
      return null;
    }

    MetaAction metaAction = null;
    switch (builder.getTypeSelect()) {
      case ActionBuilderRepository.TYPE_SELECT_CREATE:
        metaAction = actionScriptBuilderService.build(builder);
        break;
      case ActionBuilderRepository.TYPE_SELECT_UPDATE:
        metaAction = actionScriptBuilderService.build(builder);
        break;
      case ActionBuilderRepository.TYPE_SELECT_SCRIPT:
        metaAction = actionScriptBuilderService.build(builder);
        break;
      case ActionBuilderRepository.TYPE_SELECT_VIEW:
        metaAction = actionViewBuilderService.build(builder);
        break;
      case ActionBuilderRepository.TYPE_SELECT_EMAIL:
        metaAction = actionEmailBuilderService.build(builder);
    }

    if (builder.getMetaModule() != null) {
      metaAction.setModule(builder.getMetaModule().getName());
    }

    MetaStore.clear();

    return metaAction;
  }

  public ActionBuilder setActionBuilderViews(
      ActionBuilder actionBuilder, String modelName, String formViewName, String gridViewName) {
    if (actionBuilder.getActionBuilderViews() == null) {
      actionBuilder.setActionBuilderViews(new ArrayList<>());
    }
    List<ActionBuilderView> actionBuilderViews = actionBuilder.getActionBuilderViews();
    if (formViewName != null) {
      setActionBuilderView("form", formViewName, actionBuilderViews);
    }
    if (gridViewName != null) {
      setActionBuilderView("grid", gridViewName, actionBuilderViews);
    }

    actionBuilder.setModel(modelName);
    return actionBuilder;
  }

  protected void setActionBuilderView(
      String viewType, String viewName, List<ActionBuilderView> actionBuilderViews) {
    ActionBuilderView actionBuilderView = new ActionBuilderView();
    actionBuilderView.setViewType(viewType);
    actionBuilderView.setViewName(viewName);
    actionBuilderViews.add(actionBuilderView);
  }
}

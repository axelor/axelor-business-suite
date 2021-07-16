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
package com.axelor.apps.base.recording;

import com.axelor.apps.base.db.Recording;
import com.axelor.apps.base.db.repo.RecordingRepository;
import com.axelor.apps.base.service.RecordingService;
import com.axelor.event.Observes;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import java.util.Map;
import javax.inject.Named;

public class RecordingListener {

  @SuppressWarnings("unchecked")
  public void onBeforeTransactionComplete(@Observes @Named(RequestEvent.SAVE) PostRequest event) {

    Recording recording =
        Beans.get(RecordingRepository.class).findByStatus(RecordingRepository.STATUS_START);

    if (recording != null) {

      MetaModel metaModel =
          Beans.get(MetaModelRepository.class)
              .all()
              .filter("self.fullName = ?", event.getRequest().getBeanClass().getName())
              .fetchOne();

      Object object = event.getResponse().getItem(0);

      if (object instanceof Map) {
        Map<String, Object> dataMap = (Map<String, Object>) object;
        Long recordId =
            Long.valueOf(dataMap.containsKey("id") ? dataMap.get("id").toString() : "0");

        Beans.get(RecordingService.class).addModelIdLog(recording, metaModel, recordId);
      }
    }
  }
}

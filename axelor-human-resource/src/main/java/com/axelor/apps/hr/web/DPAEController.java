package com.axelor.apps.hr.web;

import com.axelor.apps.hr.db.DPAE;
import com.axelor.apps.hr.db.repo.DPAERepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.dpae.DPAEService;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import java.util.List;
import java.util.stream.Collectors;

public class DPAEController {

  @SuppressWarnings("unchecked")
  public void send(ActionRequest request, ActionResponse response) throws AxelorException {
    Context context = request.getContext();

    try {
      DPAEService dpaeService = Beans.get(DPAEService.class);
      if (Beans.get(AppHumanResourceService.class).getAppEmployee().getTemplateDPAE() == null) {
        throw new AxelorException(
            DPAE.class,
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.DPAE_MISSING_TEMPLATE));
      }
      if (!ObjectUtils.isEmpty(request.getContext().get("_ids"))) {
        List<Long> ids =
            (List)
                (((List) context.get("_ids"))
                    .stream()
                    .filter(ObjectUtils::notEmpty)
                    .map(input -> Long.parseLong(input.toString()))
                    .collect(Collectors.toList()));
        MetaFile metafile = dpaeService.sendMultiple(ids);
        if (metafile != null) {
          response.setView(
              ActionView.define("Export")
                  .add(
                      "html",
                      "ws/rest/com.axelor.meta.db.MetaFile/"
                          + metafile.getId()
                          + "/content/download?v="
                          + metafile.getVersion())
                  .param("download", "true")
                  .map());
        }
      } else if (context.get("id") != null) {
        DPAE dpae = context.asType(DPAE.class);
        dpae = Beans.get(DPAERepository.class).find(dpae.getId());
        dpaeService.sendSingle(dpae);
        if (dpae.getMetaFile() != null) {
          response.setView(
              ActionView.define("Export")
                  .add(
                      "html",
                      "ws/rest/com.axelor.meta.db.MetaFile/"
                          + dpae.getMetaFile().getId()
                          + "/content/download?v="
                          + dpae.getMetaFile().getVersion())
                  .param("download", "true")
                  .map());
        }
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.DPAE_SEND_SELECT));
      }
      response.setReload(true);
      response.setFlash(I18n.get(IExceptionMessage.DPAE_SEND));
    } catch (Exception e) {
      TraceBackService.trace(response, e, ResponseMessageType.ERROR);
    }
  }
}

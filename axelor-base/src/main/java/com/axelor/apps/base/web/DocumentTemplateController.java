package com.axelor.apps.base.web;

import com.axelor.apps.base.db.DocumentTemplate;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DocumentTemplateController {
  private MetaSelectItemRepository metaSelectItemRepository;

  @Inject
  public DocumentTemplateController(MetaSelectItemRepository metaSelectItemRepository) {
    this.metaSelectItemRepository = metaSelectItemRepository;
  }

  public void getDefaultTitle(ActionRequest request, ActionResponse response) {
    DocumentTemplate template = request.getContext().asType(DocumentTemplate.class);
    if (!Strings.isNullOrEmpty(template.getType())) {
      response.setValue(
          "title",
          I18n.get(
              metaSelectItemRepository
                  .all()
                  .filter(
                      "self.select.name = ? and self.value = ?",
                      "base.document.template.type.select",
                      template.getType())
                  .fetchOne()
                  .getTitle()));
    }
  }
}

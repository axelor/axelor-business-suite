package com.axelor.apps.account.service.extract;

import com.axelor.rpc.Context;
import java.util.LinkedHashMap;

public interface ExtractContextMoveService {

  public LinkedHashMap<String, Object> getMapFromMoveWizardGenerateReverseForm(Context context);
}

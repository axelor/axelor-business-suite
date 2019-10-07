package com.axelor.apps.hr.service.dpae;

import com.axelor.apps.hr.db.DPAE;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaFile;
import java.io.IOException;
import java.util.List;
import javax.mail.MessagingException;

public interface DPAEService {

  public List<String> checkDPAEValidity(DPAE dpae);

  public void sendSingle(DPAE dpae)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException;

  public MetaFile sendMultiple(List<Long> dpaeIdList)
      throws AxelorException, IOException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException;
}

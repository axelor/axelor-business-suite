package com.axelor.apps.hr.service.sentdpae;

import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.message.db.Message;
import com.axelor.meta.db.MetaFile;
import java.util.List;

public interface SentDPAEService {

  public void createSentDPAE(Message message, MetaFile metaFile, List<Employee> employeeList);
}

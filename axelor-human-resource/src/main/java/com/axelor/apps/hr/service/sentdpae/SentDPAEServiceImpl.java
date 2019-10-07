package com.axelor.apps.hr.service.sentdpae;

import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.SentDPAE;
import com.axelor.apps.hr.db.repo.SentDPAERepository;
import com.axelor.apps.message.db.Message;
import com.axelor.meta.db.MetaFile;
import com.google.inject.persist.Transactional;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;

public class SentDPAEServiceImpl implements SentDPAEService {

  @Inject protected SentDPAERepository sentDPAERepo;

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Transactional
  @Override
  public void createSentDPAE(Message message, MetaFile metaFile, List<Employee> employeeList) {
    SentDPAE sentDPAE = new SentDPAE();
    sentDPAE.setMessage(message);
    sentDPAE.setMetaFile(metaFile);
    sentDPAE.setEmployeeList(new HashSet(employeeList));
    sentDPAERepo.save(sentDPAE);
  }
}

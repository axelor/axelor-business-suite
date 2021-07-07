package com.axelor.apps.hr.db.repo;

import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.hr.db.EmploymentAmendment;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.common.ObjectUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import javax.persistence.PersistenceException;

public class EmploymentAmendmentValvitalRepository extends EmploymentAmendmentRepository {

  @Inject protected SequenceService sequenceService;

  @Override
  public EmploymentAmendment save(EmploymentAmendment employmentAmendment) {
    if (employmentAmendment.getEmploymentAmendmentReference() == null) {
      String seq = sequenceService.getSequenceNumber(SequenceRepository.EMPLOYMENT_AMENDMENT);
      if (seq == null) {
        throw new PersistenceException(
            new AxelorException(
                TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
                I18n.get(IExceptionMessage.EMPLOYMENT_AMENDMENT_NO_SEQUENCE)));
      }
      employmentAmendment.setEmploymentAmendmentReference(
          (ObjectUtils.notEmpty(employmentAmendment.getPayCompany())
                  ? employmentAmendment.getPayCompany().getCode()
                  : "")
              + seq);
    }

    return super.save(employmentAmendment);
  }

  @Override
  public EmploymentAmendment copy(EmploymentAmendment entity, boolean deep) {
    entity.setEmploymentAmendmentReference(null);
    return super.copy(entity, deep);
  }
}

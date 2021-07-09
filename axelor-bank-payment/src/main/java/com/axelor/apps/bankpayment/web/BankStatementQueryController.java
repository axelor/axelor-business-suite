package com.axelor.apps.bankpayment.web;

import com.axelor.apps.bankpayment.db.BankStatementQuery;
import com.axelor.apps.bankpayment.db.repo.BankStatementQueryRepository;
import com.axelor.apps.bankpayment.db.repo.BankStatementRuleRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.i18n.I18n;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class BankStatementQueryController {

  protected BankStatementQueryRepository bankStatementQueryRepo;

  @Inject
  public BankStatementQueryController(BankStatementQueryRepository bankStatementQueryRepo) {
    this.bankStatementQueryRepo = bankStatementQueryRepo;
  }

  public void checkSequenceUnicity(ActionRequest request, ActionResponse response) {
    BankStatementQuery bankStatementQuery = request.getContext().asType(BankStatementQuery.class);
    int sequence = bankStatementQuery.getSequence();
    if (ObjectUtils.notEmpty(
        bankStatementQueryRepo.findBySequenceAndRuleType(
            sequence, BankStatementRuleRepository.RULE_TYPE_RECONCILIATION_AUTO)))
      response.setError(I18n.get("Sequence is already used"));
  }
}

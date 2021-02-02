package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.message.service.MailMessageService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import com.google.inject.servlet.RequestScoper;
import com.google.inject.servlet.ServletScopes;
import java.util.Collections;
import java.util.concurrent.Callable;

public class ClosePeriodCallableService implements Callable<Period> {

  private Period period;

  public void setPeriod(Period period) {
    this.period = period;
  }

  @Override
  public Period call() throws AxelorException {
    final RequestScoper scope = ServletScopes.scopeRequest(Collections.emptyMap());
    try (RequestScoper.CloseableScope ignored = scope.open()) {
      period = Beans.get(PeriodRepository.class).find(period.getId());
      closePeriodAndSendMessage();
      return period;
    } catch (Exception e) {
      onRunnerException(e);
      throw e;
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void closePeriodAndSendMessage() throws AxelorException {
    Beans.get(PeriodService.class).close(period);
    Beans.get(MailMessageService.class)
        .sendNotification(
            AuthUtils.getUser(),
            String.format(I18n.get(IExceptionMessage.PERIOD_CLOSING_MESSAGE), period.getName()),
            String.format(I18n.get(IExceptionMessage.PERIOD_CLOSING_MESSAGE), period.getName()),
            period.getId(),
            period.getClass());
  }

  @Transactional
  protected void onRunnerException(Exception e) {
    TraceBackService.trace(e);
    Beans.get(MailMessageService.class)
        .sendNotification(
            AuthUtils.getUser(),
            String.format(
                I18n.get(IExceptionMessage.PERIOD_CLOSING_EXCEPTION_MESSAGE), period.getName()),
            e.getMessage(),
            period.getId(),
            period.getClass());
  }
}

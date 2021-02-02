package com.axelor.apps.base.callable;

import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.exception.IExceptionMessage;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tool class to call specific callable services in a controller.
 *
 * @param <V> the type of the callable service.
 */
public class ControllerCallableTool<V> {

  /**
   * Run the given callable in a separate thread. display any occurring exception in the given
   * response. If the thread is not over before the timeout, send a notification to the user.
   *
   * @param callable a callable service
   * @param response a response available in a controller
   * @return what is returned by the service
   * @throws InterruptedException if this exception occurs while waiting for callable result
   */
  public V runInSeparateThread(Callable<V> callable, ActionResponse response)
      throws InterruptedException {
    boolean isDone = false;
    V result = null;
    ExecutorService executor = Executors.newSingleThreadExecutor();

    // Start thread
    Future<V> future = executor.submit(callable);

    int processTimeout = Beans.get(AppBaseService.class).getProcessTimeout();
    // Wait processTimeout seconds
    int count = 0;
    while (count++ < processTimeout) {
      Thread.sleep(1000);

      if (future.isDone()) {
        try {
          result = future.get();
          isDone = true;
        } catch (ExecutionException e) {
          // cause already traced in traceback
          response.setFlash(e.getCause().getMessage());
          isDone = true;
          break;
        }
        break;
      }
    }
    if (!isDone) {
      response.setNotify(I18n.get(IExceptionMessage.PROCESS_BEING_COMPUTED));
    }
    return result;
  }
}

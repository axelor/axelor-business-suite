/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2020 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.exception.service;

import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.ResponseMessageType;
import com.axelor.exception.db.TraceBack;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe implémentant l'ensemble des services pouvant être utiles dans la gestion des exceptions
 * Axelor.
 */
public class TraceBackService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  // TYPE SELECT
  protected static final int TYPE_TECHNICAL = 0;
  protected static final int TYPE_FUNCTIONAL = 1;

  /**
   * Créer un log des exceptions en tant qu'anomalie.
   *
   * @param e L'exception générée.
   * @param categorySelect <code>0 = Champ manquant</code> <code>1 = Clef non unique</code> <code>
   *     2 = Aucune valeur retournée</code> <code>3 = Problème de configuration</code>
   */
  protected static TraceBack _create(
      Throwable e, String origin, int typeSelect, int categorySelect, long batchId) {

    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));

    TraceBack traceBack = new TraceBack();
    traceBack.setException(e.toString());
    traceBack.setDate(ZonedDateTime.now());
    traceBack.setError(
        e.getStackTrace() != null && e.getStackTrace().length > 0
            ? e.getStackTrace()[0].toString()
            : e.getMessage());

    traceBack.setOrigin(origin);
    traceBack.setTypeSelect(typeSelect);
    traceBack.setCategorySelect(categorySelect);
    traceBack.setBatchId(batchId);

    if (AuthUtils.getSubject() != null) {
      traceBack.setInternalUser(AuthUtils.getUser());
    }
    if (e.getCause() != null) {
      traceBack.setCause(e.getCause().toString());
    }
    if (e.getMessage() != null) {
      traceBack.setMessage(e.getMessage());
    }

    traceBack.setTrace(sw.toString());
    Beans.get(TraceBackRepository.class).persist(traceBack);

    return traceBack;
  }

  protected static TraceBack _create(Throwable e, String origin, int categorySelect, long batchId) {
    return _create(e, origin, TYPE_TECHNICAL, categorySelect, batchId);
  }

  protected static TraceBack _create(AxelorException e, String origin, long batchId) {
    TraceBack traceBack = _create(e, origin, TYPE_FUNCTIONAL, e.getCategory(), batchId);

    if (e.getRefClass() != null) {
      traceBack.setRef(e.getRefClass().getName());
      traceBack.setRefId(e.getRefId());
    }

    return traceBack;
  }

  /**
   * Affiche à l'écran par l'intermédiaire d'une popup le message d'une exception.
   *
   * @param response
   * @param e L'exception cible.
   */
  protected static void _response(
      ActionResponse response, Throwable e, ResponseMessageType responseMessageType) {

    String message = e.getMessage() != null ? e.getMessage() : e.toString();
    responseMessageType.setMessage(response, message);
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug.
   *
   * @param e L'exception cible.
   */
  public static void trace(final Throwable e, final String origin) {

    JPA.runInTransaction(
        () -> {
          if (e instanceof AxelorException) {

            LOG.trace(_create((AxelorException) e, origin, 0).getTrace());

          } else {

            LOG.error(_create(e, origin, 0, 0).getTrace());
          }
        });
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug.
   *
   * @param e L'exception cible.
   */
  public static void trace(final AxelorException e, final String origin, final long batchId) {

    JPA.runInTransaction(() -> LOG.trace(_create(e, origin, batchId).getTrace()));
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug.
   *
   * @param e L'exception cible.
   */
  public static void trace(final Throwable e, final String origin, final long batchId) {

    JPA.runInTransaction(() -> LOG.error(_create(e, origin, 0, batchId).getTrace()));
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug.
   *
   * @param e L'exception cible.
   */
  public static void trace(Throwable e) {

    trace(e, null);
  }

  /**
   * Tracer une exception dans Traceback correspondant à une anomalie.
   *
   * @param e L'exception cible.
   * @param categorySelect <code>0 = Champ manquant</code> <code>1 = Clef non unique</code> <code>
   *     2 = Aucune valeur retournée</code> <code>3 = Problème de configuration</code>
   */
  public static void trace(AxelorException e) {

    trace(e, null);
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug et affiche à l'écran par
   * l'intermédiaire d'une popup le message de l'exception.
   *
   * @param response
   * @param e L'exception cible.
   */
  public static void trace(ActionResponse response, Throwable e, String origin) {

    trace(e, origin);
    _response(response, e, ResponseMessageType.INFORMATION);
  }

  /**
   * Tracer une exception dans Traceback correspondant à un bug et affiche à l'écran par
   * l'intermédiaire d'une popup le message de l'exception.
   *
   * @param response
   * @param e L'exception cible.
   */
  public static void trace(ActionResponse response, Throwable e) {

    trace(response, e, (String) null);
  }

  /**
   * Trace an exception into the traceback and show it to the client with the specified response
   * message type.
   *
   * @param response
   * @param e
   * @param origin
   * @param responseMessageType
   */
  public static void trace(
      ActionResponse response,
      Throwable e,
      String origin,
      ResponseMessageType responseMessageType) {

    trace(e, origin);
    _response(response, e, responseMessageType);
  }

  /**
   * Trace an exception into the traceback and show it to the client with the specified response
   * message type.
   *
   * @param response
   * @param e
   * @param responseMessageType
   */
  public static void trace(
      ActionResponse response, Throwable e, ResponseMessageType responseMessageType) {

    trace(response, e, null, responseMessageType);
  }

  /** @return "Axelor Exception" */
  public String toString() {
    return "Axelor Exception";
  }
}

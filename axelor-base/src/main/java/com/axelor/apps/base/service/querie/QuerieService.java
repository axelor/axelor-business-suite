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
package com.axelor.apps.base.service.querie;

import com.axelor.apps.base.db.Querie;
import com.axelor.apps.base.db.repo.QuerieRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuerieService {

  public List<Long> getQuerieResult(Set<Querie> querieSet) throws AxelorException {
    Set<Long> idList = Sets.newHashSet();

    if (querieSet != null) {
      for (Querie querie : querieSet) {
        idList.addAll(this.getQuerieResult(querie));
      }
    }

    return Lists.newArrayList(idList);
  }

  public List<Long> getQuerieResult(Querie querie) throws AxelorException {
    List<Long> result = Lists.newArrayList();
    int requestType = querie.getType();
    String filter = querie.getQuery();

    if (filter == null || filter.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.QUERIE_1),
          querie.getId());
    }

    Class<?> klass = this.getClass(querie.getMetaModel());
    try {
      if (requestType == QuerieRepository.TYPE_SQL) {
        result = this.runSqlRequest(filter);
      } else if (requestType == QuerieRepository.TYPE_JPQL) {
        result = this.runJpqlRequest(filter, klass);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new AxelorException(
          e.getCause(),
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.QUERIE_2),
          querie.getId());
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  public List<Long> runSqlRequest(String filter) {
    List<Long> idLists = Lists.newArrayList();

    javax.persistence.Query query = JPA.em().createNativeQuery(filter);
    List<BigInteger> queryResult = query.getResultList();

    for (BigInteger bi : queryResult) {
      idLists.add(bi.longValue());
    }

    return idLists;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public List<Long> runJpqlRequest(String filter, Class<?> klass) {
    List<Long> idLists = Lists.newArrayList();

    List<Map> result =
        Query.of((Class<? extends Model>) klass).filter(filter).select("id").fetch(0, 0);
    for (Map map : result) {
      idLists.add(Long.valueOf(map.get("id").toString()));
    }

    return idLists;
  }

  protected Class<?> getClass(MetaModel metaModel) {
    String model = metaModel.getFullName();

    try {
      return Class.forName(model);
    } catch (NullPointerException e) {
    } catch (ClassNotFoundException e) {
    }
    return null;
  }

  public void checkQuerie(Querie querie) throws AxelorException {
    this.getQuerieResult(querie);
  }
}

/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.csv.script;

import com.axelor.apps.base.db.City;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportCity {

  private final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public Object importCity(Object bean, Map<String, Object> values) {

    assert bean instanceof City;

    City city = (City) bean;

    try {

      if (city.getName() == null) {
        return null;
      }

      if (city.getCanton() != null) {
        city.getCanton().setDepartment(city.getDepartment());
      }
    } catch (Exception e) {
      LOG.error("Error when importing city : {}", e);
    }

    return city;
  }
}

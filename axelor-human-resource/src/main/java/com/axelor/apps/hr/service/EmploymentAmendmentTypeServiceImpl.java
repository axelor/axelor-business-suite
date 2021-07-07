/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.hr.service;

import com.axelor.apps.hr.db.EmploymentAmendmentType;
import com.axelor.apps.hr.db.EmploymentContractSubType;
import com.axelor.apps.hr.db.EmploymentContractType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;

public class EmploymentAmendmentTypeServiceImpl implements EmploymentAmendmentTypeService {

  @Override
  public String getEmploymentContractSubTypeSetDomain(
      EmploymentAmendmentType employmentAmendmentType) {

    String employmentContractSubTypeIds = null;
    Set<String> employmentContractSubTypeIdSet = new HashSet<String>();

    employmentContractSubTypeIdSet.add("0");

    if (CollectionUtils.isNotEmpty(employmentAmendmentType.getContractTypeSet())) {
      for (EmploymentContractType employmentContractType :
          employmentAmendmentType.getContractTypeSet()) {
        if (CollectionUtils.isNotEmpty(employmentContractType.getEmploymentContractSubTypeList())) {
          for (EmploymentContractSubType employmentContractSubType :
              employmentContractType.getEmploymentContractSubTypeList()) {
            employmentContractSubTypeIdSet.add(employmentContractSubType.getId().toString());
          }
        }
      }
    }

    employmentContractSubTypeIds = String.join(",", employmentContractSubTypeIdSet);

    return employmentContractSubTypeIds;
  }

  @Override
  public EmploymentAmendmentType setEmploymentContractSubTypeSet(
      EmploymentAmendmentType employmentAmendmentType) {

    List<EmploymentContractSubType> allEmploymentContractSubTypeList =
        new ArrayList<EmploymentContractSubType>();

    Set<EmploymentContractSubType> employmentContractSubTypeSet =
        new HashSet<EmploymentContractSubType>();

    if (CollectionUtils.isNotEmpty(employmentAmendmentType.getContractTypeSet())) {
      for (EmploymentContractType employmentContractType :
          employmentAmendmentType.getContractTypeSet()) {
        if (CollectionUtils.isNotEmpty(employmentContractType.getEmploymentContractSubTypeList())) {
          allEmploymentContractSubTypeList.addAll(
              employmentContractType.getEmploymentContractSubTypeList());
        }
      }
    }

    if (CollectionUtils.isNotEmpty(allEmploymentContractSubTypeList)
        && CollectionUtils.isNotEmpty(employmentAmendmentType.getEmploymentContractSubTypeSet())) {
      for (EmploymentContractSubType employmentContractSubType :
          employmentAmendmentType.getEmploymentContractSubTypeSet()) {
        if (allEmploymentContractSubTypeList.contains(employmentContractSubType)) {
          employmentContractSubTypeSet.add(employmentContractSubType);
        }
      }
    }

    employmentAmendmentType.setEmploymentContractSubTypeSet(employmentContractSubTypeSet);

    return employmentAmendmentType;
  }
}

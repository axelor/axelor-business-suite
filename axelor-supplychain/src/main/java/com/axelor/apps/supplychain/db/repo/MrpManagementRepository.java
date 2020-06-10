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
package com.axelor.apps.supplychain.db.repo;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.apps.supplychain.db.Mrp;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.MrpService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import javax.persistence.PersistenceException;

public class MrpManagementRepository extends MrpRepository {

  @Override
  public void remove(Mrp entity) {

    Beans.get(MrpService.class).reset(entity);

    super.save(entity);
  }

  @Override
  public Mrp save(Mrp entity) {

    try {
      if (Strings.isNullOrEmpty(entity.getMrpSeq())) {
        Company company = entity.getStockLocationSet().iterator().next().getCompany();
        String seq =
            Beans.get(SequenceService.class)
                .getSequenceNumber(SequenceRepository.SUPPLYCHAIN_MRP, company);

        if (seq == null) {
          throw new AxelorException(
              company,
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.SUPPLYCHAIN_MRP_SEQUENCE_ERROR),
              company.getName());
        }

        entity.setMrpSeq(seq);
      }
    } catch (AxelorException e) {
      throw new PersistenceException(e.getLocalizedMessage());
    }

    return super.save(entity);
  }

  @Override
  public Mrp copy(Mrp entity, boolean deep) {

    Mrp copy = super.copy(entity, deep);
    copy.setMrpSeq(null);
    return copy;
  }
}

package com.axelor.apps.base.db.repo;

import javax.persistence.PersistenceException;

import com.axelor.apps.base.db.IAdministration;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.administration.SequenceService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;

public class PartnerBaseRepository extends PartnerRepository {

	@Override
	public Partner save(Partner partner) {
		try {

			if (partner.getPartnerSeq() == null){
				String seq = Beans.get(SequenceService.class).getSequenceNumber(IAdministration.PARTNER);
				if (seq == null)
					throw new AxelorException(I18n.get(IExceptionMessage.PARTNER_1),
							IException.CONFIGURATION_ERROR);
				partner.setPartnerSeq(seq);
			}

			return super.save(partner);
		} catch (Exception e) {
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}
}

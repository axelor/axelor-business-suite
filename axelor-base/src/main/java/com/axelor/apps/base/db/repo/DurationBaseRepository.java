package com.axelor.apps.base.db.repo;

import javax.persistence.PersistenceException;

import com.axelor.apps.base.db.Duration;
import com.axelor.i18n.I18n;

public class DurationBaseRepository extends DurationRepository {

	@Override
	public Duration save(Duration duration) {
		try {

			duration.setName(this.computeName(duration.getTypeSelect(), duration.getValue()));

			return super.save(duration);
		} catch (Exception e) {
			throw new PersistenceException(e.getLocalizedMessage());
		}
	}

	public String computeName(int typeSelect, int value)  {

		String name = "";

		switch (typeSelect) {
		case TYPE_MONTH:

			name += "month";
			break;

		case TYPE_DAY:

			name += "day";
			break;

		default:
			break;
		}

		if(value > 1)  {  name += "s";  }

		return value + " " + I18n.get(name);

	}
}

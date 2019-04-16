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
package com.axelor.apps.base.db.repo;

import com.axelor.apps.base.db.MailingListMessage;
import com.axelor.auth.AuthUtils;
import com.axelor.inject.Beans;
import com.axelor.mail.db.MailFollower;
import com.axelor.mail.db.repo.MailFollowerRepository;
import java.util.Map;

public class MailingListMessageBaseRepository extends MailingListMessageRepository {

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    if (json != null && json.get("id") != null) {
      final MailingListMessage entity = find((Long) json.get("id"));

      final MailFollowerRepository followers = Beans.get(MailFollowerRepository.class);
      final MailFollower follower = followers.findOne(entity, AuthUtils.getUser());

      json.put("_following", follower != null && follower.getArchived() == Boolean.FALSE);
      json.put("_image", entity.getImage() != null);
    }
    return json;
  }
}

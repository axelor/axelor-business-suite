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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import wslite.json.JSONException;

public class MapRestServiceImpl implements MapRestService {

  @Override
  public String makeAddressString(Address address, ObjectNode objectNode)
      throws AxelorException, JSONException {

    Optional<Pair<BigDecimal, BigDecimal>> latLong =
        Beans.get(AddressService.class).getOrUpdateLatLong(address);

    if (!latLong.isPresent()) {
      return "";
    }

    objectNode.put("latit", latLong.get().getLeft());
    objectNode.put("longit", latLong.get().getRight());

    return makeAddressString(address);
  }

  @Override
  public void setData(ObjectNode mainNode, ArrayNode arrayNode)
      throws AxelorException, JSONException {

    mainNode.put("status", 0);
    mainNode.set("data", arrayNode);
    Optional<Address> optionalAddress = Beans.get(UserService.class).getUserActiveCompanyAddress();

    if (optionalAddress.isPresent()) {
      Optional<Pair<BigDecimal, BigDecimal>> latLong =
          Beans.get(AddressService.class).getOrUpdateLatLong(optionalAddress.get());

      if (latLong.isPresent()) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode objectNode = factory.objectNode();
        objectNode.put("lat", latLong.get().getLeft());
        objectNode.put("lng", latLong.get().getRight());
        mainNode.set("company", objectNode);
      }
    }
  }

  @Override
  public void setError(ObjectNode mainNode, Exception e) {
    TraceBackService.trace(e);
    mainNode.put("status", -1);
    mainNode.put("errorMsg", e.getLocalizedMessage());
  }

  protected String makeAddressString(Address address) {
    StringBuilder addressString = new StringBuilder();

    if (StringUtils.notBlank(address.getAddressL2())) {
      addressString.append(address.getAddressL2() + "<br/>");
    }

    if (StringUtils.notBlank(address.getAddressL3())) {
      addressString.append(address.getAddressL3() + "<br/>");
    }

    if (StringUtils.notBlank(address.getAddressL4())) {
      addressString.append(address.getAddressL4() + "<br/>");
    }

    if (StringUtils.notBlank(address.getAddressL5())) {
      addressString.append(address.getAddressL5() + "<br/>");
    }

    if (StringUtils.notBlank(address.getAddressL6())) {
      addressString.append(address.getAddressL6());
    }

    if (address.getAddressL7Country() != null) {
      addressString = addressString.append("<br/>" + address.getAddressL7Country().getName());
    }

    return addressString.toString();
  }
}

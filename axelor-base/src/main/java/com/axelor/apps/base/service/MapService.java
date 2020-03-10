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

import com.axelor.apps.base.db.AppBase;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import groovy.util.slurpersupport.Node;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.ws.rs.core.UriBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wslite.json.JSONArray;
import wslite.json.JSONException;
import wslite.json.JSONObject;
import wslite.rest.ContentType;
import wslite.rest.RESTClient;
import wslite.rest.Response;

public class MapService {

  @Inject protected AppBaseService appBaseService;

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private BigDecimal lat;
  private BigDecimal lon;

  public JSONObject geocodeGoogle(String qString) throws AxelorException, JSONException {
    if (StringUtils.isBlank(qString)) {
      return null;
    }
    // http://maps.googleapis.com/maps/api/geocode/json?address=1600+Amphitheatre+Parkway,+Mountain+View,+CA&sensor=true_or_false

    // TODO inject the rest client, or better, run it in the browser
    RESTClient restClient = new RESTClient("https://maps.googleapis.com");
    Map<String, Object> responseQuery = new HashMap<>();
    responseQuery.put("address", qString.trim());
    responseQuery.put("sensor", "false");
    responseQuery.put("key", getGoogleMapsApiKey());
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put("path", "/maps/api/geocode/json");
    responseMap.put("accept", ContentType.JSON);
    responseMap.put("query", responseQuery);

    responseMap.put("connectTimeout", 5000);
    responseMap.put("readTimeout", 10000);
    responseMap.put("followRedirects", false);
    responseMap.put("useCaches", false);
    responseMap.put("sslTrustAllCerts", true);

    JSONObject restResponse = getJSON(restClient.get(responseMap));
    LOG.debug("Gmap response: {}", restResponse);

    if (restResponse.containsKey("results")) {
      JSONArray results = (JSONArray) restResponse.get("results");

      if (CollectionUtils.isNotEmpty(results)) {
        JSONObject result = (JSONObject) results.iterator().next();

        if (result != null && result.containsKey("geometry")) {
          return (JSONObject) ((JSONObject) result.get("geometry")).get("location");
        }
      }
    }

    throw new AxelorException(
        appBaseService.getAppBase(),
        TraceBackRepository.CATEGORY_NO_VALUE,
        I18n.get(IExceptionMessage.MAP_RESPONSE_ERROR),
        restResponse);

    /*
     * log.debug("restResponse = {}", restResponse)
     * log.debug("restResponse.parsedResponseContent.text = {}",
     * restResponse.parsedResponseContent.text)
     */
    // def searchresults = new
    // JsonSlurper().parseText(restResponse.parsedResponseContent.text);
    /*
     * LOG.debug("searchresults.status = {}", searchresults.status); if
     * (searchresults.status == "OK") { /*
     * log.debug("searchresults.results.size() = {}", searchresults.results.size())
     * log.debug("searchresults.results[0] = {}", searchresults.results[0])
     * log.debug("searchresults.results[0].address_components = {}",
     * searchresults.results[0].address_components)
     * log.debug("searchresults.results[0].geometry.location = {}",
     * searchresults.results[0].geometry.location)
     */
    /*
     * def results = searchresults.results;
     *
     * if (results.size() > 1) { response.put("multiple", true); } def
     * firstPlaceFound = results[0];
     *
     * if (firstPlaceFound) { BigDecimal lat = new
     * BigDecimal(firstPlaceFound.geometry.location.lat); BigDecimal lng = new
     * BigDecimal(firstPlaceFound.geometry.location.lng);
     *
     * response.put("lat", lat.setScale(10, RoundingMode.HALF_EVEN));
     * response.put("lng", lng.setScale(10, RoundingMode.HALF_EVEN)); }
     */
    // }
  }

  public Map<String, Object> getMapGoogle(String qString) throws AxelorException, JSONException {
    LOG.debug("Query string: {}", qString);
    JSONObject googleResponse = geocodeGoogle(qString);
    LOG.debug("Google response: {}", googleResponse);
    if (googleResponse != null) {
      Map<String, Object> result = new HashMap<>();
      BigDecimal latitude = new BigDecimal(googleResponse.get("lat").toString());
      BigDecimal longitude = new BigDecimal(googleResponse.get("lng").toString());
      LOG.debug("URL:" + "map/gmaps.html?x=" + latitude + "&y=" + longitude + "&z=18");
      result.put(
          "url",
          "map/gmaps.html?key="
              + getGoogleMapsApiKey()
              + "&x="
              + latitude
              + "&y="
              + longitude
              + "&z=18");
      result.put("latitude", latitude);
      result.put("longitude", longitude);
      return result;
    }

    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public Map<String, Object> getMapOsm(String qString) {
    Map<String, Object> result = new HashMap<>();
    try {
      BigDecimal latitude = BigDecimal.ZERO;
      BigDecimal longitude = BigDecimal.ZERO;
      RESTClient restClient = new RESTClient("https://nominatim.openstreetmap.org/");
      Map<String, Object> mapQuery = new HashMap<>();
      mapQuery.put("q", qString);
      mapQuery.put("format", "xml");
      mapQuery.put("polygon", true);
      mapQuery.put("addressdetails", true);
      Map<String, Object> mapResponse = new HashMap<>();
      mapResponse.put("path", "/search");
      mapResponse.put("accept", ContentType.JSON);
      mapResponse.put("query", mapQuery);
      mapResponse.put("connectTimeout", 10000);
      mapResponse.put("readTimeout", 10000);
      mapResponse.put("followRedirects", false);
      mapResponse.put("useCaches", false);
      mapResponse.put("sslTrustAllCerts", true);
      Response restResponse = restClient.get(mapResponse);
      GPathResult searchresults = new XmlSlurper().parseText(restResponse.getContentAsString());
      Iterator<Node> iterator = searchresults.childNodes();
      if (iterator.hasNext()) {
        Node node = iterator.next();
        Map attributes = node.attributes();
        if (attributes.containsKey("lat") && attributes.containsKey("lon")) {
          if (BigDecimal.ZERO.compareTo(latitude) == 0)
            latitude = new BigDecimal(node.attributes().get("lat").toString());
          if (BigDecimal.ZERO.compareTo(longitude) == 0)
            longitude = new BigDecimal(node.attributes().get("lon").toString());
        }
      }

      LOG.debug("OSMap qString: {}, latitude: {}, longitude: {}", qString, latitude, longitude);

      if (BigDecimal.ZERO.compareTo(latitude) != 0 && BigDecimal.ZERO.compareTo(longitude) != 0) {
        result.put("url", "map/oneMarker.html?x=" + latitude + "&y=" + longitude + "&z=18");
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        return result;
      }

    } catch (Exception e) {
      TraceBackService.trace(e);
    }
    return null;
  }

  public Map<String, Object> getMap(String qString) throws AxelorException, JSONException {
    LOG.debug("qString = {}", qString);

    switch (appBaseService.getAppBase().getMapApiSelect()) {
      case AppBaseRepository.MAP_API_GOOGLE:
        return getMapGoogle(qString);

      case AppBaseRepository.MAP_API_OPEN_STREET_MAP:
        return getMapOsm(qString);

      default:
        return null;
    }
  }

  public String getMapUrl(Pair<BigDecimal, BigDecimal> latLong) {
    return getMapUrl(latLong.getLeft(), latLong.getRight(), null);
  }

  public String getMapUrl(Pair<BigDecimal, BigDecimal> latLong, String title) {
    return getMapUrl(latLong.getLeft(), latLong.getRight(), title);
  }

  public String getMapUrl(BigDecimal latitude, BigDecimal longitude) {
    return getMapUrl(latitude, longitude, null);
  }

  public String getMapUrl(BigDecimal latitude, BigDecimal longitude, String title) {
    try {
      switch (appBaseService.getAppBase().getMapApiSelect()) {
        case AppBaseRepository.MAP_API_GOOGLE:
          final String uri = "map/gmaps.html";
          UriBuilder ub = UriBuilder.fromUri(uri);
          ub.queryParam("key", getGoogleMapsApiKey());
          ub.queryParam("x", String.valueOf(latitude));
          ub.queryParam("y", String.valueOf(longitude));
          ub.queryParam("z", String.valueOf(18));
          ub.queryParam("title", title);
          return ub.build().toString();

        case AppBaseRepository.MAP_API_OPEN_STREET_MAP:
          return "map/oneMarker.html?x=" + latitude + "&y=" + longitude + "&z=18";

        default:
          return null;
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
      return getErrorURI(e.getMessage());
    }
  }

  public String getDirectionUrl(
      String key,
      Pair<BigDecimal, BigDecimal> departureLatLong,
      Pair<BigDecimal, BigDecimal> arrivalLatLong) {
    return getDirectionUrl(
        key,
        departureLatLong.getLeft(),
        departureLatLong.getRight(),
        arrivalLatLong.getLeft(),
        arrivalLatLong.getRight());
  }

  public String getDirectionUrl(
      String key, BigDecimal dLat, BigDecimal dLon, BigDecimal aLat, BigDecimal aLon) {
    String queryParam = "dx=" + dLat + "&dy=" + dLon + "&ax=" + aLat + "&ay=" + aLon;
    if (appBaseService.getAppBase().getMapApiSelect()
        == AppBaseRepository.MAP_API_OPEN_STREET_MAP) {
      return "map/osm-directions.html?" + queryParam;
    }
    return "map/directions.html?" + queryParam + "&key=" + key;
  }

  public Map<String, Object> getDirectionMapGoogle(
      String dString,
      BigDecimal dLat,
      BigDecimal dLon,
      String aString,
      BigDecimal aLat,
      BigDecimal aLon) {
    LOG.debug("departureString = {}", dString);
    LOG.debug("arrivalString = {}", aString);
    Map<String, Object> result = new HashMap<>();
    try {
      if (!checkNotNullNotZero(dLat) || !checkNotNullNotZero(dLon)) {
        getGoogleResponse(dString);
        dLat = lat;
        dLon = lon;
      }
      LOG.debug("departureLat = {}, departureLng={}", dLat, dLon);
      if (!checkNotNullNotZero(aLat) || !checkNotNullNotZero(aLon)) {
        getGoogleResponse(aString);
        aLat = lat;
        aLon = lon;
      }
      LOG.debug("arrivalLat = {}, arrivalLng={}", aLat, aLon);

      if (checkNotNullNotZero(dLat)
          && checkNotNullNotZero(dLon)
          && checkNotNullNotZero(aLon)
          && checkNotNullNotZero(aLat)) {
        result.put(
            "url",
            "map/directions.html?key="
                + getGoogleMapsApiKey()
                + "&dx="
                + dLat
                + "&dy="
                + dLon
                + "&ax="
                + aLat
                + "&ay="
                + aLon);
        result.put("aLat", aLat);
        result.put("dLat", dLat);
        return result;
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return null;
  }

  protected void getGoogleResponse(String key) throws AxelorException, JSONException {
    @SuppressWarnings("unchecked")
    Map<String, Object> googleResponse = geocodeGoogle(key);

    lat = lon = BigDecimal.ZERO;
    if (googleResponse != null) {
      lat = new BigDecimal(googleResponse.get("lat").toString());
      lon = new BigDecimal(googleResponse.get("lng").toString());
    }
  }

  protected Boolean checkNotNullNotZero(BigDecimal value) {
    Boolean flag = false;
    if (value != null && BigDecimal.ZERO.compareTo(value) != 0) {
      flag = true;
    }
    return flag;
  }

  public void testGMapService() throws AxelorException, JSONException {
    RESTClient restClient = new RESTClient("https://maps.googleapis.com");

    Map<String, Object> responseQuery = new HashMap<>();
    responseQuery.put("address", "google");
    responseQuery.put("sensor", "false");
    responseQuery.put("key", getGoogleMapsApiKey());

    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put("path", "/maps/api/geocode/json");
    responseMap.put("accept", ContentType.JSON);
    responseMap.put("query", responseQuery);

    responseMap.put("connectTimeout", 5000);
    responseMap.put("readTimeout", 10000);
    responseMap.put("followRedirects", false);
    responseMap.put("useCaches", false);
    responseMap.put("sslTrustAllCerts", true);

    Response response = restClient.get(responseMap);
    getJSON(response);
  }

  protected JSONObject getJSON(Response response) throws AxelorException, JSONException {
    LOG.debug(
        "Gmap connection status code: {}, message: {}",
        response.getStatusCode(),
        response.getStatusMessage());

    AppBase appBase = appBaseService.getAppBase();

    if (response.getStatusCode() != HttpStatus.SC_OK) {
      String msg = String.format("%d: %s", response.getStatusCode(), response.getStatusMessage());
      throw new AxelorException(appBase, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, msg);
    }

    JSONObject json = new JSONObject(response.getContentAsString());
    String status = json.getString("status");

    if (!"OK".equalsIgnoreCase(status)) {
      String msg =
          json.has("error_message")
              ? String.format("%s: %s", status, json.getString("error_message"))
              : status;
      throw new AxelorException(appBase, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, msg);
    }

    return json;
  }

  public String getMapURI(String name) {
    return appBaseService.getAppBase().getMapApiSelect() == AppBaseRepository.MAP_API_GOOGLE
        ? getMapURI(name, null)
        : getOsmMapURI(name, null);
  }

  public String getMapURI(String name, Long id) {
    final String uri = "map/gmap-objs.html";

    try {
      UriBuilder ub = UriBuilder.fromUri(uri);
      ub.queryParam("key", getGoogleMapsApiKey());
      ub.queryParam("object", name);

      if (id != null) {
        ub.queryParam("id", id);
      }
      return ub.build().toString();
    } catch (Exception e) {
      TraceBackService.trace(e);
      return getErrorURI(e.getMessage());
    }
  }

  protected String getErrorURI(String msg) {
    final String uri = "map/error.html";

    try {
      UriBuilder ub = UriBuilder.fromUri(uri);
      ub.queryParam("msg", msg);

      return ub.build().toString();
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return uri;
  }

  public String getGoogleMapsApiKey() {
    Preconditions.checkArgument(
        StringUtils.notBlank(appBaseService.getAppBase().getGoogleMapsApiKey()),
        I18n.get(IExceptionMessage.MAP_GOOGLE_MAPS_API_KEY_MISSING));
    return appBaseService.getAppBase().getGoogleMapsApiKey();
  }

  public boolean isConfigured() {
    switch (appBaseService.getAppBase().getMapApiSelect()) {
      case AppBaseRepository.MAP_API_GOOGLE:
        return StringUtils.notBlank(appBaseService.getAppBase().getGoogleMapsApiKey());

      case AppBaseRepository.MAP_API_OPEN_STREET_MAP:
        return true;

      default:
        return false;
    }
  }

  public String getOsmMapURI(String name) {
    return getOsmMapURI(name, null);
  }

  public String getOsmMapURI(String name, Long id) {
    final String uri = "map/osm-objs.html";

    try {
      UriBuilder ub = UriBuilder.fromUri(uri);
      ub.queryParam("object", name);

      if (id != null) {
        ub.queryParam("id", id);
      }
      return ub.build().toString();
    } catch (Exception e) {
      TraceBackService.trace(e);
      return getErrorURI(e.getMessage());
    }
  }

  public Map<String, Object> getDirectionMapOsm(
      String dString,
      BigDecimal dLat,
      BigDecimal dLon,
      String aString,
      BigDecimal aLat,
      BigDecimal aLon) {
    LOG.debug("departureString = {}", dString);
    LOG.debug("arrivalString = {}", aString);
    Map<String, Object> result = new HashMap<>();
    try {
      if (!checkNotNullNotZero(dLat) || !checkNotNullNotZero(dLon)) {
        getOsmResponse(dString);
        dLat = lat;
        dLon = lon;
      }
      LOG.debug("departureLat = {}, departureLng={}", dLat, dLon);
      if (!checkNotNullNotZero(aLat) || !checkNotNullNotZero(aLon)) {
        getOsmResponse(aString);
        aLat = lat;
        aLon = lon;
      }
      LOG.debug("arrivalLat = {}, arrivalLng={}", aLat, aLon);

      if (checkNotNullNotZero(dLat)
          && checkNotNullNotZero(dLon)
          && checkNotNullNotZero(aLon)
          && checkNotNullNotZero(aLat)) {
        result.put(
            "url",
            "map/osm-directions.html?"
                + "dx="
                + dLat
                + "&dy="
                + dLon
                + "&ax="
                + aLat
                + "&ay="
                + aLon);
        result.put("aLat", aLat);
        result.put("dLat", dLat);
        return result;
      }
    } catch (Exception e) {
      TraceBackService.trace(e);
    }

    return null;
  }

  protected void getOsmResponse(String qString) throws AxelorException, JSONException {
    Map<String, Object> osmResponse = getMapOsm(qString);

    lat = lon = BigDecimal.ZERO;
    if (osmResponse != null) {
      lat = new BigDecimal(osmResponse.get("latitude").toString());
      lon = new BigDecimal(osmResponse.get("longitude").toString());
    }
  }
}

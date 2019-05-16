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
package com.axelor.apps.base.service.app;

import com.axelor.apps.base.db.App;
import com.axelor.apps.base.db.repo.AppRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.event.Event;
import com.axelor.events.FeatureChanged;
import com.axelor.events.PostRequest;
import com.axelor.exception.service.TraceBackService;
import com.axelor.rpc.RequestUtils;
import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.beanutils.PropertyUtils;

@Singleton
class AppFeatureManager {
  @Inject private Event<FeatureChanged> featureChangedEvent;
  @Inject private AppRepository appRepo;

  public void fireFeatureChanged(PostRequest event, App app) {
    RequestUtils.processRequest(
        event.getRequest(),
        values ->
            values
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof Boolean)
                .forEach(
                    entry -> {
                      final Feature feature = new Feature(app.getCode(), entry.getKey());
                      final boolean enabled = getBoolean((Boolean) entry.getValue());
                      featureChangedEvent.fire(new FeatureChanged(feature.toString(), enabled));
                    }));
  }

  public boolean hasFeature(String featureName) {
    final Feature feature = new Feature(featureName);
    final Stream<App> appStream =
        feature.getAppCode().isEmpty()
            ? appRepo.all().fetch().stream()
            : Stream.of(appRepo.findByCode(feature.getAppCode())).filter(Objects::nonNull);

    return appStream
        .filter(app -> PropertyUtils.isReadable(app, feature.getFeatureName()))
        .findFirst()
        .map(app -> getBoolean(app, feature.getFeatureName()))
        .orElse(false);
  }

  private static boolean getBoolean(Object bean, String name) {
    final Object value;

    try {
      value = PropertyUtils.getProperty(bean, name);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      TraceBackService.trace(e);
      return false;
    }

    return getBoolean(value);
  }

  private static boolean getBoolean(Object value) {
    if (value instanceof Boolean) {
      return getBoolean((Boolean) value);
    }

    return ObjectUtils.notEmpty(value);
  }

  private static boolean getBoolean(Boolean value) {
    return Optional.ofNullable(value).orElse(false);
  }

  private static class Feature {
    private final String appCode;
    private final String featureName;

    public Feature(String appCode, String featureName) {
      this.appCode = appCode;
      this.featureName = featureName;
    }

    public Feature(String featureName) {
      final int pos = featureName.lastIndexOf('.');

      if (pos < 0) {
        appCode = "";
        this.featureName = featureName;
      } else {
        appCode = featureName.substring(0, pos);
        this.featureName = featureName.substring(pos + 1);
      }
    }

    public String getAppCode() {
      return appCode;
    }

    public String getFeatureName() {
      return featureName;
    }

    @Override
    public String toString() {
      return ImmutableList.of(appCode, featureName)
          .stream()
          .filter(StringUtils::notBlank)
          .collect(Collectors.joining("."));
    }
  }
}

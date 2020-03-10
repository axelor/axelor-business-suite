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
package com.axelor.apps.tool.service;

import com.axelor.common.StringUtils;
import com.axelor.meta.db.MetaTranslation;
import com.axelor.meta.db.repo.MetaTranslationRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.Collection;
import java.util.HashSet;

public class TranslationServiceImpl implements TranslationService {
  protected MetaTranslationRepository metaTranslationRepo;

  protected static final String VALUE_KEY_PREFIX = "value:";

  @Inject
  TranslationServiceImpl(MetaTranslationRepository metaTranslationRepo) {
    this.metaTranslationRepo = metaTranslationRepo;
  }

  @Override
  @Transactional
  public void updateFormatedValueTranslations(String oldKey, String format, Object... args) {
    removeValueTranslations(oldKey);
    createFormatedValueTranslations(format, args);
  }

  @Override
  @Transactional
  public void createFormatedValueTranslations(String format, Object... args) {
    String key = VALUE_KEY_PREFIX + String.format(format, args);

    for (String language : getLanguages(args)) {
      MetaTranslation metaTranslation = metaTranslationRepo.findByKey(key, language);

      if (metaTranslation == null) {
        metaTranslation = new MetaTranslation();
        metaTranslation.setKey(key);
        metaTranslation.setLanguage(language);
      }

      String message = String.format(format, getTranslatedValueArgs(args, language));
      metaTranslation.setMessage(message);

      metaTranslationRepo.save(metaTranslation);
    }
  }

  @Override
  @Transactional
  public void removeValueTranslations(String key) {
    if (StringUtils.isBlank(key)) {
      return;
    }
    for (MetaTranslation metaTranslation :
        metaTranslationRepo
            .all()
            .filter("self.key = :key")
            .bind("key", VALUE_KEY_PREFIX + key)
            .fetch()) {
      metaTranslationRepo.remove(metaTranslation);
    }
  }

  protected Object[] getTranslatedValueArgs(Object[] args, String language) {
    Object[] translatedArgs = new String[args.length];

    for (int i = 0; i < args.length; ++i) {
      String key = String.valueOf(args[i]);
      translatedArgs[i] = getValueTranslation(key, language);
    }

    return translatedArgs;
  }

  @Override
  public String getTranslation(String key, String language) {
    MetaTranslation metaTranslation = metaTranslationRepo.findByKey(key, language);
    return metaTranslation != null && !StringUtils.isBlank(metaTranslation.getMessage())
        ? metaTranslation.getMessage()
        : key;
  }

  @Override
  public String getTranslationKey(String message, String language) {
    MetaTranslation metaTranslation =
        metaTranslationRepo
            .all()
            .filter("self.message = ?1 AND self.language = ?2", message, language)
            .fetchOne();
    return metaTranslation != null && !StringUtils.isBlank(metaTranslation.getKey())
        ? metaTranslation.getKey()
        : message;
  }

  @Override
  public String getValueTranslation(String key, String language) {
    String valueKey = VALUE_KEY_PREFIX + key;
    String translation = getTranslation(valueKey, language);
    return !valueKey.equals(translation) ? translation : key;
  }

  protected Collection<String> getLanguages(Object... args) {
    Collection<String> languages = new HashSet<>();

    for (Object arg : args) {
      for (MetaTranslation metaTranslation :
          metaTranslationRepo
              .all()
              .filter("self.key = :key")
              .bind("key", VALUE_KEY_PREFIX + arg)
              .fetch()) {
        languages.add(metaTranslation.getLanguage());
      }
    }

    return languages;
  }
}

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
package com.axelor.apps.base.service.advanced.imports;

import com.axelor.apps.base.db.FileField;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;

public class FileFieldServiceImpl implements FileFieldService {

  @Inject private MetaModelRepository metaModelRepo;

  @Inject MetaFieldRepository metaFieldRepo;

  @Override
  public FileField fillType(FileField fileField) {
    String targetType = null;
    String relationship = null;

    if (fileField.getImportField() != null) {
      targetType = fileField.getImportField().getTypeName();
      relationship = fileField.getImportField().getRelationship();

      if (!Strings.isNullOrEmpty(fileField.getSubImportField())) {
        String[] subFields = fileField.getSubImportField().split("\\.");
        String[] types = this.getSubFieldType(subFields, 0, fileField.getImportField());
        targetType = types[0];
        relationship = types[1];
      }
    }
    fileField.setTargetType(targetType);
    fileField.setRelationship(relationship);
    return fileField;
  }

  protected String[] getSubFieldType(String[] subFields, int index, MetaField parentField) {
    String[] types = new String[2];

    if (index < subFields.length) {
      types[0] = parentField.getTypeName();
      types[1] = parentField.getRelationship();
      MetaModel childModel = metaModelRepo.findByName(parentField.getTypeName());
      if (childModel != null) {
        MetaField childField = metaFieldRepo.findByModel(subFields[index], childModel);

        if (childField == null) {
          return types;
        }
        if (childField.getRelationship() != null) {
          if (!types[0].equals("MetaFile")) {
            types[0] = childField.getTypeName();
          }
          types[1] = childField.getRelationship();
          types = this.getSubFieldType(subFields, index + 1, childField);
        } else {
          if (!types[0].equals("MetaFile")) {
            types[0] = childField.getTypeName();
          }
        }
      }
    } else {
      if ((!Strings.isNullOrEmpty(types[0]) && !types[0].equals("MetaFile"))
          || Strings.isNullOrEmpty(types[0])) {
        types[0] = parentField.getTypeName();
      }
      types[1] = parentField.getRelationship();
    }
    return types;
  }

  @Override
  public String computeFullName(FileField fileField) {
    String fullName = fileField.getSequence().toString();

    if (!Strings.isNullOrEmpty(fileField.getColumnTitle())) {
      fullName += " - " + fileField.getColumnTitle();
    }
    if (fileField.getImportField() != null) {
      fullName += " - " + fileField.getImportField().getName();
    }
    if (!Strings.isNullOrEmpty(fileField.getSubImportField())) {
      fullName += "." + fileField.getSubImportField();
    }
    return fullName;
  }
}

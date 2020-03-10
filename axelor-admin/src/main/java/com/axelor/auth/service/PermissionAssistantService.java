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
package com.axelor.auth.service;

import com.axelor.apps.tool.StringTool;
import com.axelor.auth.db.Group;
import com.axelor.auth.db.IMessage;
import com.axelor.auth.db.Permission;
import com.axelor.auth.db.PermissionAssistant;
import com.axelor.auth.db.Role;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.auth.db.repo.PermissionAssistantRepository;
import com.axelor.auth.db.repo.PermissionRepository;
import com.axelor.auth.db.repo.RoleRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaPermission;
import com.axelor.meta.db.MetaPermissionRule;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.db.repo.MetaPermissionRepository;
import com.axelor.meta.db.repo.MetaPermissionRuleRepository;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionAssistantService {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject private MetaPermissionRepository metaPermissionRepository;

  @Inject private PermissionRepository permissionRepository;

  @Inject private MetaPermissionRuleRepository ruleRepository;

  @Inject private GroupRepository groupRepository;

  @Inject private MetaModelRepository modelRepository;

  @Inject private MetaFieldRepository fieldRepository;

  @Inject private MetaFiles metaFiles;

  @Inject private RoleRepository roleRepo;

  private String errorLog = "";

  private Collection<String> header =
      Arrays.asList(/*$$(*/ "Object" /*)*/, /*$$(*/ "Field" /*)*/, /*$$(*/ "Title" /*)*/);

  private Collection<String> groupHeader =
      Arrays.asList(
          "",
          /*$$(*/ "Read" /*)*/,
          /*$$(*/ "Write" /*)*/,
          /*$$(*/ "Create" /*)*/,
          /*$$(*/ "Delete" /*)*/,
          /*$$(*/ "Export" /*)*/,
          /*$$(*/ "Condition" /*)*/,
          /*$$(*/ "ConditionParams" /*)*/,
          /*$$(*/ "Readonly If" /*)*/,
          /*$$(*/ "Hide If" /*)*/);

  protected String getFileName(PermissionAssistant assistant) {

    String userCode = assistant.getCreatedBy().getCode();
    String dateString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    String fileName = userCode + "-" + dateString + ".csv";

    return fileName;
  }

  public void createFile(PermissionAssistant assistant) {

    File permFile = new File(Files.createTempDir(), getFileName(assistant));

    try {

      try (FileWriterWithEncoding fileWriter =
          new FileWriterWithEncoding(permFile, StandardCharsets.UTF_8)) {
        CSVWriter csvWriter = new CSVWriter(fileWriter, ';');
        writeGroup(csvWriter, assistant);
      }

      createMetaFile(permFile, assistant);

    } catch (Exception e) {
      LOG.error(e.getLocalizedMessage());
      TraceBackService.trace(e);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  public void createMetaFile(File permFile, PermissionAssistant assistant) throws IOException {

    assistant.setMetaFile(metaFiles.upload(permFile));

    Beans.get(PermissionAssistantRepository.class).save(assistant);
  }

  protected Collection<String> getTranslatedStrings(
      Collection<String> strings, ResourceBundle bundle) {
    return strings.stream().map(bundle::getString).collect(Collectors.toList());
  }

  protected void writeGroup(CSVWriter csvWriter, PermissionAssistant assistant) {

    String[] groupRow = null;
    Integer count = header.size();
    ResourceBundle bundle = I18n.getBundle(new Locale(assistant.getLanguage()));

    List<String> headerRow = new ArrayList<String>();
    headerRow.addAll(getTranslatedStrings(header, bundle));
    if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_GROUPS) {
      groupRow = new String[header.size() + (assistant.getGroupSet().size() * groupHeader.size())];
      for (Group group : assistant.getGroupSet()) {
        groupRow[count + 1] = group.getCode();
        headerRow.addAll(getTranslatedStrings(groupHeader, bundle));
        count += groupHeader.size();
      }
    } else if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_ROLES) {
      groupRow = new String[header.size() + (assistant.getRoleSet().size() * groupHeader.size())];
      for (Role role : assistant.getRoleSet()) {
        groupRow[count + 1] = role.getName();
        headerRow.addAll(getTranslatedStrings(groupHeader, bundle));
        count += groupHeader.size();
      }
    }

    LOG.debug("Header row created: {}", headerRow);

    csvWriter.writeNext(groupRow);
    csvWriter.writeNext(headerRow.toArray(groupRow));

    writeObject(csvWriter, assistant, groupRow.length, bundle);
  }

  public Comparator<Object> compareField() {

    return new Comparator<Object>() {
      @Override
      public int compare(Object field1, Object field2) {
        return ((MetaField) field1).getName().compareTo(((MetaField) field2).getName());
      }
    };
  }

  protected void writeObject(
      CSVWriter csvWriter, PermissionAssistant assistant, Integer size, ResourceBundle bundle) {

    MetaField userField = assistant.getMetaField();

    for (MetaModel object : assistant.getObjectSet()) {

      int colIndex = header.size() + 1;
      String[] row = new String[size];
      row[0] = object.getFullName();

      if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_GROUPS) {
        for (Group group : assistant.getGroupSet()) {
          String permName = getPermissionName(userField, object.getName(), group.getCode());
          colIndex = writePermission(object, userField, row, colIndex, permName);
          colIndex++;
        }

      } else if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_ROLES) {
        for (Role role : assistant.getRoleSet()) {
          String permName = getPermissionName(userField, object.getName(), role.getName());
          colIndex = writePermission(object, userField, row, colIndex, permName);
          colIndex++;
        }
      }

      csvWriter.writeNext(row);

      if (!assistant.getFieldPermission()) {
        continue;
      }

      List<MetaField> fieldList = object.getMetaFields();
      Collections.sort(fieldList, compareField());

      for (MetaField field : fieldList) {
        colIndex = header.size() + 1;
        row = new String[size];
        row[1] = field.getName();
        row[2] = getFieldTitle(field, bundle);

        if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_GROUPS) {
          for (Group group : assistant.getGroupSet()) {
            String permName = getPermissionName(null, object.getName(), group.getCode());
            colIndex = writeFieldPermission(field, row, colIndex, permName);
            colIndex++;
          }

        } else if (assistant.getTypeSelect() == PermissionAssistantRepository.TYPE_ROLES) {
          for (Role role : assistant.getRoleSet()) {
            String permName = getPermissionName(null, object.getName(), role.getName());
            colIndex = writeFieldPermission(field, row, colIndex, permName);
            colIndex++;
          }
        }
        csvWriter.writeNext(row);
      }
    }
  }

  protected String getPermissionName(MetaField userField, String objectName, String suffix) {

    String permName = "perm." + objectName + "." + suffix;
    if (userField != null) {
      permName += "." + userField.getName();
    }

    return permName;
  }

  protected String getFieldTitle(MetaField field, ResourceBundle bundle) {

    String title = field.getLabel();
    if (!Strings.isNullOrEmpty(title)) {
      title = bundle.getString(title);
      if (Strings.isNullOrEmpty(title)) {
        title = field.getLabel();
      }
    }

    return title;
  }

  protected int writeFieldPermission(MetaField field, String[] row, int colIndex, String permName) {

    MetaPermissionRule rule =
        ruleRepository
            .all()
            .filter(
                "self.metaPermission.name = ?1 and self.metaPermission.object = ?2 and self.field = ?3",
                permName,
                field.getMetaModel().getFullName(),
                field.getName())
            .fetchOne();
    if (rule != null) {
      row[colIndex++] = rule.getCanRead() == false ? "" : "x";
      row[colIndex++] = rule.getCanWrite() == false ? "" : "x";
      row[colIndex++] = "";
      row[colIndex++] = "";
      row[colIndex++] = rule.getCanExport() == false ? "" : "x";
      row[colIndex++] = "";
      row[colIndex++] = "";
      row[colIndex++] =
          Strings.isNullOrEmpty(rule.getReadonlyIf()) ? "" : rule.getReadonlyIf(); // readonly if
      row[colIndex++] = Strings.isNullOrEmpty(rule.getHideIf()) ? "" : rule.getHideIf(); // hide if
    }

    return colIndex;
  }

  protected int writePermission(
      MetaModel object, MetaField userField, String[] row, int colIndex, String permName) {

    Permission perm = permissionRepository.findByName(permName);

    if (perm != null && perm.getObject().equals(object.getFullName())) {
      row[colIndex++] = perm.getCanRead() == false ? "" : "x";
      row[colIndex++] = perm.getCanWrite() == false ? "" : "x";
      row[colIndex++] = perm.getCanCreate() == false ? "" : "x";
      row[colIndex++] = perm.getCanRemove() == false ? "" : "x";
      row[colIndex++] = perm.getCanExport() == false ? "" : "x";
      row[colIndex++] = Strings.isNullOrEmpty(perm.getCondition()) ? "" : perm.getCondition();
      row[colIndex++] =
          Strings.isNullOrEmpty(perm.getConditionParams()) ? "" : perm.getConditionParams();
      row[colIndex++] = ""; // readonly if
      row[colIndex++] = ""; // hide if
    } else if (userField != null) {
      MetaField objectField =
          fieldRepository
              .all()
              .filter(
                  "self.typeName = ?1 and self.metaModel = ?2 and self.relationship = 'ManyToOne'",
                  userField.getTypeName(),
                  object)
              .fetchOne();
      if (objectField != null) {
        String condition = "";
        String conditionParams = "__user__." + userField.getName();

        if (userField.getRelationship().contentEquals("ManyToOne")) {
          condition = "self." + objectField.getName() + " = ?";
        } else {
          condition = "self." + objectField.getName() + " in (?)";
        }
        row[colIndex++] = "x";
        row[colIndex++] = "x";
        row[colIndex++] = "x";
        row[colIndex++] = "x";
        row[colIndex++] = "x";
        row[colIndex++] = condition;
        row[colIndex++] = conditionParams;
        row[colIndex++] = ""; // readonly if
        row[colIndex++] = ""; // hide if
      }
    }

    return colIndex;
  }

  protected static boolean headerEquals(
      Collection<String> standardRow,
      Collection<String> translatedRow,
      Collection<String> headerRow) {

    if (standardRow.size() != translatedRow.size() || headerRow.size() != standardRow.size()) {
      return false;
    }

    Iterator<String> itStandard = standardRow.iterator();
    Iterator<String> itTranslated = translatedRow.iterator();
    Iterator<String> it = headerRow.iterator();

    while (it.hasNext()) {
      String standard = itStandard.next();
      String translated = itTranslated.next();
      String name = it.next();

      if (!StringTool.equalsIgnoreCaseAndAccents(standard, name)
          && !StringTool.equalsIgnoreCaseAndAccents(translated, name)) {
        return false;
      }
    }

    return true;
  }

  protected boolean checkHeaderRow(Collection<String> headerRow, ResourceBundle bundle) {
    Collection<String> translatedHeader = getTranslatedStrings(header, bundle);
    Collection<String> translatedGroupHeader = getTranslatedStrings(groupHeader, bundle);

    // Untranslated
    Collection<String> standardRow = Lists.newArrayList(header);
    for (int count = header.size(); count < headerRow.size(); count += groupHeader.size()) {
      standardRow.addAll(groupHeader);
    }

    // Translated
    Collection<String> translatedRow = Lists.newArrayList(translatedHeader);
    for (int count = header.size(); count < headerRow.size(); count += groupHeader.size()) {
      translatedRow.addAll(translatedGroupHeader);
    }

    LOG.debug("Standard Headers: {}", standardRow);
    LOG.debug("File Headers: {}", headerRow);

    return headerEquals(standardRow, translatedRow, headerRow);
  }

  public String importPermissions(PermissionAssistant permissionAssistant) {

    try {
      ResourceBundle bundle = I18n.getBundle(new Locale(permissionAssistant.getLanguage()));
      MetaFile metaFile = permissionAssistant.getMetaFile();
      File csvFile = MetaFiles.getPath(metaFile).toFile();

      try (CSVReader csvReader =
          new CSVReader(
              new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8), ';')) {

        String[] groupRow = csvReader.readNext();
        if (groupRow == null || groupRow.length < 11) {
          errorLog = I18n.get(IMessage.BAD_FILE);
        }

        String[] headerRow = csvReader.readNext();
        if (headerRow == null) {
          errorLog = I18n.get(IMessage.NO_HEADER);
        }
        if (!checkHeaderRow(Arrays.asList(headerRow), bundle)) {
          errorLog = I18n.get(IMessage.BAD_HEADER) + " " + Arrays.asList(headerRow);
        }

        if (!errorLog.equals("")) {
          return errorLog;
        }

        if (permissionAssistant.getTypeSelect() == PermissionAssistantRepository.TYPE_GROUPS) {
          Map<String, Group> groupMap = checkBadGroups(groupRow);
          processGroupCSV(
              csvReader,
              groupRow,
              groupMap,
              permissionAssistant.getMetaField(),
              permissionAssistant.getFieldPermission());
          saveGroups(groupMap);
        } else if (permissionAssistant.getTypeSelect()
            == PermissionAssistantRepository.TYPE_ROLES) {
          Map<String, Role> roleMap = checkBadRoles(groupRow);
          processRoleCSV(
              csvReader,
              groupRow,
              roleMap,
              permissionAssistant.getMetaField(),
              permissionAssistant.getFieldPermission());
          saveRoles(roleMap);
        }
      }

    } catch (Exception e) {
      LOG.error(e.getLocalizedMessage());
      TraceBackService.trace(e);
      errorLog += "\n" + String.format(I18n.get(IMessage.ERR_IMPORT_WITH_MSG), e.getMessage());
    }

    return errorLog;
  }

  @Transactional
  public void saveGroups(Map<String, Group> groupMap) {

    for (Group group : groupMap.values()) {
      groupRepository.save(group);
    }
  }

  @Transactional
  public void saveRoles(Map<String, Role> roleMap) {

    for (Role role : roleMap.values()) {
      roleRepo.save(role);
    }
  }

  protected Map<String, Group> checkBadGroups(String[] groupRow) {

    List<String> badGroups = new ArrayList<String>();
    Map<String, Group> groupMap = new HashMap<String, Group>();

    for (Integer glen = header.size() + 1; glen < groupRow.length; glen += groupHeader.size()) {

      String groupName = groupRow[glen];
      Group group = groupRepository.all().filter("self.code = ?1", groupName).fetchOne();
      if (group == null) {
        badGroups.add(groupName);
      } else {
        groupMap.put(groupName, group);
      }
    }

    if (!badGroups.isEmpty()) {
      errorLog += "\n" + String.format(I18n.get(IMessage.NO_GROUP), badGroups);
    }

    return groupMap;
  }

  protected Map<String, Role> checkBadRoles(String[] roleRow) {

    List<String> badroles = new ArrayList<String>();
    Map<String, Role> roleMap = new HashMap<String, Role>();

    for (Integer len = header.size() + 1; len < roleRow.length; len += groupHeader.size()) {

      String roleName = roleRow[len];
      Role role = roleRepo.all().filter("self.name = ?1", roleName).fetchOne();
      if (roleName == null) {
        badroles.add(roleName);
      } else {
        roleMap.put(roleName, role);
      }
    }

    if (!badroles.isEmpty()) {
      errorLog += "\n" + String.format(I18n.get(IMessage.NO_ROLE), badroles);
    }

    return roleMap;
  }

  protected String checkObject(String objectName) {

    MetaModel model = modelRepository.all().filter("self.fullName = ?1", objectName).fetchOne();

    if (model == null) {
      errorLog += "\n" + String.format(I18n.get(IMessage.NO_OBJECT), objectName);
      return null;
    }

    return objectName;
  }

  protected void processGroupCSV(
      CSVReader csvReader,
      String[] groupRow,
      Map<String, Group> groupMap,
      MetaField field,
      Boolean fieldBoolean)
      throws IOException {

    Map<String, MetaPermission> metaPermDict = new HashMap<String, MetaPermission>();
    String objectName = null;

    String[] row = csvReader.readNext();
    while (row != null) {
      for (Integer groupIndex = header.size() + 1;
          groupIndex < row.length;
          groupIndex += groupHeader.size()) {

        String groupName = groupRow[groupIndex];
        if (!groupMap.containsKey(groupName)) {
          continue;
        }

        String[] rowGroup = Arrays.copyOfRange(row, groupIndex, groupIndex + groupHeader.size());

        if (!Strings.isNullOrEmpty(groupName) && !Strings.isNullOrEmpty(row[0])) {
          objectName = checkObject(row[0]);
          if (objectName == null) {
            break;
          }
          if (fieldBoolean) {
            metaPermDict.put(groupName, getMetaPermission(groupMap.get(groupName), objectName));
          }
          updatePermission(groupMap.get(groupName), objectName, field, rowGroup);
        } else if (fieldBoolean && objectName != null && !Strings.isNullOrEmpty(row[1])) {
          updateFieldPermission(metaPermDict.get(groupName), row[1], rowGroup);
        }
      }

      row = csvReader.readNext();
    }
  }

  protected void processRoleCSV(
      CSVReader csvReader,
      String[] roleRow,
      Map<String, Role> roleMap,
      MetaField field,
      Boolean fieldPermission)
      throws IOException {

    Map<String, MetaPermission> metaPermDict = new HashMap<String, MetaPermission>();
    String objectName = null;

    String[] row = csvReader.readNext();
    while (row != null) {

      for (Integer groupIndex = header.size() + 1;
          groupIndex < row.length;
          groupIndex += groupHeader.size()) {

        String roleName = roleRow[groupIndex];
        if (!roleMap.containsKey(roleName)) {
          continue;
        }

        String[] rowGroup = Arrays.copyOfRange(row, groupIndex, groupIndex + groupHeader.size());

        if (!Strings.isNullOrEmpty(roleName) && !Strings.isNullOrEmpty(row[0])) {
          objectName = checkObject(row[0]);
          if (objectName == null) {
            break;
          }
          if (fieldPermission) {
            metaPermDict.put(roleName, getMetaPermission(roleMap.get(roleName), objectName));
          }
          updatePermission(roleMap.get(roleName), objectName, field, rowGroup);
        } else if (fieldPermission && objectName != null && !Strings.isNullOrEmpty(row[1])) {
          updateFieldPermission(metaPermDict.get(roleName), row[1], rowGroup);
        }
      }

      row = csvReader.readNext();
    }
  }

  public MetaPermission getMetaPermission(Group group, String objectName) {

    String[] objectNames = objectName.split("\\.");
    String groupName = group.getCode();
    String permName = getPermissionName(null, objectNames[objectNames.length - 1], group.getCode());
    MetaPermission metaPermission =
        metaPermissionRepository.all().filter("self.name = ?1", permName).fetchOne();

    if (metaPermission == null) {
      LOG.debug("Create metaPermission group: {}, object: {}", groupName, objectName);

      metaPermission = new MetaPermission();
      metaPermission.setName(permName);
      metaPermission.setObject(objectName);

      group.addMetaPermission(metaPermission);
    }

    return metaPermission;
  }

  public MetaPermission getMetaPermission(Role role, String objectName) {

    String[] objectNames = objectName.split("\\.");
    String roleName = role.getName();
    String permName = getPermissionName(null, objectNames[objectNames.length - 1], roleName);
    MetaPermission metaPermission =
        metaPermissionRepository.all().filter("self.name = ?1", permName).fetchOne();

    if (metaPermission == null) {
      LOG.debug("Create metaPermission role: {}, object: {}", roleName, objectName);

      metaPermission = new MetaPermission();
      metaPermission.setName(permName);
      metaPermission.setObject(objectName);

      role.addMetaPermission(metaPermission);
    }

    return metaPermission;
  }

  public MetaPermission updateFieldPermission(
      MetaPermission metaPermission, String field, String[] row) {

    MetaPermissionRule permissionRule =
        ruleRepository
            .all()
            .filter(
                "self.field = ?1 and self.metaPermission.name = ?2",
                field,
                metaPermission.getName())
            .fetchOne();

    if (permissionRule == null) {
      permissionRule = new MetaPermissionRule();
      permissionRule.setMetaPermission(metaPermission);
      permissionRule.setField(field);
    }

    permissionRule.setCanRead(row[0].equalsIgnoreCase("x"));
    permissionRule.setCanWrite(row[1].equalsIgnoreCase("x"));
    permissionRule.setCanExport(row[4].equalsIgnoreCase("x"));
    permissionRule.setReadonlyIf(row[5]);
    permissionRule.setHideIf(row[6]);
    metaPermission.addRule(permissionRule);

    return metaPermission;
  }

  public void updatePermission(Group group, String objectName, MetaField field, String[] row) {

    String[] objectNames = objectName.split("\\.");
    String permName =
        getPermissionName(field, objectNames[objectNames.length - 1], group.getCode());

    Permission permission =
        permissionRepository.all().filter("self.name = ?1", permName).fetchOne();
    boolean newPermission = false;

    if (permission == null) {
      newPermission = true;
      permission = new Permission();
      permission.setName(permName);
      permission.setObject(objectName);
    }

    permission.setCanRead(row[0].equalsIgnoreCase("x"));
    permission.setCanWrite(row[1].equalsIgnoreCase("x"));
    permission.setCanCreate(row[2].equalsIgnoreCase("x"));
    permission.setCanRemove(row[3].equalsIgnoreCase("x"));
    permission.setCanExport(row[4].equalsIgnoreCase("x"));

    if (newPermission) {
      group.addPermission(permission);
    }
  }

  public void updatePermission(Role role, String objectName, MetaField field, String[] row) {

    String[] objectNames = objectName.split("\\.");
    String permName = getPermissionName(field, objectNames[objectNames.length - 1], role.getName());

    Permission permission =
        permissionRepository.all().filter("self.name = ?1", permName).fetchOne();
    boolean newPermission = false;

    if (permission == null) {
      newPermission = true;
      permission = new Permission();
      permission.setName(permName);
      permission.setObject(objectName);
    }

    permission.setCanRead(row[0].equalsIgnoreCase("x"));
    permission.setCanWrite(row[1].equalsIgnoreCase("x"));
    permission.setCanCreate(row[2].equalsIgnoreCase("x"));
    permission.setCanRemove(row[3].equalsIgnoreCase("x"));
    permission.setCanExport(row[4].equalsIgnoreCase("x"));
    permission.setCondition(row[5]);
    permission.setConditionParams(row[6]);

    if (newPermission) {
      role.addPermission(permission);
    }
  }
}

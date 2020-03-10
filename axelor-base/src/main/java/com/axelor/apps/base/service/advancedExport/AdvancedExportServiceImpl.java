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
package com.axelor.apps.base.service.advancedExport;

import com.axelor.apps.base.db.AdvancedExport;
import com.axelor.apps.base.db.AdvancedExportLine;
import com.axelor.apps.tool.NamingTool;
import com.axelor.auth.AuthUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.db.hibernate.type.JsonFunction;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.db.repo.MetaSelectRepository;
import com.axelor.rpc.filter.Filter;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedExportServiceImpl implements AdvancedExportService {

  private static final Logger log = LoggerFactory.getLogger(AdvancedExportServiceImpl.class);

  @Inject private MetaFieldRepository metaFieldRepo;

  @Inject private MetaModelRepository metaModelRepo;

  @Inject private MetaSelectRepository metaSelectRepo;

  @Inject private AdvancedExportGeneratorFactory exportGeneratorFactory;

  private LinkedHashSet<String> joinFieldSet = new LinkedHashSet<>(),
      selectionJoinFieldSet = new LinkedHashSet<>();

  private List<Object> params = null;

  private String exportFileName, language, selectField, aliasName;
  private boolean isReachMaxExportLimit, isNormalField, isSelectionField = false;
  private int msi, mt;

  /**
   * This method split and join the all fields/columns which are selected by user and create the
   * query.
   *
   * @param advancedExport
   * @param criteria
   * @return
   * @throws AxelorException
   * @throws ClassNotFoundException
   */
  @Override
  public Query getAdvancedExportQuery(AdvancedExport advancedExport, List<Long> recordIds)
      throws AxelorException {

    StringBuilder selectFieldBuilder = new StringBuilder(),
        orderByFieldBuilder = new StringBuilder();

    joinFieldSet.clear();
    selectionJoinFieldSet.clear();
    isNormalField = true;
    selectField = "";
    msi = 0;
    mt = 0;
    int col = 0;
    language = AuthUtils.getUser().getLanguage();

    try {
      for (AdvancedExportLine advancedExportLine : advancedExport.getAdvancedExportLineList()) {
        String[] splitField = advancedExportLine.getTargetField().split("\\.");
        String alias = "Col_" + col;

        createQueryParts(splitField, 0, advancedExport.getMetaModel());

        selectFieldBuilder.append(aliasName + selectField + " AS " + alias + ",");

        if (advancedExportLine.getOrderBy()) {
          orderByFieldBuilder.append(alias + " " + advancedExportLine.getOrderByType() + ",");
        }
        selectField = "";
        aliasName = "";
        col++;
      }
    } catch (ClassNotFoundException e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR);
    }
    return createQuery(
        createQueryBuilder(
            advancedExport.getMetaModel(), selectFieldBuilder, recordIds, orderByFieldBuilder));
  }

  /**
   * This method create query parts based on field.
   *
   * @param splitField
   * @param parentIndex
   * @param metaModel
   * @throws ClassNotFoundException
   */
  protected void createQueryParts(String[] splitField, int parentIndex, MetaModel metaModel)
      throws ClassNotFoundException {

    while (parentIndex <= splitField.length - 1) {
      MetaField relationalField =
          metaFieldRepo
              .all()
              .filter("self.name = ?1 and self.metaModel = ?2", splitField[parentIndex], metaModel)
              .fetchOne();
      MetaModel subMetaModel =
          metaModelRepo.all().filter("self.name = ?1", relationalField.getTypeName()).fetchOne();

      if (!Strings.isNullOrEmpty(relationalField.getRelationship())) {
        checkRelationalField(splitField, parentIndex);
      } else {
        checkSelectionField(splitField, parentIndex, metaModel);
        checkNormalField(splitField, parentIndex);
      }
      parentIndex += 1;
      metaModel = subMetaModel;
    }
  }

  protected void checkRelationalField(String[] splitField, int parentIndex) {
    String tempAliasName = "";
    isNormalField = false;
    if (parentIndex != 0) {
      tempAliasName = isKeyword(splitField, 0);
      aliasName = tempAliasName;

      for (int subIndex = 1; subIndex <= parentIndex; subIndex++) {
        tempAliasName = isKeyword(splitField, subIndex);
        if (!aliasName.equals(splitField[parentIndex])) {
          joinFieldSet.add(
              "LEFT JOIN " + aliasName + "." + splitField[subIndex] + " " + tempAliasName);
          aliasName = tempAliasName;
        }
      }
    } else {
      tempAliasName = isKeyword(splitField, parentIndex);
      joinFieldSet.add("LEFT JOIN self." + splitField[parentIndex] + " " + tempAliasName);
      aliasName = tempAliasName;
    }
  }

  protected String isKeyword(String[] fieldNames, int ind) {
    if (NamingTool.isKeyword(fieldNames[ind])) {
      return fieldNames[ind] + "_id";
    }
    return fieldNames[ind];
  }

  protected void checkSelectionField(String[] fieldName, int index, MetaModel metaModel)
      throws ClassNotFoundException {

    Class<?> klass = Class.forName(metaModel.getFullName());
    Mapper mapper = Mapper.of(klass);
    MetaSelect metaSelect =
        metaSelectRepo.findByName(mapper.getProperty(fieldName[index]).getSelection());

    if (metaSelect != null) {
      isSelectionField = true;
      String alias = "self";
      msi++;
      mt++;
      if (!isNormalField && index != 0) {
        alias = aliasName;
      }
      addSelectionField(fieldName[index], alias, metaSelect.getId());
    }
  }

  protected void addSelectionField(String fieldName, String alias, Long metaSelectId) {
    String selectionJoin =
        "LEFT JOIN "
            + "MetaSelectItem "
            + ("msi_" + (msi))
            + " ON CAST("
            + alias
            + "."
            + fieldName
            + " AS text) = "
            + ("msi_" + (msi))
            + ".value AND "
            + ("msi_" + (msi))
            + ".select = "
            + metaSelectId;

    if (language.equals(LANGUAGE_FR)) {
      selectionJoin +=
          " LEFT JOIN "
              + "MetaTranslation "
              + ("mt_" + (mt))
              + " ON "
              + ("msi_" + (msi))
              + ".title = "
              + ("mt_" + (mt))
              + ".key AND "
              + ("mt_" + (mt))
              + ".language = \'"
              + language
              + "\'";
    }
    selectionJoinFieldSet.add(selectionJoin);
  }

  protected void checkNormalField(String[] splitField, int parentIndex) {

    if (isSelectionField) {
      if (parentIndex == 0) {
        selectField = "";
      }
      if (language.equals(LANGUAGE_FR)) {
        aliasName =
            "COALESCE ("
                + "NULLIF"
                + "("
                + ("mt_" + (mt))
                + ".message, '') , "
                + ("msi_" + (msi))
                + ".title)";
        selectField += "";
      } else {
        aliasName = ("msi_" + (msi));
        selectField += ".title";
      }
      isSelectionField = false;
    } else {
      if (parentIndex == 0) {
        selectField = "";
        aliasName = "self";
      }
      selectField += "." + splitField[parentIndex];
    }
  }

  /**
   * This method build a dynamic query using <i>StringBuilder</i>.
   *
   * @param metaModel
   * @param selectFieldBuilder
   * @param selectJoinFieldBuilder
   * @param selectionFieldBuilder
   * @param criteria
   * @param orderByFieldBuilder
   * @return
   */
  protected StringBuilder createQueryBuilder(
      MetaModel metaModel,
      StringBuilder selectFieldBuilder,
      List<Long> recordIds,
      StringBuilder orderByFieldBuilder) {

    String joinField = "", selectionJoinField = "", orderByCol = "";

    joinField = String.join(" ", joinFieldSet);
    selectionJoinField = String.join(" ", selectionJoinFieldSet);

    params = null;
    String criteria = getCriteria(metaModel, recordIds);

    if (!orderByFieldBuilder.toString().equals(""))
      orderByCol =
          " ORDER BY " + orderByFieldBuilder.substring(0, orderByFieldBuilder.length() - 1);

    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("SELECT NEW List(");
    queryBuilder.append(selectFieldBuilder.substring(0, selectFieldBuilder.length() - 1));
    queryBuilder.append(") FROM " + metaModel.getName() + " self ");
    queryBuilder.append((!Strings.isNullOrEmpty(joinField)) ? joinField + " " : "");
    queryBuilder.append(
        (!Strings.isNullOrEmpty(selectionJoinField)) ? selectionJoinField + " " : "");
    queryBuilder.append((!Strings.isNullOrEmpty(criteria)) ? criteria : "");
    queryBuilder.append((!Strings.isNullOrEmpty(orderByCol)) ? orderByCol : "");

    return queryBuilder;
  }

  /**
   * This method make <i>WHERE</i> clause with criteria.
   *
   * @param metaModel
   * @param criteria
   * @return
   */
  protected String getCriteria(MetaModel metaModel, List<Long> recordIds) {
    String criteria = null;
    if (recordIds != null) {
      criteria = recordIds.toString().substring(1, recordIds.toString().length() - 1);
      log.trace("criteria : {}", recordIds.toString());
      criteria = " WHERE self.id IN (" + criteria + ")";
    }
    Filter filter = getJpaSecurityFilter(metaModel);
    JoinHelper helper = null;
    if (filter != null) {
      String permissionFilter = filter.getQuery();
      try {
        helper = new JoinHelper(Class.forName(metaModel.getFullName()));
        permissionFilter = helper.parse(permissionFilter).toString();
      } catch (ClassNotFoundException e) {
        TraceBackService.trace(e, e.getMessage());
      }
      if (recordIds == null) {
        criteria = " WHERE " + permissionFilter;
      } else {
        criteria += " AND (" + permissionFilter + ")";
      }
      params = filter.getParams();
    }

    if (helper != null) {
      criteria = helper.toString() + " " + criteria;
    }

    return criteria;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Filter getJpaSecurityFilter(MetaModel metaModel) {
    JpaSecurity jpaSecurity = Beans.get(JpaSecurity.class);
    try {
      return jpaSecurity.getFilter(
          JpaSecurity.CAN_EXPORT,
          (Class<? extends Model>) Class.forName(metaModel.getFullName()),
          (Long) null);
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage());
    }
    return null;
  }

  protected Query createQuery(StringBuilder queryBuilder) {
    int n = 0, i = queryBuilder.indexOf("?");
    while (i > -1) {
      queryBuilder.replace(i, i + 1, "?" + (++n));
      i = queryBuilder.indexOf("?", i + 1);
    }
    log.debug("query : {}", queryBuilder.toString());
    Query query = JPA.em().createQuery(queryBuilder.toString(), List.class);
    if (params != null) {
      for (i = 0; i < params.size(); i++) {
        query.setParameter(i + 1, params.get(i));
      }
    }
    return query;
  }

  protected List<AdvancedExportLine> sortAdvancedExportLineList(
      List<AdvancedExportLine> advancedExportLineList) {

    advancedExportLineList.sort(
        new Comparator<AdvancedExportLine>() {

          @Override
          public int compare(AdvancedExportLine line1, AdvancedExportLine line2) {
            if (line1.getSequence() == line2.getSequence()) {
              if (line1.getId() > line2.getId()) {
                return 1;
              } else {
                return -1;
              }
            }
            return line1.getSequence() - line2.getSequence();
          }
        });
    return advancedExportLineList;
  }

  /**
   * Initialize the object of <i>AdvancedExportGenerator</i> based on file type and generate the
   * export file.
   *
   * @throws AxelorException
   */
  @Override
  public File export(AdvancedExport advancedExport, List<Long> recordIds, String fileType)
      throws AxelorException {

    AdvancedExportGenerator exportGenerator =
        exportGeneratorFactory.getAdvancedExportGenerator(advancedExport, fileType);

    sortAdvancedExportLineList(advancedExport.getAdvancedExportLineList());

    Query query = getAdvancedExportQuery(advancedExport, recordIds);

    File file = exportGenerator.generateFile(query);
    isReachMaxExportLimit = exportGenerator.getIsReachMaxExportLimit();
    exportFileName = exportGenerator.getExportFileName();
    return file;
  }

  @Override
  public boolean getIsReachMaxExportLimit() {
    return isReachMaxExportLimit;
  }

  @Override
  public String getExportFileName() {
    return exportFileName;
  }

  /**
   * JoinHelper class is used to auto generate <code>LEFT JOIN</code> for association expressions.
   *
   * <p>For example:
   *
   * <pre>
   * 	Query<Contact> q = Contact.all().filter("self.title.code = ?1 OR self.age > ?2", "mr", 20);
   * </pre>
   *
   * Results in:
   *
   * <pre>
   * SELECT self FROM Contact self LEFT JOIN self.title _title WHERE _title.code = ?1 OR self.age > ?2
   * </pre>
   *
   * So that all the records are matched even if <code>title</code> field is null.
   */
  private static class JoinHelper {

    private Class<?> beanClass;

    private Map<String, String> joins = Maps.newLinkedHashMap();

    private static final Pattern pathPattern =
        Pattern.compile("self\\." + "((?:[a-zA-Z_]\\w+)(?:(?:\\[\\])?\\.\\w+)*)");

    public JoinHelper(Class<?> beanClass) {
      this.beanClass = beanClass;
    }

    /**
     * Parse the given filter string and return transformed filter expression.
     *
     * <p>Automatically calculate <code>LEFT JOIN</code> for association path expressions and the
     * path expressions are replaced with the join variables.
     *
     * @param filter the filter expression
     * @return the transformed filter expression
     */
    protected String parse(String filter) {

      String result = "";
      Matcher matcher = pathPattern.matcher(filter);

      int last = 0;
      while (matcher.find()) {
        MatchResult matchResult = matcher.toMatchResult();
        String alias = joinName(matchResult.group(1));
        if (alias == null) {
          alias = "self." + matchResult.group(1);
        }
        result += filter.substring(last, matchResult.start()) + alias;
        last = matchResult.end();
      }
      if (last < filter.length()) result += filter.substring(last);

      return result;
    }

    /**
     * Automatically generate <code>LEFT JOIN</code> for the given name (association path
     * expression) and return the join variable.
     *
     * @param name the path expression or field name
     * @return join variable if join is created else returns name
     */
    public String joinName(String name) {

      Mapper mapper = Mapper.of(beanClass);
      String[] path = name.split("\\.");
      String prefix = null;
      String variable = name;

      if (path.length > 1) {
        variable = path[path.length - 1];
        String joinOn = null;
        Mapper currentMapper = mapper;
        for (int i = 0; i < path.length - 1; i++) {
          String item = path[i].replace("[]", "");
          Property property = currentMapper.getProperty(item);
          if (property == null) {
            throw new org.hibernate.QueryException(
                "could not resolve property: "
                    + item
                    + " of: "
                    + currentMapper.getBeanClass().getName());
          }

          if (property.isJson()) {
            return JsonFunction.fromPath(name).toString();
          }

          if (prefix == null) {
            joinOn = "self." + item;
            prefix = "_" + item;
          } else {
            joinOn = prefix + "." + item;
            prefix = prefix + "_" + item;
          }
          if (!joins.containsKey(joinOn)) {
            joins.put(joinOn, prefix);
          }

          if (property.getTarget() != null) {
            currentMapper = Mapper.of(property.getTarget());
          }

          if (i == path.length - 2) {
            property = currentMapper.getProperty(variable);
            if (property == null) {
              throw new IllegalArgumentException(
                  String.format(
                      "No such field '%s' in object '%s'",
                      variable, currentMapper.getBeanClass().getName()));
            }
            if (property.isReference()) {
              joinOn = prefix + "." + variable;
              prefix = prefix + "_" + variable;
              joins.put(joinOn, prefix);
              return prefix;
            }
          }
        }
      } else {
        Property property = mapper.getProperty(name);
        if (property == null) {
          throw new IllegalArgumentException(
              String.format("No such field '%s' in object '%s'", variable, beanClass.getName()));
        }
        if (property.isCollection()) {
          return null;
        }
        if (property.getTarget() != null) {
          prefix = "_" + name;
          joins.put("self." + name, prefix);
          return prefix;
        }
      }

      if (prefix == null) {
        prefix = "self";
      }

      return prefix + "." + variable;
    }

    @Override
    public String toString() {
      if (joins.size() == 0) return "";
      List<String> joinItems = Lists.newArrayList();
      for (String key : joins.keySet()) {
        String val = joins.get(key);
        joinItems.add("LEFT JOIN " + key + " " + val);
      }
      return " " + Joiner.on(" ").join(joinItems);
    }
  }
}

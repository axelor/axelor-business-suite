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
package com.axelor.apps.base.service.administration;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.service.user.UserService;
import com.axelor.apps.tool.file.CsvTool;
import com.axelor.apps.tool.xml.XPathParse;
import com.axelor.auth.db.Group;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaMenu;
import com.axelor.meta.db.MetaTranslation;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.axelor.meta.db.repo.MetaMenuRepository;
import com.axelor.meta.db.repo.MetaTranslationRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ExportDbObjectService {

  @Inject UserService uis;

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static String[] fieldAttrs = new String[] {"name", "type", "title"};

  public String[] csvHeaders =
      new String[] {
        "object", "module", "name", "type", "title_en", "title_fr", "help_en", "help_fr", "url"
      };

  private List<String[]> fieldDataList = new ArrayList<String[]>();

  private List<String> objectList = new ArrayList<String>();

  private Map<String, Object> objectMap = new HashMap<String, Object>();

  private Group group = null;

  @Transactional
  public MetaFile exportObject() {

    //		group = AuthUtils.getUser().getGroup();
    group = Beans.get(GroupRepository.class).all().filter("self.code = 'admins'").fetchOne();
    try {
      log.debug("Attachment dir: {}", AppSettings.get().get("file.upload.dir"));
      String uploadDir = AppSettings.get().get("file.upload.dir");
      if (uploadDir == null || !new File(uploadDir).exists()) {
        return null;
      }
      String appSrc = AppSettings.get().get("application.src");
      log.debug("Module dir: {}", appSrc);
      if (appSrc == null) {
        return null;
      }
      File moduleDir = new File(appSrc);
      if (!moduleDir.exists()) {
        return null;
      }

      MetaFile metaFile = new MetaFile();
      String fileName =
          "ExportObject-"
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyMMddHHmmSS"))
              + ".csv";
      metaFile.setFileName(fileName);
      metaFile.setFilePath(fileName);
      metaFile = Beans.get(MetaFileRepository.class).save(metaFile);

      SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
      updateObjectMap(
          Arrays.asList(moduleDir.listFiles()), saxParserFactory.newSAXParser(), new XmlHandler());

      writeObjects(MetaFiles.getPath(metaFile).toFile());

      return metaFile;

    } catch (ParserConfigurationException | SAXException | IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  protected void writeObjects(File objectFile) {
    try {
      List<? extends MetaMenu> menuList =
          Beans.get(MetaMenuRepository.class)
              .all()
              .filter("self.parent = null AND self.left = true AND ?1 MEMBER OF self.groups", group)
              .order("-priority")
              .order("id")
              .fetch();
      log.debug("Total root menus: {}", menuList.size());
      generateMenuGraph(menuList);
      CsvTool.csvWriter(
          objectFile.getParent(), objectFile.getName(), ';', csvHeaders, fieldDataList);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void generateMenuGraph(List<? extends MetaMenu> menuList) {
    // log.debug("Checking menu list: {}",menuList);
    for (MetaMenu menu : menuList) {
      String model = menu.getAction() != null ? menu.getAction().getModel() : null;
      // log.debug("Action model: ",model);
      if (model != null && !objectList.contains(model)) {
        updateFieldData(menu.getAction());
      }
      // List<? extends MetaMenu> childList = MetaMenu.all().filter("self.parent = ?1 AND self.left
      // = true AND ?2 MEMBER OF self.groups", menu,group).order("-priority").order("id").fetch();
      List<? extends MetaMenu> childList =
          Beans.get(MetaMenuRepository.class)
              .all()
              .filter("self.parent = ?1 AND self.left = true", menu)
              .order("-priority")
              .order("id")
              .fetch();
      generateMenuGraph(childList);
    }
  }

  @SuppressWarnings("unchecked")
  protected void updateFieldData(MetaAction action) {
    String[] objectName = action.getModel().split("\\.");
    String objName = objectName[objectName.length - 1];
    Map<String, Object> moduleMap = (Map<String, Object>) objectMap.get(objName);
    if (moduleMap == null) {
      return;
    }
    boolean addObject = true;

    MetaTranslationRepository metaTranslationRepo = Beans.get(MetaTranslationRepository.class);

    // log.debug("Adding object: {}",objName);
    for (Entry<String, Object> module : moduleMap.entrySet()) {
      boolean addModule = true;
      for (Map<String, String> field : (List<Map<String, String>>) module.getValue()) {
        String[] fields = new String[csvHeaders.length];
        fields[0] = "";
        if (addObject) {
          fields[0] = action.getModel();
          fields[8] = getActionUrl(action);
        }
        fields[1] = "";
        if (addModule) {
          fields[1] = module.getKey();
        }
        fields[2] = field.get("name");
        fields[3] = field.get("type");
        fields[4] = field.get("title");
        MetaTranslation mts = metaTranslationRepo.findByKey(field.get("title"), "fr");
        if (mts != null) {
          fields[5] = mts.getMessage();
        }
        mts = metaTranslationRepo.findByKey("help:" + objName + "." + field.get("name"), "en");
        if (mts != null) {
          fields[6] = mts.getMessage().replace(";", "\n");
        }
        mts = metaTranslationRepo.findByKey("help:" + objName + "." + field.get("name"), "fr");
        if (mts != null) {
          fields[7] = mts.getMessage().replace(";", "\n");
        }
        fieldDataList.add(fields);
        addObject = false;
        addModule = false;
      }
    }
    objectList.add(action.getModel());
  }

  protected String getActionUrl(MetaAction action) {

    String url = AppSettings.get().getBaseURL() + "#/ds";
    String viewType = getActionViewType(action.getXml());
    if (viewType.equals("grid")) {
      url = url + "/" + action.getName() + "/list/1";
    } else if (viewType.equals("form")) {
      url = url + "/" + action.getName() + "/edit";
    } else if (viewType.equals("calendar")) {
      url = url + "/" + action.getName() + "/calendar";
    }

    return url;
  }

  public static String getActionViewType(String xml) {

    DocumentBuilderFactory domFactory = Beans.get(XPathParse.class).getDocumentBuilderFactory();
    domFactory.setNamespaceAware(true); // never forget this!
    DocumentBuilder builder;

    try {
      builder = domFactory.newDocumentBuilder();
      File tempXml = File.createTempFile("Temp", "xml");
      try (FileWriter fw = new FileWriter(tempXml)) {
        fw.write(xml);
        Document doc = builder.parse(new FileInputStream(tempXml));
        Node child = doc.getFirstChild();
        NodeList chs = child.getChildNodes();
        for (Integer i = 0; i < chs.getLength(); i++) {
          if (chs.item(i).getNodeName().equals("view")) {
            NamedNodeMap attributes = chs.item(i).getAttributes();
            return attributes.getNamedItem("type").getNodeValue();
          }
        }
        return "";
      }
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  protected void updateObjectMap(List<File> modules, SAXParser parser, XmlHandler xmlHandler)
      throws SAXException, IOException {

    for (File module : modules) {
      String modulePath = module.getAbsolutePath();
      File modelDir = new File(modulePath + "/src/main/resources/domains/");
      if (!modelDir.exists()) {
        continue;
      }
      // log.debug("Module : {}",modelDir.getAbsolutePath());

      for (File objectFile : modelDir.listFiles()) {
        // log.debug("Parsing domain : {}",objectFile.getName());
        String objectName = objectFile.getName().split("\\.")[0];
        parser.parse(new InputSource(new FileInputStream(objectFile)), xmlHandler);
        Map<String, Object> moduleMap = (Map<String, Object>) objectMap.get(objectName);
        if (moduleMap == null) {
          moduleMap = new HashMap<String, Object>();
        }
        moduleMap.put(
            module.getName(),
            updateObjectModel(xmlHandler.fieldList, objectName, module.getName()));
        objectMap.put(objectName, moduleMap);
      }
    }
  }

  protected Object updateObjectModel(
      List<Map<String, String>> fieldList, String objectName, String moduleName) {

    for (Map<String, String> field : fieldList) {
      field.put("module", moduleName);
      field.put("object", objectName);
    }
    return fieldList;
  }
}

class XmlHandler extends DefaultHandler {

  public List<Map<String, String>> fieldList;

  private boolean isObject = false;

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes)
      throws SAXException {
    switch (qName) {
      case "entity":
        {
          isObject = true;
          fieldList = new ArrayList<Map<String, String>>();
          break;
        }
      default:
        {
          if (isObject) {
            Map<String, String> fieldMap = new HashMap<String, String>();
            fieldMap.put("type", qName);
            for (String fieldAttr : Arrays.asList(ExportDbObjectService.fieldAttrs)) {
              if (attributes.getValue(fieldAttr) != null) {
                fieldMap.put(fieldAttr, attributes.getValue(fieldAttr));
              }
            }
            fieldList.add(fieldMap);
          }
        }
    }
    super.startElement(uri, localName, qName, attributes);
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {

    if (qName.equals("entity")) {
      isObject = false;
    }
    super.endElement(uri, localName, qName);
  }
}

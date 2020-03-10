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
package com.axelor.apps.base.service.app;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.App;
import com.axelor.apps.base.db.repo.AppRepository;
import com.axelor.apps.base.exceptions.IExceptionMessages;
import com.axelor.common.FileUtils;
import com.axelor.common.Inflector;
import com.axelor.data.Importer;
import com.axelor.data.csv.CSVConfig;
import com.axelor.data.csv.CSVImporter;
import com.axelor.data.csv.CSVInput;
import com.axelor.data.xml.XMLImporter;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import javax.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AppServiceImpl implements AppService {

  private final Logger log = LoggerFactory.getLogger(AppService.class);

  private static final String DIR_DEMO = "demo";

  private static final String DIR_INIT = "data-init" + File.separator + "app";

  private static final String DIR_ROLES = "roles";

  private static final String CONFIG_PATTERN = "-config.xml";

  private static final String IMG_DIR = "img";

  private static final String EXT_DIR = "extra";

  private static Pattern patCsv = Pattern.compile("^\\<\\s*csv-inputs");

  private static Pattern patXml = Pattern.compile("^\\<\\s*xml-inputs");

  private Inflector inflector = Inflector.getInstance();

  @Inject private AppRepository appRepo;

  @Inject private MetaModelRepository metaModelRepo;

  @Override
  public App importDataDemo(App app) throws AxelorException {

    if (app.getDemoDataLoaded()) {
      return app;
    }

    log.debug("Demo import: App code: {}, App lang: {}", app.getCode(), app.getLanguageSelect());
    importParentData(app);

    String lang = getLanguage(app);
    if (lang == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessages.NO_LANGUAGE_SELECTED));
    }

    importData(app, DIR_DEMO, true);

    app = appRepo.find(app.getId());

    app.setDemoDataLoaded(true);

    return saveApp(app);
  }

  @Transactional
  public App saveApp(App app) {
    return appRepo.save(app);
  }

  protected void importData(App app, String dataDir, boolean useLang) {

    String modules = app.getModules();
    if (modules == null) {
      return;
    }
    String code = app.getCode();
    String lang = useLang ? getLanguage(app) : "";

    log.debug("Data import: DataDir: {}, App code: {}, App lang: {}", dataDir, code, lang);

    for (String module : modules.split(",")) {
      File tmp = extract(module, dataDir, lang, code);
      if (tmp == null) {
        continue;
      }
      log.debug("Importing from module: {}", module);
      importPerConfig(code, new File(tmp, dataDir));
    }
  }

  protected void importPerConfig(String appCode, File dataDir) {

    try {
      File[] configs =
          dataDir.listFiles(
              new FilenameFilter() {
                public boolean accept(File dir, String name) {
                  return name.startsWith(appCode + "-") && name.endsWith(CONFIG_PATTERN);
                }
              });

      if (configs.length == 0) {
        log.debug("No config file found for the app: {}", appCode);
        return;
      }

      Arrays.sort(configs);

      for (File config : configs) {
        runImport(config, dataDir);
      }

    } finally {
      clean(dataDir);
    }
  }

  protected String getLanguage(App app) {

    String lang = app.getLanguageSelect();

    if (app.getLanguageSelect() == null) {
      lang = AppSettings.get().get("application.locale");
    }

    return lang;
  }

  protected void importParentData(App app) throws AxelorException {

    List<App> depends = getDepends(app, true);

    for (App parent : depends) {
      parent = appRepo.find(parent.getId());
      if (!parent.getDemoDataLoaded()) {
        importDataDemo(parent);
      }
    }
  }

  protected App importDataInit(App app) {

    String lang = getLanguage(app);
    if (lang == null) {
      return app;
    }

    importData(app, DIR_INIT, true);

    app = appRepo.find(app.getId());

    app.setInitDataLoaded(true);

    return app;
  }

  protected void runImport(File config, File data) {

    log.debug(
        "Running import with config path: {}, data path: {}",
        config.getAbsolutePath(),
        data.getAbsolutePath());

    try (Scanner scanner = new Scanner(config)) {
      Importer importer = null;
      while (scanner.hasNextLine()) {
        String str = scanner.nextLine();
        if (patCsv.matcher(str).find()) {
          importer = new CSVImporter(config.getAbsolutePath(), data.getAbsolutePath(), null);
          break;
        }
        if (patXml.matcher(str).find()) {
          importer = new XMLImporter(config.getAbsolutePath(), data.getAbsolutePath());
          break;
        }
      }
      scanner.close();

      if (importer != null) {
        importer.run();
      }

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  protected File extract(String module, String dirName, String lang, String code) {
    String dirNamePattern = dirName.replaceAll("/|\\\\", "(/|\\\\\\\\)");
    List<URL> files = new ArrayList<URL>();
    files.addAll(MetaScanner.findAll(module, dirNamePattern, code + "(-+.*)?" + CONFIG_PATTERN));
    if (files.isEmpty()) {
      return null;
    }
    if (lang.isEmpty()) {
      files.addAll(MetaScanner.findAll(module, dirNamePattern, code + "*"));
    } else {
      String dirPath = dirName + "/";
      files.addAll(fetchUrls(module, dirPath + IMG_DIR));
      files.addAll(fetchUrls(module, dirPath + EXT_DIR));
      files.addAll(fetchUrls(module, dirPath + lang));
    }

    final File tmp = Files.createTempDir();
    final String dir = dirName.replace("\\", "/");

    for (URL file : files) {
      String name = file.toString();
      name = name.substring(name.lastIndexOf(dir));
      if (!lang.isEmpty()) {
        name = name.replace(dir + "/" + lang, dir);
      }
      if (File.separatorChar == '\\') {
        name = name.replace("/", "\\");
      }
      try {
        copy(file.openStream(), tmp, name);
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }

    return tmp;
  }

  protected List<URL> fetchUrls(String module, String fileName) {
    final String fileNamePattern = fileName.replaceAll("/|\\\\", "(/|\\\\\\\\)");
    return MetaScanner.findAll(module, fileNamePattern, "(.+?)");
  }

  protected void copy(InputStream in, File toDir, String name) throws IOException {
    File dst = FileUtils.getFile(toDir, name);
    Files.createParentDirs(dst);
    FileOutputStream out = new FileOutputStream(dst);
    try {
      ByteStreams.copy(in, out);
    } finally {
      out.close();
    }
  }

  protected void clean(File file) {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        clean(child);
      }
      file.delete();
    } else if (file.exists()) {
      file.delete();
    }
  }

  @Override
  public App getApp(String code) {
    return appRepo.findByCode(code);
  }

  @Override
  public boolean isApp(String code) {
    App app = getApp(code);
    if (app == null) {
      return false;
    }

    return app.getActive();
  }

  protected List<App> getDepends(App app, Boolean active) {

    List<App> apps = new ArrayList<App>();
    app = appRepo.find(app.getId());

    for (App depend : app.getDependsOnSet()) {
      if (depend.getActive().equals(active)) {
        apps.add(depend);
      }
    }

    return sortApps(apps);
  }

  protected List<String> getNames(List<App> apps) {

    List<String> names = new ArrayList<String>();

    for (App app : apps) {
      names.add(app.getName());
    }

    return names;
  }

  protected List<App> getChildren(App app, Boolean active) {

    String code = app.getCode();

    String query = "self.dependsOnSet.code = ?1";

    if (active != null) {
      query = "(" + query + ") AND self.active = " + active;
    }
    List<App> apps = appRepo.all().filter(query, code).fetch();

    log.debug("Parent app: {}, Total children: {}", app.getName(), apps.size());

    return apps;
  }

  @Override
  public App installApp(App app, String language) throws AxelorException {

    app = appRepo.find(app.getId());

    if (app.getActive()) {
      return app;
    }

    if (language != null) {
      app.setLanguageSelect(language);
    } else {
      language = app.getLanguageSelect();
    }

    List<App> apps = getDepends(app, false);

    for (App parentApp : apps) {
      installApp(parentApp, language);
    }

    log.debug("Init data loaded: {}, for app: {}", app.getInitDataLoaded(), app.getCode());
    if (!app.getInitDataLoaded()) {
      app = importDataInit(app);
    }

    app = appRepo.find(app.getId());

    app.setActive(true);

    return saveApp(app);
  }

  protected List<App> sortApps(Collection<App> apps) {

    List<App> appsList = new ArrayList<App>();

    appsList.addAll(apps);

    appsList.sort(
        new Comparator<App>() {

          @Override
          public int compare(App app1, App app2) {

            Integer order1 = app1.getInstallOrder();
            Integer order2 = app2.getInstallOrder();

            if (order1 < order2) {
              return -1;
            }
            if (order1 > order2) {
              return 1;
            }

            return 0;
          }
        });

    log.debug("Apps sorted: {}", getNames(appsList));

    return appsList;
  }

  @Override
  public void refreshApp() throws IOException {

    File dataDir = Files.createTempDir();
    File imgDir = new File(dataDir, "img");
    imgDir.mkdir();

    CSVConfig csvConfig = new CSVConfig();
    csvConfig.setInputs(new ArrayList<CSVInput>());

    List<MetaModel> metaModels =
        metaModelRepo
            .all()
            .filter(
                "self.name != 'App' and self.name like 'App%' and self.packageName =  ?1",
                App.class.getPackage().getName())
            .fetch();

    log.debug("Total app models: {}", metaModels.size());
    for (MetaModel metaModel : metaModels) {
      Class<?> klass;
      try {
        klass = Class.forName(metaModel.getFullName());
      } catch (ClassNotFoundException e) {
        continue;
      }
      if (!App.class.isAssignableFrom(klass)) {
        log.debug("Not a App class : {}", metaModel.getName());
        continue;
      }
      Object obj = null;
      Query query = JPA.em().createQuery("SELECT id FROM " + metaModel.getName());
      try {
        obj = query.setMaxResults(1).getSingleResult();
      } catch (Exception ex) {
      }
      if (obj != null) {
        continue;
      }
      log.debug("App without app record: {}", metaModel.getName());
      String csvName = "base_" + inflector.camelize(klass.getSimpleName(), true) + ".csv";
      String pngName = inflector.dasherize(klass.getSimpleName()) + ".png";
      CSVInput input = new CSVInput();
      input.setFileName(csvName);
      input.setTypeName(klass.getName());
      input.setCallable("com.axelor.csv.script.ImportApp:importApp");
      input.setSearch("self.code =:code");
      input.setSeparator(';');
      csvConfig.getInputs().add(input);
      InputStream stream = klass.getResourceAsStream("/data-init/input/" + csvName);
      copyStream(stream, new File(dataDir, csvName));
      stream = klass.getResourceAsStream("/data-init/input/img/" + pngName);
      copyStream(stream, new File(imgDir, pngName));
    }

    if (!csvConfig.getInputs().isEmpty()) {
      CSVImporter importer = new CSVImporter(csvConfig, dataDir.getAbsolutePath());
      if (importer != null) {
        importer.run();
      }
    }
  }

  protected void copyStream(InputStream stream, File file) throws IOException {

    if (stream != null) {
      FileOutputStream out = new FileOutputStream(file);
      try {
        ByteStreams.copy(stream, out);
        out.close();
      } finally {
        out.close();
      }
    }
  }

  @Override
  public App unInstallApp(App app) throws AxelorException {

    List<App> children = getChildren(app, true);
    if (!children.isEmpty()) {
      List<String> childrenNames = getNames(children);
      throw new AxelorException(
          TraceBackRepository.CATEGORY_INCONSISTENCY, IExceptionMessages.APP_IN_USE, childrenNames);
    }

    app.setActive(false);

    return saveApp(app);
  }

  @Override
  public void bulkInstall(Collection<App> apps, Boolean importDemo, String language)
      throws AxelorException {

    apps = sortApps(apps);

    for (App app : apps) {
      app = installApp(app, language);
      if (importDemo != null && importDemo) {
        importDataDemo(app);
      }
    }
  }

  @Override
  public App importRoles(App app) throws AxelorException {

    if (app.getIsRolesImported()) {
      return app;
    }

    importParentRoles(app);

    importData(app, DIR_ROLES, false);

    app = appRepo.find(app.getId());

    app.setIsRolesImported(true);

    return saveApp(app);
  }

  protected void importParentRoles(App app) throws AxelorException {

    List<App> depends = getDepends(app, true);

    for (App parent : depends) {
      parent = appRepo.find(parent.getId());
      if (!parent.getIsRolesImported()) {
        importRoles(parent);
      }
    }
  }

  @Override
  public void importRoles() throws AxelorException {

    List<App> apps = appRepo.all().filter("self.isRolesImported = false").fetch();
    apps = sortApps(apps);

    for (App app : apps) {
      importRoles(app);
    }
  }
}

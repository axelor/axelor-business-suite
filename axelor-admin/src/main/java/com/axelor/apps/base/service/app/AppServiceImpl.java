/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2016 Axelor (<http://axelor.com>).
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.App;
import com.axelor.apps.base.db.repo.AppRepository;
import com.axelor.common.FileUtils;
import com.axelor.common.Inflector;
import com.axelor.data.Importer;
import com.axelor.data.csv.CSVConfig;
import com.axelor.data.csv.CSVImporter;
import com.axelor.data.csv.CSVInput;
import com.axelor.data.xml.XMLImporter;
import com.axelor.db.JPA;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.schema.views.Selection.Option;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;

@Singleton
public class AppServiceImpl implements AppService {

	private final Logger log = LoggerFactory.getLogger(AppService.class);

	private static final String DIR_DEMO = "demo";

	private static final String DIR_INIT = "data-init" + File.separator + "app";

	private static final String APP_TYPE_SELECT = "app.type.select";
	
	private static final String CONFIG_PATTERN = "-config.xml";
	
	private static final String IMG_DIR = "img";
	
	private static final String EXT_DIR = "extra";

	private static Pattern patCsv = Pattern.compile("^\\<\\s*csv-inputs");

	private static Pattern patXml = Pattern.compile("^\\<\\s*xml-inputs");
	
	private Inflector inflector = Inflector.getInstance();
	
	@Inject
	private AppRepository appRepo;
	
	@Inject
	private MetaModelRepository metaModelRepo;

	@Override
	public String importDataDemo(App app) {
		app = appRepo.find(app.getId());

		importParentData(app);
		
		String lang = getLanguage(app);
		if (lang == null) {
			return I18n.get("No application language set. Please set 'application.locale' property.");
		}
		
		importData(app, DIR_DEMO);
		
		app = appRepo.find(app.getId());
		app.setDemoDataLoaded(true);
		
		saveApp(app);
		
		return I18n.get("Demo data loaded successfully");
	}
	
	private void importData(App app, String dataDir) {
		
		String modules = app.getModules();
		String type = app.getTypeSelect();
		String lang = getLanguage(app);
		
		log.debug("Data import: App type: {}, App lang: {}", type, lang);
		
		for (String module : modules.split(",")) {
			log.debug("Importing module: {}", module);
			File tmp = extract(module, dataDir, lang, type);
			if (tmp == null) {
				continue;
			}
			try {
				File config = FileUtils.getFile(tmp, dataDir, type + CONFIG_PATTERN);
				File data = FileUtils.getFile(tmp, dataDir);
				if (config != null && config.exists()) {
					runImport(config, data);
				}
				else {
					log.debug("Config file not found");
				}
			} finally {
				clean(tmp);
			}
		}
		
	}

	private String getLanguage(App app) {
		
		String lang = AppSettings.get().get("application.locale");
		
		if (app.getLanguageSelect() != null) {
			lang = app.getLanguageSelect();
		}
		
		return lang;
	}
	
	private void importParentData(App app) {
		
		List<App> depends = getDepends(app, true);
		for (App parent : depends) {
			parent = appRepo.find(parent.getId());
			if (!parent.getDemoDataLoaded()) {
				log.debug("Importing demo data for parent app: {}", parent.getName());
				importDataDemo(parent);
			}
		}

	}

	@Transactional
	public App saveApp(App app) {
		return appRepo.save(app);
	}

	private void importDataInit(App app) {

		String lang = getLanguage(app);
		if (lang == null) {
			return;
		}
		
		importData(app, DIR_INIT);
		
		app = appRepo.find(app.getId());
		app.setInitDataLoaded(true);

		saveApp(app);

	}

	private void runImport(File config, File data) {

		log.debug("Running import with config path: {}, data path: {}", config.getAbsolutePath(), data.getAbsolutePath());

		try {
			Scanner scanner = new Scanner(config);
			Importer importer = null;
			while(scanner.hasNextLine()){
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
	
	private File extract(String module, String dirName, String lang, String type) {
		
		String dirPath = dirName + File.separator;
		List<URL> files = new ArrayList<URL>();
		files.addAll(MetaScanner.findAll(module, dirName, type + CONFIG_PATTERN));
		if (files.isEmpty()) {
			return null;
		}
		files.addAll(fetchUrls(module, dirPath + lang));
		if (files.isEmpty()) {
			return null;
		}
		files.addAll(fetchUrls(module, dirPath + IMG_DIR));
		files.addAll(fetchUrls(module, dirPath + EXT_DIR));

		final File tmp = Files.createTempDir();

		for (URL file : files) {
			String name = file.toString();
			name = name.substring(name.lastIndexOf(dirName));
			name = name.replace(dirName + File.separator + lang, dirName);
			try {
				copy(file.openStream(), tmp, name);
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
		}

		return tmp;
	}
	
	
	private List<URL> fetchUrls(String module, String fileName) {
		return  MetaScanner.findAll(module, fileName, "(.+?)");
	}
	

	private void copy(InputStream in, File toDir, String name) throws IOException {
		File dst = FileUtils.getFile(toDir, name);
		Files.createParentDirs(dst);
		FileOutputStream out = new FileOutputStream(dst);
		try {
			ByteStreams.copy(in, out);
		} finally {
			out.close();
		}
	}

	private void clean(File file) {
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
	public App getApp(String type) {
		if (type == null) {
			return null;
		}
		return Beans.get(AppRepository.class).all().filter("self.typeSelect = ?1", type).cacheable().fetchOne();
	}

	@Override
	public boolean isApp(String type) {
		App app = getApp(type);
		if (app == null) {
			return false;
		}

		return app.getActive();
	}

	@Override
	public List<App> getDepends(App app, Boolean active) {

		String dependsOn = app.getDependsOn();
		if (dependsOn == null) {
			return new ArrayList<App>();
		}

		String query = "self.typeSelect in (?1)";

		if (active != null) {
			query += " AND self.active = " + active;	
		}

		List<App> apps = appRepo.all().filter(query, Arrays.asList(dependsOn.split(","))).fetch();
		log.debug("App: {}, DependsOn: {}, Parent active: {}, Total parent founds: {}", app.getName(), dependsOn, active, apps.size());
		return sortApps(apps);
	}

	@Override
	public List<String> getNames(List<App> apps) {

		List<String> names = new ArrayList<String>();

		for (App app : apps) {
			names.add(app.getName());
		}

		return names;
	}

	@Override
	public List<App> getChildren(App app, Boolean active) {

		String type = app.getTypeSelect();

		String query = "self.dependsOn = ?1 "
				+ "OR self.dependsOn like ?2 "
				+ "OR self.dependsOn like ?3 "
				+ "OR self.dependsOn like ?4 ";

		if (active != null) {
			query = "(" + query + ") AND self.active = " + active;
		}
		List<App> apps = appRepo.all().filter(query, type, type + ",%", "%," + type + ",%",  "%," + type).fetch();

		log.debug("Parent app: {}, Total children: {}", app.getName(), apps.size());

		return apps;
	}

	@Override
	public App installApp(App app, Boolean importDemo) {
		List<App> apps = getDepends(app, false);

		for (App parentApp : apps) {
			parentApp = appRepo.find(parentApp.getId());
			installApp(parentApp, importDemo);
		}

		app = appRepo.find(app.getId());
		
		log.debug("Init data loaded: {}, for app: {}", app.getInitDataLoaded(), app.getTypeSelect());
		if (!app.getInitDataLoaded()) {
			importDataInit(app);
		}

		app = appRepo.find(app.getId());
		if (importDemo != null && importDemo && !app.getDemoDataLoaded()) {
			importDataDemo(app);
		}

		app = appRepo.find(app.getId());

		app.setActive(true);

		return saveApp(app);
	}

	@Override
	public List<App> sortApps(Collection<App> apps) {

		List<App> appsList = new ArrayList<App>();

		appsList.addAll(apps);

		appsList.sort(new Comparator<App>() {

			@Override
			public int compare(App app1, App app2) {

				Option option1 = MetaStore.getSelectionItem(APP_TYPE_SELECT, app1.getTypeSelect());
				Option option2 = MetaStore.getSelectionItem(APP_TYPE_SELECT, app2.getTypeSelect());

				if (option1 == null || option2 == null) {
					return 0;
				}

				Integer order1 = option1.getOrder();
				Integer order2 = option2.getOrder();

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
	public void refreshApp() throws IOException, ClassNotFoundException {

		File dataDir = Files.createTempDir();
		File imgDir = new File(dataDir, "img");
		imgDir.mkdir();
		
		CSVConfig csvConfig = new CSVConfig();
		csvConfig.setInputs(new ArrayList<CSVInput>());
		
		List<MetaModel> metaModels = metaModelRepo.all()
				.filter("self.name != 'App' and self.name like 'App%' and self.packageName =  ?1", App.class.getPackage().getName())
				.fetch();
		
		log.debug("Total app models: {}", metaModels.size());
		for (MetaModel metaModel : metaModels) {
			Class<?> klass = Class.forName(metaModel.getFullName());
			if (!App.class.isAssignableFrom(klass)){
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
			String csvName  = "base_" + inflector.camelize(klass.getSimpleName(), true) + ".csv";
			String pngName  = inflector.dasherize(klass.getSimpleName()) + ".png";
			CSVInput input = new CSVInput();
			input.setFileName(csvName);
			input.setTypeName(klass.getName());
			input.setCallable("com.axelor.csv.script.ImportApp:importApp");
			input.setSearch("self.typeSelect =:typeSelect");
			input.setSeparator(';');
			csvConfig.getInputs().add(input);
			InputStream stream = klass.getResourceAsStream("/data-init/input/" +  csvName);
			copyStream(stream, new File(dataDir,csvName));
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

	private void copyStream(InputStream stream, File file) throws IOException {
		
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
	@Transactional
	public App updateLanguage(App app, String language) {
		
		if (language != null) {
			app.setLanguageSelect(language);
			app = appRepo.save(app);
		}
		
		return app;
	}

}

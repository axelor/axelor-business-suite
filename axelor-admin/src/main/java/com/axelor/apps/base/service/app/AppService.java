package com.axelor.apps.base.service.app;

import java.util.Collection;
import java.util.List;

import com.axelor.apps.base.db.App;

public interface AppService {
	
	public String importDataDemo(App app);
	
	public App getApp(String type);
	
	public boolean isApp(String type);
	
	public List<App> getDepends(App app, Boolean active);
	
	public List<String> getNames(List<App> apps);
	
	public List<App> getChildren(App app, Boolean active);
	
	public App installApp(App app, Boolean importDemo);
	
	public List<App> sortApps(Collection<App> apps);
}

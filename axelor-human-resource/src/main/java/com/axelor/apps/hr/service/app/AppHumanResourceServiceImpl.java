package com.axelor.apps.hr.service.app;

import java.util.HashMap;
import java.util.Map;

import com.axelor.apps.base.db.AppExpense;
import com.axelor.apps.base.db.AppLeave;
import com.axelor.apps.base.db.AppTimesheet;
import com.axelor.apps.base.db.repo.AppExpenseRepository;
import com.axelor.apps.base.db.repo.AppLeaveRepository;
import com.axelor.apps.base.db.repo.AppTimesheetRepository;
import com.axelor.apps.base.service.app.AppBaseServiceImpl;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class AppHumanResourceServiceImpl extends AppBaseServiceImpl implements AppHumanResourceService {

	private AppTimesheetRepository appTimesheetRepo;
	private AppLeaveRepository appLeaveRepo;
	private AppExpenseRepository appExpenseRepo;

	@Inject
	public AppHumanResourceServiceImpl(AppTimesheetRepository appTimesheetRepo, AppLeaveRepository appLeaveRepo,
			AppExpenseRepository appExpense) {
		this.appTimesheetRepo = appTimesheetRepo;
		this.appLeaveRepo = appLeaveRepo;
		this.appExpenseRepo = appExpense;
	}

	@Override
	public AppTimesheet getAppTimesheet() {
		return appTimesheetRepo.all().fetchOne();
	}

	@Override
	public AppLeave getAppLeave() {
		return appLeaveRepo.all().fetchOne();
	}

	@Override
	public AppExpense getAppExpense() {
		return appExpenseRepo.all().fetchOne();
	}

	@Override
	public void getHrmAppSettings(ActionRequest request, ActionResponse response) {

		try {

			Map<String, Object> map = new HashMap<>();

			map.put("hasInvoicingAppEnable", isApp("invoice"));
			map.put("hasLeaveAppEnable", isApp("leave"));
			map.put("hasExpenseAppEnable", isApp("expense"));
			map.put("hasTimesheetAppEnable", isApp("timesheet"));
			map.put("hasProjectAppEnable", isApp("project"));

			response.setData(map);
			response.setTotal(map.size());

		} catch (Exception e) {
			e.printStackTrace();
			response.setException(e);
		}
	}

}

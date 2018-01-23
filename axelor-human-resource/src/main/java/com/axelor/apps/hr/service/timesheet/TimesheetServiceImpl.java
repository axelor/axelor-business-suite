/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.hr.service.timesheet;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.DayPlanning;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.EventsPlanningLine;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.LeaveRequestRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.employee.EmployeeService;
import com.axelor.apps.hr.service.project.ProjectService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimesheetServiceImpl implements TimesheetService{

	@Inject
	protected EmployeeService employeeService;

	@Inject
	protected PriceListService priceListService;

	@Inject
	protected AppHumanResourceService appHumanResourceService;
	
	@Inject
	protected ProjectService projectService;

	@Inject
	protected EmployeeRepository employeeRepo;
	
	@Inject
	protected TimesheetRepository timesheetRepository;
	
	@Inject
	protected HRConfigService  hrConfigService;
	
	@Inject
	protected TemplateMessageService  templateMessageService;

	@Inject
	private ProjectRepository projectRepo;

	@Inject
	private UserRepository userRepo;

	@Inject
	private UserHrService userHrService;


	@Override
	@Transactional(rollbackOn={Exception.class})
	public void getTimeFromTask(Timesheet timesheet){

		List<TimesheetLine> timesheetLineList = TimesheetLineRepository.of(TimesheetLine.class).all().filter("self.user = ?1 AND self.timesheet = null AND self.project != null", timesheet.getUser()).fetch();
		for (TimesheetLine timesheetLine : timesheetLineList) {
			timesheet.addTimesheetLineListItem(timesheetLine);
		}
		Beans.get(TimesheetRepository.class).save(timesheet);
	}

	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void confirm(Timesheet timesheet) throws AxelorException  {
				
		this.validToDate(timesheet);
		
		timesheet.setStatusSelect(TimesheetRepository.STATUS_CONFIRMED);
		timesheet.setSentDate(appHumanResourceService.getTodayDate());
		
		if(timesheet.getToDate() == null)  {
			List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
			LocalDate timesheetLineLastDate = timesheetLineList.get(0).getDate();
			for (TimesheetLine timesheetLine : timesheetLineList.subList(1, timesheetLineList.size())) {
				if (timesheetLine.getDate().compareTo(timesheetLineLastDate) > 0) {
					timesheetLineLastDate = timesheetLine.getDate();
				}
			}

			timesheet.setToDate(timesheetLineLastDate);
		}
		
		timesheetRepository.save(timesheet);
		
	}
	
	
	public Message sendConfirmationEmail(Timesheet timesheet) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException  {
		
		HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());
		
		if(hrConfig.getTimesheetMailNotification())  {
				
			return templateMessageService.generateAndSendMessage(timesheet, hrConfigService.getSentTimesheetTemplate(hrConfig));
				
		}
		
		return null;
		
	}
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void validate(Timesheet timesheet) throws AxelorException  {
		
		timesheet.setStatusSelect(TimesheetRepository.STATUS_VALIDATED);
		timesheet.setValidatedBy(AuthUtils.getUser());
		timesheet.setValidationDate(appHumanResourceService.getTodayDate());
		
		timesheetRepository.save(timesheet);
		
	}
	
	
	public Message sendValidationEmail(Timesheet timesheet) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException  {
		
		HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());
		
		if(hrConfig.getTimesheetMailNotification())  {
				
			return templateMessageService.generateAndSendMessage(timesheet, hrConfigService.getValidatedTimesheetTemplate(hrConfig));
				
		}
		
		return null;
		
	}
	
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void refuse(Timesheet timesheet) throws AxelorException  {
		
		timesheet.setStatusSelect(TimesheetRepository.STATUS_REFUSED);
		timesheet.setRefusedBy(AuthUtils.getUser());
		timesheet.setRefusalDate(appHumanResourceService.getTodayDate());
		
		timesheetRepository.save(timesheet);
		
	}
	
	public Message sendRefusalEmail(Timesheet timesheet) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException  {
		
		HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());
		
		if(hrConfig.getTimesheetMailNotification())  {
				
			return templateMessageService.generateAndSendMessage(timesheet, hrConfigService.getRefusedTimesheetTemplate(hrConfig));
				
		}
		
		return null;
		
	}
	
	@Transactional(rollbackOn={Exception.class})
	public void cancel(Timesheet timesheet){
		timesheet.setStatusSelect(TimesheetRepository.STATUS_CANCELED);
		Beans.get(TimesheetRepository.class).save(timesheet);
	}
	
	public Message sendCancellationEmail(Timesheet timesheet) throws AxelorException, ClassNotFoundException, InstantiationException, IllegalAccessException, MessagingException, IOException  {

		HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());

		if(hrConfig.getTimesheetMailNotification())  {

			return templateMessageService.generateAndSendMessage(timesheet, hrConfigService.getCanceledTimesheetTemplate(hrConfig));

		}

		return null;

	}

	@Override
	public Timesheet generateLines(Timesheet timesheet, LocalDate fromGenerationDate, LocalDate toGenerationDate, BigDecimal logTime, Project project, Product product) throws AxelorException{

		User user = timesheet.getUser();
		Employee employee = user.getEmployee();
		
		if (fromGenerationDate == null) {
			throw new AxelorException(timesheet, IException.MISSING_FIELD, I18n.get(IExceptionMessage.TIMESHEET_FROM_DATE));
		}
		if (toGenerationDate == null) {
			throw new AxelorException(timesheet, IException.MISSING_FIELD, I18n.get(IExceptionMessage.TIMESHEET_TO_DATE));
		}
		if (product == null) {
			throw new AxelorException(timesheet, IException.MISSING_FIELD, I18n.get(IExceptionMessage.TIMESHEET_PRODUCT));
		}
		if (employee == null) {
			throw new AxelorException(timesheet, IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),user.getName());
		}
		WeeklyPlanning planning = user.getEmployee().getPlanning();
		if (planning == null) {
			throw new AxelorException(timesheet, IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_DAY_PLANNING),user.getName());
		}
		List<DayPlanning> dayPlanningList = planning.getWeekDays();

		LocalDate fromDate = fromGenerationDate;
		LocalDate toDate = toGenerationDate;
		Map<Integer,String> correspMap = new HashMap<Integer,String>();
		correspMap.put(1, "monday");
		correspMap.put(2, "tuesday");
		correspMap.put(3, "wednesday");
		correspMap.put(4, "thursday");
		correspMap.put(5, "friday");
		correspMap.put(6, "saturday");
		correspMap.put(7, "sunday");
		
		//Leaving list
		List<LeaveRequest> leaveList = LeaveRequestRepository.of(LeaveRequest.class).all().filter("self.user = ?1 AND (self.statusSelect = 2 OR self.statusSelect = 3)", user).fetch();
		
		//Public holidays list
		List<EventsPlanningLine> publicHolidayList = employee.getPublicHolidayEventsPlanning().getEventsPlanningLineList();
		 
		while(!fromDate.isAfter(toDate)){
			DayPlanning dayPlanningCurr = new DayPlanning();
			for (DayPlanning dayPlanning : dayPlanningList) {
				if(dayPlanning.getName().equals(correspMap.get(fromDate.getDayOfWeek().getValue()))){
					dayPlanningCurr = dayPlanning;
					break;
				}
			}
			if(dayPlanningCurr.getMorningFrom() != null || dayPlanningCurr.getMorningTo() != null || dayPlanningCurr.getAfternoonFrom() != null || dayPlanningCurr.getAfternoonTo() != null)
			{
				/*Check if the day is not a leaving day */
				boolean noLeave = true;
				if(leaveList != null){
					for (LeaveRequest leave : leaveList) {
						if((leave.getFromDate().isBefore(fromDate) && leave.getToDate().isAfter(fromDate))
							|| leave.getFromDate().isEqual(fromDate) || leave.getToDate().isEqual(fromDate))
						{
							noLeave = false;
							break;
						}
					}
				}
				
				/*Check if the day is not a public holiday */
				boolean noPublicHoliday = true;
				if(publicHolidayList != null){
					for (EventsPlanningLine publicHoliday : publicHolidayList) {
						if(publicHoliday.getDate().isEqual(fromDate))
						{
							noPublicHoliday = false;
							break;
						}
					}
				}
				
				if(noLeave && noPublicHoliday){
					TimesheetLine timesheetLine = createTimesheetLine(project, product, user, fromDate, timesheet, employeeService.getUserDuration(logTime, user, true), "");
					timesheetLine.setVisibleDuration(logTime);
				}
				
			}
			fromDate=fromDate.plusDays(1);
		}
		return timesheet;
	}

	@Override
	public LocalDate getFromPeriodDate(){
		Timesheet timesheet = Beans.get(TimesheetRepository.class).all().filter("self.user = ?1 ORDER BY self.toDate DESC", AuthUtils.getUser()).fetchOne();
		if(timesheet != null){
			return timesheet.getToDate();
		}
		else{
			return null;
		}
	}
	
	public Timesheet getCurrentTimesheet(){
		Timesheet timesheet = Beans.get(TimesheetRepository.class).all().filter("self.statusSelect = ?1 AND self.user.id = ?2", TimesheetRepository.STATUS_DRAFT, AuthUtils.getUser().getId()).order("-id").fetchOne();
		if(timesheet != null){
			return timesheet;
		}
		else{
			return null;
		}
	}
	
	public Timesheet getCurrentOrCreateTimesheet(){
		Timesheet timesheet = getCurrentTimesheet();
		if(timesheet == null)
			timesheet = createTimesheet(AuthUtils.getUser(), appHumanResourceService.getTodayDateTime().toLocalDate(), null);
		return timesheet;
	}
	
	public Timesheet createTimesheet(User user, LocalDate fromDate, LocalDate toDate){
		Timesheet timesheet = new Timesheet();
		
		timesheet.setUser(user);
		Company company = null;
		if (user.getEmployee() != null
				&& user.getEmployee().getMainEmploymentContract() != null) {
			company = user.getEmployee().getMainEmploymentContract().getPayCompany();
		}
		timesheet.setCompany(company);
		timesheet.setFromDate(fromDate);
		timesheet.setStatusSelect(TimesheetRepository.STATUS_DRAFT);
		timesheet.setFullName(computeFullName(timesheet));
		
		return timesheet;
	}
	
	public TimesheetLine createTimesheetLine(Project project, Product product, User user, LocalDate date, Timesheet timesheet, BigDecimal hours, String comments){
		TimesheetLine timesheetLine = new TimesheetLine();
		
		timesheetLine.setDate(date);
		timesheetLine.setComments(comments);
		timesheetLine.setProduct(product);
		timesheetLine.setProject(project);
		timesheetLine.setUser(user);
		timesheetLine.setDurationStored(hours);
		timesheet.addTimesheetLineListItem(timesheetLine);
		
		return timesheetLine;
	}

	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<TimesheetLine> timesheetLineList, int priority) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();
		int count = 0;
		DateFormat ddmmFormat = new SimpleDateFormat("dd/MM");
		HashMap<String, Object[]> timeSheetInformationsMap = new HashMap<String, Object[]>();
		//Check if a consolidation by product and user must be done
		boolean consolidate = appHumanResourceService.getAppTimesheet().getConsolidateTSLine();

		for (TimesheetLine timesheetLine : timesheetLineList) {
			Object[] tabInformations = new Object[5];
			tabInformations[0] = timesheetLine.getProduct();
			tabInformations[1] = timesheetLine.getUser();
			//Start date
			tabInformations[2] = timesheetLine.getDate();
			//End date, useful only for consolidation
			tabInformations[3] = timesheetLine.getDate();
			tabInformations[4] = timesheetLine.getDurationStored();

			String key = null;
			if(consolidate){
				key = timesheetLine.getProduct().getId() + "|" + timesheetLine.getUser().getId();
				if (timeSheetInformationsMap.containsKey(key)){
					tabInformations = timeSheetInformationsMap.get(key);
					//Update date
					if (timesheetLine.getDate().compareTo((LocalDate)tabInformations[2]) < 0){
						//If date is lower than start date then replace start date by this one
						tabInformations[2] = timesheetLine.getDate();
					}else if (timesheetLine.getDate().compareTo((LocalDate)tabInformations[3]) > 0){
						//If date is upper than end date then replace end date by this one
						tabInformations[3] = timesheetLine.getDate();
					}
					tabInformations[4] = ((BigDecimal)tabInformations[4]).add(timesheetLine.getDurationStored());
				}else{
					timeSheetInformationsMap.put(key, tabInformations);
				}
			}else{
				key = String.valueOf(timesheetLine.getId());
				timeSheetInformationsMap.put(key, tabInformations);
			}

			timesheetLine.setInvoiced(true);

		}

		for(Object[] timesheetInformations : timeSheetInformationsMap.values())  {

			String strDate = null;
			Product product = (Product)timesheetInformations[0];
			User user = (User)timesheetInformations[1];
			LocalDate startDate = (LocalDate)timesheetInformations[2];
			LocalDate endDate = (LocalDate)timesheetInformations[3];
			BigDecimal durationStored = (BigDecimal) timesheetInformations[4];

			if (consolidate){
				strDate = ddmmFormat.format(startDate) + " - " + ddmmFormat.format(endDate);
			}else{
				strDate = ddmmFormat.format(startDate);
			}

			invoiceLineList.addAll(this.createInvoiceLine(invoice, product, user, strDate, durationStored, priority*100+count));
			count++;
		}

		return invoiceLineList;

	}

	public List<InvoiceLine> createInvoiceLine(Invoice invoice, Product product, User user, String date, BigDecimal durationStored, int priority) throws AxelorException  {

		int discountTypeSelect = 1;
		if(product == null){
			throw new AxelorException(IException.CONFIGURATION_ERROR, I18n.get(IExceptionMessage.TIMESHEET_PRODUCT));
		}
		BigDecimal price = product.getSalePrice();
		BigDecimal discountAmount = product.getCostPrice();


		BigDecimal qtyConverted = durationStored;
		qtyConverted = Beans.get(UnitConversionService.class).convert(appHumanResourceService.getAppBase().getUnitHours(), product.getUnit(), durationStored);

		PriceList priceList = Beans.get(PartnerPriceListService.class).getDefaultPriceList(invoice.getPartner(), PriceListRepository.TYPE_SALE);
		if(priceList != null)  {
			PriceListLine priceListLine = priceListService.getPriceListLine(product, qtyConverted, priceList);
			if(priceListLine!=null){
				discountTypeSelect = priceListLine.getTypeSelect();
			}
			if((appHumanResourceService.getAppBase().getComputeMethodDiscountSelect() == AppBaseRepository.INCLUDE_DISCOUNT_REPLACE_ONLY && discountTypeSelect == IPriceListLine.TYPE_REPLACE) || appHumanResourceService.getAppBase().getComputeMethodDiscountSelect() == AppBaseRepository.INCLUDE_DISCOUNT)
			{
				Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
				if(discounts != null){
					discountAmount = (BigDecimal) discounts.get("discountAmount");
					price = priceListService.computeDiscount(price, (int) discounts.get("discountTypeSelect"), discountAmount);
				}
			}
			else{
				Map<String, Object> discounts = priceListService.getDiscounts(priceList, priceListLine, price);
				if(discounts != null){
					discountAmount = (BigDecimal) discounts.get("discountAmount");
					if(discounts.get("price") != null)  {
						price = (BigDecimal) discounts.get("price");
					}
				}
			}

		}

		String 	description = user.getFullName(),
				productName = product.getName() + " " + "(" + date + ")";

		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, productName, price,
					price, description, qtyConverted, product.getUnit(), null, priority, discountAmount, discountTypeSelect,
					price.multiply(qtyConverted), null, false, false)  {

			@Override
			public List<InvoiceLine> creates() throws AxelorException {

				InvoiceLine invoiceLine = this.createInvoiceLine();

				List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
				invoiceLines.add(invoiceLine);

				return invoiceLines;
			}
		};

		return invoiceLineGenerator.creates();
	}

	@Override
	@Transactional
	public void computeTimeSpent(Timesheet timesheet){
		List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
		for (TimesheetLine timesheetLine : timesheetLineList) {
			Project project = timesheetLine.getProject();
			if(project != null){
				project.setTimeSpent(timesheetLine.getDurationStored().add(this.computeSubTimeSpent(project)));
				this.computeParentTimeSpent(project);
				Beans.get(ProjectRepository.class).save(project);
			}
		}
	}

	public BigDecimal computeSubTimeSpent(Project project){
		BigDecimal sum = BigDecimal.ZERO;
		List<Project> subProjectList = Beans.get(ProjectRepository.class).all().filter("self.parentProject = ?1", project).fetch();
		if(subProjectList == null || subProjectList.isEmpty()){
			return project.getTimeSpent();
		}
		for (Project projectIt : subProjectList) {
			sum = sum.add(this.computeSubTimeSpent(projectIt));
		}
		return sum;
	}

	public void computeParentTimeSpent(Project project){
		Project parentProject = project.getParentProject();
		if(parentProject == null){
			return;
		}
		parentProject.setTimeSpent(project.getTimeSpent().add(this.computeTimeSpent(parentProject)));
		this.computeParentTimeSpent(parentProject);
	}

	public BigDecimal computeTimeSpent(Project project){
		BigDecimal sum = BigDecimal.ZERO;
		List<TimesheetLine> timesheetLineList = Beans.get(TimesheetLineRepository.class).all().filter("self.project = ?1 AND self.timesheet.statusSelect = ?2", project, TimesheetRepository.STATUS_VALIDATED).fetch();
		for (TimesheetLine timesheetLine : timesheetLineList) {
			sum = sum.add(timesheetLine.getDurationStored());
		}
		return sum;
	}
	
	public void getActivities(ActionRequest request, ActionResponse response){
		List<Map<String,String>> dataList = new ArrayList<Map<String,String>>();
		try{
			List<Product> productList = Beans.get(ProductRepository.class).all().filter("self.isActivity = true").fetch();
			for (Product product : productList) {
				Map<String, String> map = new HashMap<String,String>();
				map.put("name", product.getName());
				map.put("id", product.getId().toString());
				dataList.add(map);
			}
			response.setData(dataList);
			response.setTotal(dataList.size());
		}
		catch(Exception e){
			response.setStatus(-1);
			response.setError(e.getMessage());
		}
	}
	
	@Transactional
	public void insertTSLine(ActionRequest request, ActionResponse response){
		
		User user = AuthUtils.getUser();
		Project project = Beans.get(ProjectRepository.class).find(new Long(request.getData().get("project").toString()));
		Product product = Beans.get(ProductRepository.class).find(new Long(request.getData().get("activity").toString()));
		LocalDate date = LocalDate.parse(request.getData().get("date").toString(), DateTimeFormatter.ISO_DATE);
		if(user != null){
			Timesheet timesheet = Beans.get(TimesheetRepository.class).all().filter("self.statusSelect = 1 AND self.user.id = ?1", user.getId()).order("-id").fetchOne();
			if(timesheet == null){
				timesheet = createTimesheet(user, date, date);
			}
			BigDecimal hours = new BigDecimal(request.getData().get("duration").toString());
			TimesheetLine line = createTimesheetLine(project, product, user, date, timesheet, hours, request.getData().get("comments").toString());
			
			Beans.get(TimesheetRepository.class).save(timesheet);
			response.setTotal(1);
			HashMap<String, Object> data = new HashMap<String, Object>();
			data.put("id", line.getId());
			response.setData(data);
		}
	}
	
	public String computeFullName(Timesheet timesheet){
		
		User timesheetUser = timesheet.getUser();
		LocalDateTime createdOn = timesheet.getCreatedOn();
		
  		if(timesheetUser != null && createdOn != null){
  			return timesheetUser.getFullName() + " " + createdOn.getDayOfMonth() + "/" + createdOn.getMonthValue()
  				+ "/" + timesheet.getCreatedOn().getYear() + " " + createdOn.getHour() + ":" + createdOn.getMinute();
  		}
  		else if (timesheetUser != null){
  			return timesheetUser.getFullName()+" N°"+timesheet.getId();
  		}
  		else{
  			return "N°"+timesheet.getId();
  		}
	}
	
	public List<TimesheetLine> computeVisibleDuration(Timesheet timesheet)  {
		
		List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
		
		for(TimesheetLine timesheetLine : timesheetLineList)  {
			
			timesheetLine.setVisibleDuration(employeeService.getUserDuration(timesheetLine.getDurationStored(), timesheet.getUser(), false));
			
		}
		
		timesheetLineList = projectService._sortTimesheetLineByDate(timesheetLineList);
		
		return timesheetLineList;
	}
	
	
	public void validToDate(Timesheet timesheet) throws AxelorException  {
		
		List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
		List<Integer> listId = new ArrayList<Integer>();
		int count = 0;
		
		if (timesheet.getFromDate() == null) {
			throw new AxelorException(timesheet, IException.MISSING_FIELD, I18n.get(IExceptionMessage.TIMESHEET_NULL_FROM_DATE));
		} else if (timesheet.getToDate() != null) {
			if (timesheetLineList != null && !timesheetLineList.isEmpty()) {
				for (TimesheetLine timesheetLine : timesheetLineList) {
					count++;
					if (timesheetLine.getDate().isAfter(timesheet.getToDate())) {
						listId.add(count);
					} else if (timesheetLine.getDate().isBefore(timesheet.getFromDate())) {
						listId.add(count);
					}
				}
			}
		} else {
			if (timesheetLineList != null && !timesheetLineList.isEmpty()) {
				for (TimesheetLine timesheetLine : timesheetLineList) {
					count++;
					if (timesheetLine.getDate().isBefore(timesheet.getFromDate())) {
						listId.add(count);
					}
				}
			}
		}
		if (!listId.isEmpty()) {
			throw new AxelorException(timesheet, IException.FUNCTIONNAL, I18n.get(IExceptionMessage.TIMESHEET_DATE_CONFLICT), Joiner.on(",").join(listId));

		}
	}

	@Override
	public List<Map<String,Object>> createDefaultLines(Timesheet timesheet) {

		List<Map<String,Object>> lines =  new ArrayList<Map<String,Object>>();
		User user = timesheet.getUser();
		if (user == null || timesheet.getFromDate() == null) {
			return lines;
		}

		user = userRepo.find(user.getId());

		Product product = userHrService.getTimesheetProduct(user);

		if (product == null) {
			return lines;
		}

		List<Project> projects = projectRepo.all().filter("self.membersUserSet.id = ?1 and "
				+ "self.imputable = true "
				+ "and self.statusSelect != 3", user.getId()).fetch();

		for (Project project : projects) {
			TimesheetLine line = createTimesheetLine(project, product, user, timesheet.getFromDate(), timesheet, new BigDecimal(0), null);
			lines.add(Mapper.toMap(line));
		}


		return lines;
	}

	@Override
	public BigDecimal computePeriodTotal(Timesheet timesheet) {
		BigDecimal periodTotal = BigDecimal.ZERO;

		List<TimesheetLine> timesheetLines = timesheet.getTimesheetLineList();

		for (TimesheetLine timesheetLine : timesheetLines) {
			periodTotal = periodTotal.add(timesheetLine.getDurationStored());
		}

		return periodTotal;
	}

	@Override
	public String getPeriodTotalConvertTitleByUserPref(User user) {
		String title;
		if (user.getEmployee() != null) {
			if (user.getEmployee().getTimeLoggingPreferenceSelect() != null) {
				title = user.getEmployee().getTimeLoggingPreferenceSelect().equals("days") ? I18n.get("Days") : user.getEmployee().getTimeLoggingPreferenceSelect().equals("minutes") ? I18n.get("Minutes") : I18n.get("Hours");
				return title;
			}
		}
		return null;
	}

	@Override
	public void createValidateDomainTimesheetLine(User user, Employee employee, ActionView.ActionViewBuilder actionView) {

		actionView.domain("self.timesheet.company = :_activeCompany AND  self.timesheet.statusSelect = 2")
				.context("_activeCompany", user.getActiveCompany());

		if(employee == null || !employee.getHrManager())  {
			if (employee == null || employee.getManager() == null) {
				actionView.domain(actionView.get().getDomain() + " AND (self.timesheet.user = :_user OR self.timesheet.user.employee.manager = :_user)")
						.context("_user", user);
			}
			else {
				actionView.domain(actionView.get().getDomain() + " AND self.timesheet.user.employee.manager = :_user")
						.context("_user", user);
			}
		}
	}
}

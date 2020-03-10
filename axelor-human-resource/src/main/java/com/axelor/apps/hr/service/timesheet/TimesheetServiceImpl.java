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
package com.axelor.apps.hr.service.timesheet;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.AppTimesheet;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.DayPlanning;
import com.axelor.apps.base.db.EventsPlanning;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.UnitConversionService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.base.service.publicHoliday.PublicHolidayService;
import com.axelor.apps.base.service.weeklyplanning.WeeklyPlanningService;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HRConfig;
import com.axelor.apps.hr.db.LeaveRequest;
import com.axelor.apps.hr.db.Timesheet;
import com.axelor.apps.hr.db.TimesheetLine;
import com.axelor.apps.hr.db.repo.EmployeeRepository;
import com.axelor.apps.hr.db.repo.LeaveReasonRepository;
import com.axelor.apps.hr.db.repo.TimesheetLineRepository;
import com.axelor.apps.hr.db.repo.TimesheetRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.app.AppHumanResourceService;
import com.axelor.apps.hr.service.config.HRConfigService;
import com.axelor.apps.hr.service.leave.LeaveService;
import com.axelor.apps.hr.service.publicHoliday.PublicHolidayHrService;
import com.axelor.apps.hr.service.user.UserHrService;
import com.axelor.apps.message.db.Message;
import com.axelor.apps.message.service.TemplateMessageService;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.project.db.ProjectPlanningTime;
import com.axelor.apps.project.db.repo.ProjectPlanningTimeRepository;
import com.axelor.apps.project.db.repo.ProjectRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JpaSupport;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.team.db.TeamTask;
import com.axelor.team.db.repo.TeamTaskRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import org.apache.commons.collections4.ListUtils;

/** @author axelor */
public class TimesheetServiceImpl extends JpaSupport implements TimesheetService {

  protected PriceListService priceListService;
  protected AppHumanResourceService appHumanResourceService;
  protected HRConfigService hrConfigService;
  protected TemplateMessageService templateMessageService;
  protected ProjectRepository projectRepo;
  protected UserRepository userRepo;
  protected UserHrService userHrService;
  protected TimesheetLineService timesheetLineService;
  protected ProjectPlanningTimeRepository projectPlanningTimeRepository;
  protected TeamTaskRepository teamTaskRepository;
  protected TimesheetLineRepository timesheetlineRepo;
  protected TimesheetRepository timeSheetRepository;
  private ExecutorService executor = Executors.newCachedThreadPool();
  private static final int ENTITY_FIND_TIMEOUT = 10000;
  private static final int ENTITY_FIND_INTERVAL = 50;

  @Inject
  public TimesheetServiceImpl(
      PriceListService priceListService,
      AppHumanResourceService appHumanResourceService,
      HRConfigService hrConfigService,
      TemplateMessageService templateMessageService,
      ProjectRepository projectRepo,
      UserRepository userRepo,
      UserHrService userHrService,
      TimesheetLineService timesheetLineService,
      ProjectPlanningTimeRepository projectPlanningTimeRepository,
      TeamTaskRepository teamTaskRepository,
      TimesheetLineRepository timesheetlineRepo,
      TimesheetRepository timeSheetRepository) {
    this.priceListService = priceListService;
    this.appHumanResourceService = appHumanResourceService;
    this.hrConfigService = hrConfigService;
    this.templateMessageService = templateMessageService;
    this.projectRepo = projectRepo;
    this.userRepo = userRepo;
    this.userHrService = userHrService;
    this.timesheetLineService = timesheetLineService;
    this.projectPlanningTimeRepository = projectPlanningTimeRepository;
    this.teamTaskRepository = teamTaskRepository;
    this.timesheetlineRepo = timesheetlineRepo;
    this.timeSheetRepository = timeSheetRepository;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public void confirm(Timesheet timesheet) throws AxelorException {
    this.fillToDate(timesheet);
    this.validateDates(timesheet);

    timesheet.setStatusSelect(TimesheetRepository.STATUS_CONFIRMED);
    timesheet.setSentDate(appHumanResourceService.getTodayDate());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message sendConfirmationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());

    if (hrConfig.getTimesheetMailNotification()) {
      return templateMessageService.generateAndSendMessage(
          timesheet, hrConfigService.getSentTimesheetTemplate(hrConfig));
    }

    return null;
  }

  public void checkEmptyPeriod(Timesheet timesheet) throws AxelorException {
    LeaveService leaveService = Beans.get(LeaveService.class);
    PublicHolidayHrService publicHolidayHrService = Beans.get(PublicHolidayHrService.class);

    User user = timesheet.getUser();
    Employee employee = user.getEmployee();
    if (employee == null) {
      return;
    }
    if (employee.getPublicHolidayEventsPlanning() == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_PUBLIC_HOLIDAY_EVENTS_PLANNING),
          user.getName());
    }
    WeeklyPlanning planning = employee.getWeeklyPlanning();
    if (planning == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_DAY_PLANNING),
          user.getName());
    }
    List<DayPlanning> dayPlanningList = planning.getWeekDays();
    Map<Integer, String> correspMap = getCorresMap();

    List<TimesheetLine> timesheetLines = timesheet.getTimesheetLineList();
    timesheetLines.sort(Comparator.comparing(TimesheetLine::getDate));
    for (int i = 0; i < timesheetLines.size(); i++) {

      if (i + 1 < timesheetLines.size()) {
        LocalDate date1 = timesheetLines.get(i).getDate();
        LocalDate date2 = timesheetLines.get(i + 1).getDate();
        LocalDate missingDay = date1.plusDays(1);

        while (ChronoUnit.DAYS.between(date1, date2) > 1) {

          if (isWorkedDay(missingDay, correspMap, dayPlanningList)
              && !leaveService.isLeaveDay(user, missingDay)
              && !publicHolidayHrService.checkPublicHolidayDay(missingDay, employee)) {
            throw new AxelorException(
                TraceBackRepository.CATEGORY_MISSING_FIELD, "Line for %s is missing.", missingDay);
          }

          date1 = missingDay;
          missingDay = missingDay.plusDays(1);
        }
      }
    }
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message confirmAndSendConfirmationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {
    confirm(timesheet);
    return sendConfirmationEmail(timesheet);
  }

  @Override
  @Transactional
  public void validate(Timesheet timesheet) {

    timesheet.setStatusSelect(TimesheetRepository.STATUS_VALIDATED);
    timesheet.setValidatedBy(AuthUtils.getUser());
    timesheet.setValidationDate(appHumanResourceService.getTodayDate());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message sendValidationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());

    if (hrConfig.getTimesheetMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          timesheet, hrConfigService.getValidatedTimesheetTemplate(hrConfig));
    }

    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message validateAndSendValidationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {
    validate(timesheet);
    return sendValidationEmail(timesheet);
  }

  @Override
  @Transactional
  public void refuse(Timesheet timesheet) {

    timesheet.setStatusSelect(TimesheetRepository.STATUS_REFUSED);
    timesheet.setRefusedBy(AuthUtils.getUser());
    timesheet.setRefusalDate(appHumanResourceService.getTodayDate());
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message sendRefusalEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());

    if (hrConfig.getTimesheetMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          timesheet, hrConfigService.getRefusedTimesheetTemplate(hrConfig));
    }

    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message refuseAndSendRefusalEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {
    refuse(timesheet);
    return sendRefusalEmail(timesheet);
  }

  @Override
  @Transactional
  public void cancel(Timesheet timesheet) {
    timesheet.setStatusSelect(TimesheetRepository.STATUS_CANCELED);
  }

  @Override
  @Transactional
  public void draft(Timesheet timesheet) {
    timesheet.setStatusSelect(TimesheetRepository.STATUS_DRAFT);
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message sendCancellationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {

    HRConfig hrConfig = hrConfigService.getHRConfig(timesheet.getCompany());

    if (hrConfig.getTimesheetMailNotification()) {

      return templateMessageService.generateAndSendMessage(
          timesheet, hrConfigService.getCanceledTimesheetTemplate(hrConfig));
    }

    return null;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Message cancelAndSendCancellationEmail(Timesheet timesheet)
      throws AxelorException, ClassNotFoundException, InstantiationException,
          IllegalAccessException, MessagingException, IOException {
    cancel(timesheet);
    return sendCancellationEmail(timesheet);
  }

  @Override
  public Timesheet generateLines(
      Timesheet timesheet,
      LocalDate fromGenerationDate,
      LocalDate toGenerationDate,
      BigDecimal logTime,
      Project project,
      Product product)
      throws AxelorException {

    User user = timesheet.getUser();
    Employee employee = user.getEmployee();

    if (fromGenerationDate == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TIMESHEET_FROM_DATE));
    }
    if (toGenerationDate == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TIMESHEET_TO_DATE));
    }
    if (product == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TIMESHEET_PRODUCT));
    }
    if (employee == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.LEAVE_USER_EMPLOYEE),
          user.getName());
    }
    WeeklyPlanning planning = employee.getWeeklyPlanning();
    if (planning == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_DAY_PLANNING),
          user.getName());
    }
    List<DayPlanning> dayPlanningList = planning.getWeekDays();
    Map<Integer, String> correspMap = getCorresMap();

    LocalDate fromDate = fromGenerationDate;
    LocalDate toDate = toGenerationDate;

    if (employee.getPublicHolidayEventsPlanning() == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.TIMESHEET_EMPLOYEE_PUBLIC_HOLIDAY_EVENTS_PLANNING),
          user.getName());
    }

    LeaveService leaveService = Beans.get(LeaveService.class);
    PublicHolidayHrService publicHolidayHrService = Beans.get(PublicHolidayHrService.class);

    while (!fromDate.isAfter(toDate)) {
      if (isWorkedDay(fromDate, correspMap, dayPlanningList)
          && !leaveService.isLeaveDay(user, fromDate)
          && !publicHolidayHrService.checkPublicHolidayDay(fromDate, employee)) {

        TimesheetLine timesheetLine =
            timesheetLineService.createTimesheetLine(
                project,
                product,
                user,
                fromDate,
                timesheet,
                timesheetLineService.computeHoursDuration(timesheet, logTime, true),
                "");
        timesheetLine.setDuration(logTime);
      }

      fromDate = fromDate.plusDays(1);
    }
    return timesheet;
  }

  protected Map<Integer, String> getCorresMap() {
    Map<Integer, String> correspMap = new HashMap<>();
    correspMap.put(1, "monday");
    correspMap.put(2, "tuesday");
    correspMap.put(3, "wednesday");
    correspMap.put(4, "thursday");
    correspMap.put(5, "friday");
    correspMap.put(6, "saturday");
    correspMap.put(7, "sunday");
    return correspMap;
  }

  protected boolean isWorkedDay(
      LocalDate date, Map<Integer, String> correspMap, List<DayPlanning> dayPlanningList) {
    DayPlanning dayPlanningCurr = new DayPlanning();
    for (DayPlanning dayPlanning : dayPlanningList) {
      if (dayPlanning.getName().equals(correspMap.get(date.getDayOfWeek().getValue()))) {
        dayPlanningCurr = dayPlanning;
        break;
      }
    }

    return dayPlanningCurr.getMorningFrom() != null
        || dayPlanningCurr.getMorningTo() != null
        || dayPlanningCurr.getAfternoonFrom() != null
        || dayPlanningCurr.getAfternoonTo() != null;
  }

  @Override
  public LocalDate getFromPeriodDate() {
    Timesheet timesheet =
        Beans.get(TimesheetRepository.class)
            .all()
            .filter("self.user = ?1 ORDER BY self.toDate DESC", AuthUtils.getUser())
            .fetchOne();
    if (timesheet != null) {
      return timesheet.getToDate();
    } else {
      return null;
    }
  }

  @Override
  public Timesheet getCurrentTimesheet() {
    Timesheet timesheet =
        Beans.get(TimesheetRepository.class)
            .all()
            .filter(
                "self.statusSelect = ?1 AND self.user.id = ?2",
                TimesheetRepository.STATUS_DRAFT,
                AuthUtils.getUser().getId())
            .order("-id")
            .fetchOne();
    if (timesheet != null) {
      return timesheet;
    } else {
      return null;
    }
  }

  @Override
  public Timesheet getCurrentOrCreateTimesheet() {
    Timesheet timesheet = getCurrentTimesheet();
    if (timesheet == null) {
      timesheet =
          createTimesheet(
              AuthUtils.getUser(), appHumanResourceService.getTodayDateTime().toLocalDate(), null);
    }
    return timesheet;
  }

  @Override
  public Timesheet createTimesheet(User user, LocalDate fromDate, LocalDate toDate) {
    Timesheet timesheet = new Timesheet();

    timesheet.setUser(user);
    Company company = null;
    Employee employee = user.getEmployee();
    if (employee != null && employee.getMainEmploymentContract() != null) {
      company = employee.getMainEmploymentContract().getPayCompany();
    } else {
      company = user.getActiveCompany();
    }

    String timeLoggingPreferenceSelect =
        employee == null ? null : employee.getTimeLoggingPreferenceSelect();
    timesheet.setTimeLoggingPreferenceSelect(timeLoggingPreferenceSelect);
    timesheet.setCompany(company);
    timesheet.setFromDate(fromDate);
    timesheet.setStatusSelect(TimesheetRepository.STATUS_DRAFT);
    timesheet.setFullName(computeFullName(timesheet));

    return timesheet;
  }

  @Override
  public List<InvoiceLine> createInvoiceLines(
      Invoice invoice, List<TimesheetLine> timesheetLineList, int priority) throws AxelorException {

    List<InvoiceLine> invoiceLineList = new ArrayList<>();
    int count = 0;
    DateFormat ddmmFormat = new SimpleDateFormat("dd/MM");
    HashMap<String, Object[]> timeSheetInformationsMap = new HashMap<>();
    // Check if a consolidation by product and user must be done
    boolean consolidate = appHumanResourceService.getAppTimesheet().getConsolidateTSLine();

    for (TimesheetLine timesheetLine : timesheetLineList) {
      Object[] tabInformations = new Object[5];
      tabInformations[0] = timesheetLine.getProduct();
      tabInformations[1] = timesheetLine.getUser();
      // Start date
      tabInformations[2] = timesheetLine.getDate();
      // End date, useful only for consolidation
      tabInformations[3] = timesheetLine.getDate();
      tabInformations[4] = timesheetLine.getHoursDuration();

      String key = null;
      if (consolidate) {
        key = timesheetLine.getProduct().getId() + "|" + timesheetLine.getUser().getId();
        if (timeSheetInformationsMap.containsKey(key)) {
          tabInformations = timeSheetInformationsMap.get(key);
          // Update date
          if (timesheetLine.getDate().compareTo((LocalDate) tabInformations[2]) < 0) {
            // If date is lower than start date then replace start date by this one
            tabInformations[2] = timesheetLine.getDate();
          } else if (timesheetLine.getDate().compareTo((LocalDate) tabInformations[3]) > 0) {
            // If date is upper than end date then replace end date by this one
            tabInformations[3] = timesheetLine.getDate();
          }
          tabInformations[4] =
              ((BigDecimal) tabInformations[4]).add(timesheetLine.getHoursDuration());
        } else {
          timeSheetInformationsMap.put(key, tabInformations);
        }
      } else {
        key = String.valueOf(timesheetLine.getId());
        timeSheetInformationsMap.put(key, tabInformations);
      }

      timesheetLine.setInvoiced(true);
    }

    for (Object[] timesheetInformations : timeSheetInformationsMap.values()) {

      String strDate = null;
      Product product = (Product) timesheetInformations[0];
      User user = (User) timesheetInformations[1];
      LocalDate startDate = (LocalDate) timesheetInformations[2];
      LocalDate endDate = (LocalDate) timesheetInformations[3];
      BigDecimal hoursDuration = (BigDecimal) timesheetInformations[4];
      PriceList priceList =
          Beans.get(PartnerPriceListService.class)
              .getDefaultPriceList(invoice.getPartner(), PriceListRepository.TYPE_SALE);

      if (consolidate) {
        strDate = ddmmFormat.format(startDate) + " - " + ddmmFormat.format(endDate);
      } else {
        strDate = ddmmFormat.format(startDate);
      }

      invoiceLineList.addAll(
          this.createInvoiceLine(
              invoice, product, user, strDate, hoursDuration, priority * 100 + count, priceList));
      count++;
    }

    return invoiceLineList;
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(
      Invoice invoice,
      Product product,
      User user,
      String date,
      BigDecimal hoursDuration,
      int priority,
      PriceList priceList)
      throws AxelorException {

    int discountMethodTypeSelect = PriceListLineRepository.TYPE_DISCOUNT;
    int discountTypeSelect = PriceListLineRepository.AMOUNT_TYPE_NONE;
    if (product == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.TIMESHEET_PRODUCT));
    }
    BigDecimal price = product.getSalePrice();
    BigDecimal discountAmount = BigDecimal.ZERO;
    BigDecimal priceDiscounted = price;

    BigDecimal qtyConverted =
        Beans.get(UnitConversionService.class)
            .convert(
                appHumanResourceService.getAppBase().getUnitHours(),
                product.getUnit(),
                hoursDuration,
                AppBaseService.DEFAULT_NB_DECIMAL_DIGITS,
                product);

    if (priceList != null) {
      PriceListLine priceListLine =
          priceListService.getPriceListLine(product, qtyConverted, priceList, price);
      if (priceListLine != null) {
        discountMethodTypeSelect = priceListLine.getTypeSelect();
      }

      Map<String, Object> discounts =
          priceListService.getDiscounts(priceList, priceListLine, price);
      if (discounts != null) {
        discountAmount = (BigDecimal) discounts.get("discountAmount");
        discountTypeSelect = (int) discounts.get("discountTypeSelect");
        priceDiscounted =
            priceListService.computeDiscount(price, discountTypeSelect, discountAmount);
      }

      if ((appHumanResourceService.getAppBase().getComputeMethodDiscountSelect()
                  == AppBaseRepository.INCLUDE_DISCOUNT_REPLACE_ONLY
              && discountMethodTypeSelect == PriceListLineRepository.TYPE_REPLACE)
          || appHumanResourceService.getAppBase().getComputeMethodDiscountSelect()
              == AppBaseRepository.INCLUDE_DISCOUNT) {

        discountTypeSelect = PriceListLineRepository.AMOUNT_TYPE_NONE;
        price = priceDiscounted;
      }
    }

    String description = user.getFullName();
    String productName = product.getName();
    if (date != null) {
      productName += " " + "(" + date + ")";
    }

    InvoiceLineGenerator invoiceLineGenerator =
        new InvoiceLineGenerator(
            invoice,
            product,
            productName,
            price,
            price,
            priceDiscounted,
            description,
            qtyConverted,
            product.getUnit(),
            null,
            priority,
            discountAmount,
            discountTypeSelect,
            price.multiply(qtyConverted),
            null,
            false,
            false,
            0) {

          @Override
          public List<InvoiceLine> creates() throws AxelorException {

            InvoiceLine invoiceLine = this.createInvoiceLine();

            List<InvoiceLine> invoiceLines = new ArrayList<>();
            invoiceLines.add(invoiceLine);

            return invoiceLines;
          }
        };

    return invoiceLineGenerator.creates();
  }

  @Override
  @Transactional
  public void computeTimeSpent(Timesheet timesheet) {
    List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();

    if (timesheetLineList != null) {
      Map<Project, BigDecimal> projectTimeSpentMap =
          timesheetLineService.getProjectTimeSpentMap(timesheetLineList);

      Iterator<Project> projectIterator = projectTimeSpentMap.keySet().iterator();

      while (projectIterator.hasNext()) {
        Project project = projectIterator.next();
        getEntityManager().flush();
        executor.submit(
            () -> {
              final Long startTime = System.currentTimeMillis();
              boolean done = false;
              PersistenceException persistenceException = null;

              do {
                try {
                  inTransaction(
                      () -> {
                        final Project updateProject = findProject(project.getId());
                        getEntityManager().lock(updateProject, LockModeType.PESSIMISTIC_WRITE);

                        BigDecimal timeSpent =
                            projectTimeSpentMap
                                .get(updateProject)
                                .add(this.computeSubTimeSpent(updateProject));
                        updateProject.setTimeSpent(timeSpent);

                        projectRepo.save(updateProject);

                        this.computeParentTimeSpent(updateProject);
                      });
                  done = true;
                } catch (PersistenceException e) {
                  persistenceException = e;
                  sleep();
                }
              } while (!done && System.currentTimeMillis() - startTime < ENTITY_FIND_TIMEOUT);

              if (!done) {
                throw persistenceException;
              }
              return true;
            });
      }
    }
    this.setTeamTaskTotalRealHrs(timesheet.getTimesheetLineList(), true);
  }

  protected Project findProject(Long projectId) {
    Project project;
    final long startTime = System.currentTimeMillis();
    while ((project = projectRepo.find(projectId)) == null
        && System.currentTimeMillis() - startTime < ENTITY_FIND_TIMEOUT) {
      sleep();
    }
    if (project == null) {
      throw new EntityNotFoundException(projectId.toString());
    }
    return project;
  }

  protected void sleep() {
    try {
      Thread.sleep(ENTITY_FIND_INTERVAL);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public BigDecimal computeSubTimeSpent(Project project) {
    BigDecimal sum = BigDecimal.ZERO;
    List<Project> subProjectList =
        projectRepo.all().filter("self.parentProject = ?1", project).fetch();
    if (subProjectList == null || subProjectList.isEmpty()) {
      return this.computeTimeSpent(project);
    }
    for (Project projectIt : subProjectList) {
      sum = sum.add(this.computeSubTimeSpent(projectIt));
    }
    return sum;
  }

  @Override
  public void computeParentTimeSpent(Project project) {
    Project parentProject = project.getParentProject();
    if (parentProject == null) {
      return;
    }
    parentProject.setTimeSpent(project.getTimeSpent().add(this.computeTimeSpent(parentProject)));
    projectRepo.save(parentProject);
    this.computeParentTimeSpent(parentProject);
  }

  @Override
  public BigDecimal computeTimeSpent(Project project) {
    BigDecimal sum = BigDecimal.ZERO;
    List<TimesheetLine> timesheetLineList =
        timesheetlineRepo
            .all()
            .filter(
                "self.project = ?1 AND self.timesheet.statusSelect = ?2",
                project,
                TimesheetRepository.STATUS_VALIDATED)
            .fetch();
    for (TimesheetLine timesheetLine : timesheetLineList) {
      sum = sum.add(timesheetLine.getHoursDuration());
    }
    return sum;
  }

  @Override
  public String computeFullName(Timesheet timesheet) {

    User timesheetUser = timesheet.getUser();
    LocalDateTime createdOn = timesheet.getCreatedOn();

    if (timesheetUser != null && createdOn != null) {
      return timesheetUser.getFullName()
          + " "
          + createdOn.getDayOfMonth()
          + "/"
          + createdOn.getMonthValue()
          + "/"
          + timesheet.getCreatedOn().getYear()
          + " "
          + createdOn.getHour()
          + ":"
          + createdOn.getMinute();
    } else if (timesheetUser != null) {
      return timesheetUser.getFullName() + " N°" + timesheet.getId();
    } else {
      return "N°" + timesheet.getId();
    }
  }

  /**
   * Checks validity of dates related to the timesheet.
   *
   * @param timesheet
   * @throws AxelorException if
   *     <ul>
   *       <li>fromDate of the timesheet is null
   *       <li>toDate of the timesheet is null
   *       <li>timesheetLineList of the timesheet is null or empty
   *       <li>date of a timesheet line is null
   *       <li>date of a timesheet line is before fromDate or after toDate of the timesheet
   *     </ul>
   */
  protected void validateDates(Timesheet timesheet) throws AxelorException {

    List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
    LocalDate fromDate = timesheet.getFromDate();
    LocalDate toDate = timesheet.getToDate();

    if (fromDate == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TIMESHEET_NULL_FROM_DATE));

    } else if (toDate == null) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          I18n.get(IExceptionMessage.TIMESHEET_NULL_TO_DATE));

    } else if (ObjectUtils.isEmpty(timesheetLineList)) {
      throw new AxelorException(
          timesheet,
          TraceBackRepository.CATEGORY_NO_VALUE,
          I18n.get(IExceptionMessage.TIMESHEET_TIMESHEET_LINE_LIST_IS_EMPTY));

    } else {

      for (TimesheetLine timesheetLine : timesheetLineList) {
        LocalDate timesheetLineDate = timesheetLine.getDate();
        if (timesheetLineDate == null) {
          throw new AxelorException(
              timesheetLine,
              TraceBackRepository.CATEGORY_MISSING_FIELD,
              I18n.get(IExceptionMessage.TIMESHEET_LINE_NULL_DATE),
              timesheetLineList.indexOf(timesheetLine) + 1);
        }
      }
    }
  }

  /**
   * If the toDate field of the timesheet is empty, fill it with the last timesheet line date.
   *
   * @param timesheet
   * @throws AxelorException
   */
  protected void fillToDate(Timesheet timesheet) throws AxelorException {
    if (timesheet.getToDate() == null) {

      List<TimesheetLine> timesheetLineList = timesheet.getTimesheetLineList();
      if (timesheetLineList.isEmpty()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_NO_VALUE,
            I18n.get(IExceptionMessage.TIMESHEET_TIMESHEET_LINE_LIST_IS_EMPTY));
      }

      LocalDate timesheetLineLastDate = timesheetLineList.get(0).getDate();
      if (timesheetLineLastDate == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_MISSING_FIELD,
            I18n.get(IExceptionMessage.TIMESHEET_LINE_NULL_DATE),
            1);
      }

      for (TimesheetLine timesheetLine : timesheetLineList.subList(1, timesheetLineList.size())) {
        LocalDate timesheetLineDate = timesheetLine.getDate();
        if (timesheetLineDate == null) {
          throw new AxelorException(
              timesheetLine,
              TraceBackRepository.CATEGORY_MISSING_FIELD,
              I18n.get(IExceptionMessage.TIMESHEET_LINE_NULL_DATE),
              timesheetLineList.indexOf(timesheetLine) + 1);
        }
        if (timesheetLineDate.isAfter(timesheetLineLastDate)) {
          timesheetLineLastDate = timesheetLineDate;
        }
      }

      timesheet.setToDate(timesheetLineLastDate);
    }
  }

  @Override
  public List<Map<String, Object>> createDefaultLines(Timesheet timesheet) {

    List<Map<String, Object>> lines = new ArrayList<>();
    User user = timesheet.getUser();
    if (user == null || timesheet.getFromDate() == null) {
      return lines;
    }

    user = userRepo.find(user.getId());

    Product product = userHrService.getTimesheetProduct(user);

    if (product == null) {
      return lines;
    }

    List<Project> projects =
        projectRepo
            .all()
            .filter(
                "self.membersUserSet.id = ?1 and "
                    + "self.imputable = true "
                    + "and self.statusSelect != 3",
                user.getId())
            .fetch();

    for (Project project : projects) {
      TimesheetLine line =
          timesheetLineService.createTimesheetLine(
              project, product, user, timesheet.getFromDate(), timesheet, new BigDecimal(0), null);
      lines.add(Mapper.toMap(line));
    }

    return lines;
  }

  @Override
  public BigDecimal computePeriodTotal(Timesheet timesheet) {
    BigDecimal periodTotal = BigDecimal.ZERO;

    List<TimesheetLine> timesheetLines = timesheet.getTimesheetLineList();

    if (timesheetLines != null) {
      BigDecimal periodTotalTemp;
      for (TimesheetLine timesheetLine : timesheetLines) {
        if (timesheetLine != null) {
          periodTotalTemp = timesheetLine.getHoursDuration();
          if (periodTotalTemp != null) {
            periodTotal = periodTotal.add(periodTotalTemp);
          }
        }
      }
    }

    return periodTotal;
  }

  @Override
  public String getPeriodTotalConvertTitle(Timesheet timesheet) {
    String title = "";
    if (timesheet != null) {
      if (timesheet.getTimeLoggingPreferenceSelect() != null) {
        title = timesheet.getTimeLoggingPreferenceSelect();
      }
    } else {
      title = Beans.get(AppBaseService.class).getAppBase().getTimeLoggingPreferenceSelect();
    }
    switch (title) {
      case EmployeeRepository.TIME_PREFERENCE_DAYS:
        return I18n.get("Days");
      case EmployeeRepository.TIME_PREFERENCE_MINUTES:
        return I18n.get("Minutes");
      default:
        return I18n.get("Hours");
    }
  }

  @Override
  public void createDomainAllTimesheetLine(
      User user, Employee employee, ActionViewBuilder actionView) {

    actionView
        .domain("self.timesheet.company = :_activeCompany")
        .context("_activeCompany", user.getActiveCompany());

    if (employee == null || !employee.getHrManager()) {
      if (employee == null || employee.getManagerUser() == null) {
        actionView
            .domain(
                actionView.get().getDomain()
                    + " AND (self.timesheet.user = :_user OR self.timesheet.user.employee.managerUser = :_user)")
            .context("_user", user);
      } else {
        actionView
            .domain(
                actionView.get().getDomain()
                    + " AND self.timesheet.user.employee.managerUser = :_user")
            .context("_user", user);
      }
    }
  }

  @Override
  public void createValidateDomainTimesheetLine(
      User user, Employee employee, ActionView.ActionViewBuilder actionView) {

    actionView
        .domain("self.timesheet.company = :_activeCompany AND  self.timesheet.statusSelect = 2")
        .context("_activeCompany", user.getActiveCompany());

    if (employee == null || !employee.getHrManager()) {
      if (employee == null || employee.getManagerUser() == null) {
        actionView
            .domain(
                actionView.get().getDomain()
                    + " AND (self.timesheet.user = :_user OR self.timesheet.user.employee.managerUser = :_user)")
            .context("_user", user);
      } else {
        actionView
            .domain(
                actionView.get().getDomain()
                    + " AND self.timesheet.user.employee.managerUser = :_user")
            .context("_user", user);
      }
    }
  }

  @Override
  public void updateTimeLoggingPreference(Timesheet timesheet) throws AxelorException {
    String timeLoggingPref;
    if (timesheet.getUser() == null || timesheet.getUser().getEmployee() == null) {
      timeLoggingPref = EmployeeRepository.TIME_PREFERENCE_HOURS;
    } else {
      Employee employee = timesheet.getUser().getEmployee();
      timeLoggingPref = employee.getTimeLoggingPreferenceSelect();
    }
    timesheet.setTimeLoggingPreferenceSelect(timeLoggingPref);

    if (timesheet.getTimesheetLineList() != null) {
      for (TimesheetLine timesheetLine : timesheet.getTimesheetLineList()) {
        timesheetLine.setDuration(
            Beans.get(TimesheetLineService.class)
                .computeHoursDuration(timesheet, timesheetLine.getHoursDuration(), false));
      }
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public void generateLinesFromExpectedProjectPlanning(Timesheet timesheet) throws AxelorException {
    User user = timesheet.getUser();
    List<ProjectPlanningTime> planningList = getExpectedProjectPlanningTimeList(timesheet);
    for (ProjectPlanningTime projectPlanningTime : planningList) {
      TimesheetLine timesheetLine = new TimesheetLine();
      timesheetLine.setHoursDuration(projectPlanningTime.getPlannedHours());
      timesheetLine.setDuration(
          timesheetLineService.computeHoursDuration(
              timesheet, projectPlanningTime.getPlannedHours(), false));
      timesheetLine.setTimesheet(timesheet);
      timesheetLine.setUser(user);
      timesheetLine.setProduct(projectPlanningTime.getProduct());
      timesheetLine.setTeamTask(projectPlanningTime.getTask());
      timesheetLine.setProject(projectPlanningTime.getProject());
      timesheetLine.setDate(projectPlanningTime.getDate());
      timesheetLine.setProjectPlanningTime(projectPlanningTime);
      timesheet.addTimesheetLineListItem(timesheetLine);
    }
  }

  protected List<ProjectPlanningTime> getExpectedProjectPlanningTimeList(Timesheet timesheet) {
    List<ProjectPlanningTime> planningList;

    if (timesheet.getToDate() == null) {
      planningList =
          projectPlanningTimeRepository
              .all()
              .filter(
                  "self.user.id = ?1 "
                      + "AND self.date >= ?2 "
                      + "AND self.id NOT IN "
                      + "(SELECT timesheetLine.projectPlanningTime.id FROM TimesheetLine as timesheetLine "
                      + "WHERE timesheetLine.projectPlanningTime != null "
                      + "AND timesheetLine.timesheet = ?3) "
                      + "AND self.task != null ",
                  timesheet.getUser().getId(),
                  timesheet.getFromDate(),
                  timesheet)
              .fetch();
    } else {
      planningList =
          projectPlanningTimeRepository
              .all()
              .filter(
                  "self.user.id = ?1 "
                      + "AND self.date BETWEEN ?2 AND ?3 "
                      + "AND self.id NOT IN "
                      + "(SELECT timesheetLine.projectPlanningTime.id FROM TimesheetLine as timesheetLine "
                      + "WHERE timesheetLine.projectPlanningTime != null "
                      + "AND timesheetLine.timesheet = ?4) "
                      + "AND self.task != null ",
                  timesheet.getUser().getId(),
                  timesheet.getFromDate(),
                  timesheet.getToDate(),
                  timesheet)
              .fetch();
    }
    return planningList;
  }

  @Override
  public void prefillLines(Timesheet timesheet) throws AxelorException {
    PublicHolidayService holidayService = Beans.get(PublicHolidayService.class);
    LeaveService leaveService = Beans.get(LeaveService.class);
    WeeklyPlanningService weeklyPlanningService = Beans.get(WeeklyPlanningService.class);
    AppTimesheet appTimesheet = appHumanResourceService.getAppTimesheet();

    LocalDate fromDate = timesheet.getFromDate();
    LocalDate toDate = timesheet.getToDate();
    User user = timesheet.getUser();

    Employee employee = user.getEmployee();
    HRConfig config = timesheet.getCompany().getHrConfig();
    WeeklyPlanning weeklyPlanning =
        employee != null ? employee.getWeeklyPlanning() : config.getWeeklyPlanning();
    EventsPlanning holidayPlanning =
        employee != null
            ? employee.getPublicHolidayEventsPlanning()
            : config.getPublicHolidayEventsPlanning();

    if (timesheet.getTimesheetLineList() != null && !timesheet.getTimesheetLineList().isEmpty()) {
      fromDate =
          timesheet
              .getTimesheetLineList()
              .stream()
              .map(TimesheetLine::getDate)
              .max(LocalDate::compareTo)
              .get()
              .plusDays(1);
    }

    for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
      BigDecimal dayValueInHours =
          weeklyPlanningService.getWorkingDayValueInHours(
              weeklyPlanning, date, LocalTime.MIN, LocalTime.MAX);

      if (appTimesheet.getCreateLinesForHolidays()
          && holidayService.checkPublicHolidayDay(date, holidayPlanning)) {
        timesheetLineService.createTimesheetLine(
            user, date, timesheet, dayValueInHours, I18n.get(IExceptionMessage.TIMESHEET_HOLIDAY));

      } else if (appTimesheet.getCreateLinesForLeaves()) {
        LeaveRequest leave = leaveService.getLeave(user, date);
        if (leave != null) {
          BigDecimal hours = leaveService.computeDuration(leave, date, date);
          if (leave.getLeaveLine().getLeaveReason().getUnitSelect()
              == LeaveReasonRepository.UNIT_SELECT_DAYS) {
            hours = hours.multiply(dayValueInHours);
          }
          timesheetLineService.createTimesheetLine(
              user, date, timesheet, hours, I18n.get(IExceptionMessage.TIMESHEET_DAY_LEAVE));
        }
      }
    }
  }

  @Override
  @Transactional
  public void setTeamTaskTotalRealHrs(List<TimesheetLine> timesheetLines, boolean isAdd) {
    for (TimesheetLine timesheetLine : timesheetLines) {
      TeamTask teamTask = timesheetLine.getTeamTask();
      if (teamTask != null) {
        teamTask = teamTaskRepository.find(teamTask.getId());
        BigDecimal totalrealhrs =
            isAdd
                ? teamTask.getTotalRealHrs().add(timesheetLine.getHoursDuration())
                : teamTask.getTotalRealHrs().subtract(timesheetLine.getHoursDuration());
        teamTask.setTotalRealHrs(totalrealhrs);
        teamTaskRepository.save(teamTask);
      }
    }
  }

  @Override
  @Transactional
  public void removeAfterToDateTimesheetLines(Timesheet timesheet) {

    List<TimesheetLine> removedTimesheetLines = new ArrayList<>();

    for (TimesheetLine timesheetLine : ListUtils.emptyIfNull(timesheet.getTimesheetLineList())) {
      if (timesheetLine.getDate().isAfter(timesheet.getToDate())) {
        removedTimesheetLines.add(timesheetLine);
        if (timesheetLine.getId() != null) {
          timesheetlineRepo.remove(timesheetLine);
        }
      }
    }
    timesheet.getTimesheetLineList().removeAll(removedTimesheetLines);
  }
}

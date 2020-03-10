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
package com.axelor.apps.base.ical;

import com.axelor.apps.base.db.ICalendar;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.apps.message.service.MailAccountService;
import com.axelor.apps.tool.QueryBuilder;
import com.axelor.auth.db.User;
import com.axelor.common.StringUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import net.fortuna.ical4j.connector.FailedOperationException;
import net.fortuna.ical4j.connector.ObjectStoreException;
import net.fortuna.ical4j.connector.dav.CalDavCalendarCollection;
import net.fortuna.ical4j.connector.dav.PathResolver;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.util.FixedUidGenerator;
import net.fortuna.ical4j.util.HostInfo;
import net.fortuna.ical4j.util.InetAddressHostInfo;
import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;
import net.fortuna.ical4j.util.Uris;
import net.fortuna.ical4j.validate.ValidationException;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;

/** Provides calendars utilities. */
public class ICalendarService {

  static final String PRODUCT_ID = "-//Axelor//ADK Calendar 1.0//EN";
  static final String X_WR_CALNAME = "X-WR-CALNAME";

  protected static UidGenerator generator;

  @Inject protected ICalendarUserRepository iCalendarUserRepository;

  @Inject protected ICalendarEventRepository iEventRepo;

  @Inject private MailAccountService mailAccountService;

  public static class GenericPathResolver extends PathResolver {

    private String principalPath;
    private String userPath;

    public String principalPath() {
      return principalPath;
    }

    public void setPrincipalPath(String principalPath) {
      this.principalPath = principalPath;
    }

    @Override
    public String getPrincipalPath(String username) {
      return principalPath + "/" + username + "/";
    }

    public String userPath() {
      return userPath;
    }

    public void setUserPath(String userPath) {
      this.userPath = userPath;
    }

    @Override
    public String getUserPath(String username) {
      return userPath + "/" + username;
    }
  }

  /**
   * Generate next {@link Uid} to be used with calendar event.
   *
   * @return an {@link Uid} instance
   * @throws SocketException if unable to determine host name
   */
  public static Uid nextUid() throws SocketException {
    if (generator == null) {
      HostInfo info = new SimpleHostInfo("localhost");
      try {
        info = new InetAddressHostInfo(InetAddress.getLocalHost());
      } catch (Exception e) {
      }
      generator = new FixedUidGenerator(info, "" + new SecureRandom().nextInt(Integer.MAX_VALUE));
    }
    return generator.generateUid();
  }

  public static Calendar newCalendar() {
    final Calendar cal = new Calendar();
    cal.getProperties().add(new ProdId(PRODUCT_ID));
    cal.getProperties().add(Version.VERSION_2_0);
    cal.getProperties().add(CalScale.GREGORIAN);
    return cal;
  }

  public void testConnect(ICalendar cal) throws MalformedURLException, ObjectStoreException {
    PathResolver RESOLVER = getPathResolver(cal.getTypeSelect());
    Protocol protocol = getProtocol(cal.getIsSslConnection());
    URL url = new URL(protocol.getScheme(), cal.getUrl(), cal.getPort(), "");
    ICalendarStore store = new ICalendarStore(url, RESOLVER);

    try {
      store.connect(cal.getLogin(), getCalendarDecryptPassword(cal.getPassword()));
    } finally {
      store.disconnect();
    }
  }

  public Protocol getProtocol(boolean isSslConnection) {

    if (isSslConnection) {
      return Protocol.getProtocol("https");
    } else {
      return Protocol.getProtocol("http");
    }
  }

  /**
   * Load the calendar events from the given source.
   *
   * @param calendar the target {@link ICalendar}
   * @param text the raw calendar text
   * @throws ParserException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void load(ICalendar calendar, String text) throws ParserException {
    Preconditions.checkNotNull(calendar, "calendar can't be null");
    Preconditions.checkNotNull(text, "calendar source can't be null");
    final StringReader reader = new StringReader(text);
    try {
      load(calendar, reader);
    } catch (IOException e) {
    }
  }

  /**
   * Load the calendar events from the given source file.
   *
   * @param calendar the target {@link ICalendar}
   * @param file the input file
   * @throws IOException
   * @throws ParserException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void load(ICalendar calendar, File file) throws IOException, ParserException {
    Preconditions.checkNotNull(calendar, "calendar can't be null");
    Preconditions.checkNotNull(file, "input file can't be null");
    Preconditions.checkArgument(file.exists(), "no such file: " + file);

    final Reader reader = new FileReader(file);
    try {
      load(calendar, reader);
    } finally {
      reader.close();
    }
  }

  /**
   * Load the calendar events from the given reader.
   *
   * @param calendar the target {@link ICalendar}
   * @param reader the input source reader
   * @throws IOException
   * @throws ParserException
   */
  @Transactional(rollbackOn = {Exception.class})
  public void load(ICalendar calendar, Reader reader) throws IOException, ParserException {
    Preconditions.checkNotNull(calendar, "calendar can't be null");
    Preconditions.checkNotNull(reader, "reader can't be null");

    final CalendarBuilder builder = new CalendarBuilder();
    final Calendar cal = builder.build(reader);

    if (calendar.getName() == null && cal.getProperty(X_WR_CALNAME) != null) {
      calendar.setName(cal.getProperty(X_WR_CALNAME).getValue());
    }

    for (Object item : cal.getComponents(Component.VEVENT)) {
      findOrCreateEvent((VEvent) item, calendar);
    }
  }

  protected String getValue(Component component, String name) {
    if (component.getProperty(name) != null) {
      return component.getProperty(name).getValue();
    }
    return null;
  }

  @Transactional
  protected ICalendarEvent findOrCreateEvent(VEvent vEvent, ICalendar calendar) {

    String uid = vEvent.getUid().getValue();
    DtStart dtStart = vEvent.getStartDate();
    DtEnd dtEnd = vEvent.getEndDate();

    ICalendarEvent event = iEventRepo.findByUid(uid);
    if (event == null) {
      event = ICalendarEventFactory.getNewIcalEvent(calendar);
      event.setUid(uid);
      event.setCalendar(calendar);
    }

    ZoneId zoneId = OffsetDateTime.now().getOffset();
    if (dtStart.getDate() != null) {
      if (dtStart.getTimeZone() != null) {
        zoneId = dtStart.getTimeZone().toZoneId();
      }
      event.setStartDateTime(LocalDateTime.ofInstant(dtStart.getDate().toInstant(), zoneId));
    }

    if (dtEnd.getDate() != null) {
      if (dtEnd.getTimeZone() != null) {
        zoneId = dtEnd.getTimeZone().toZoneId();
      }
      event.setEndDateTime(LocalDateTime.ofInstant(dtEnd.getDate().toInstant(), zoneId));
    }

    event.setAllDay(!(dtStart.getDate() instanceof DateTime));

    event.setSubject(getValue(vEvent, Property.SUMMARY));
    event.setDescription(getValue(vEvent, Property.DESCRIPTION));
    event.setLocation(getValue(vEvent, Property.LOCATION));
    event.setGeo(getValue(vEvent, Property.GEO));
    event.setUrl(getValue(vEvent, Property.URL));
    event.setSubjectTeam(event.getSubject());
    if (Clazz.PRIVATE.getValue().equals(getValue(vEvent, Property.CLASS))) {
      event.setVisibilitySelect(ICalendarEventRepository.VISIBILITY_PRIVATE);
    } else {
      event.setVisibilitySelect(ICalendarEventRepository.VISIBILITY_PUBLIC);
    }
    if (Transp.TRANSPARENT.getValue().equals(getValue(vEvent, Property.TRANSP))) {
      event.setDisponibilitySelect(ICalendarEventRepository.DISPONIBILITY_AVAILABLE);
    } else {
      event.setDisponibilitySelect(ICalendarEventRepository.DISPONIBILITY_BUSY);
    }
    if (event.getVisibilitySelect() == ICalendarEventRepository.VISIBILITY_PRIVATE) {
      event.setSubjectTeam(I18n.get("Available"));
      if (event.getDisponibilitySelect() == ICalendarEventRepository.DISPONIBILITY_BUSY) {
        event.setSubjectTeam(I18n.get("Busy"));
      }
    }
    ICalendarUser organizer = findOrCreateUser(vEvent.getOrganizer(), event);
    if (organizer != null) {
      event.setOrganizer(organizer);
      iCalendarUserRepository.save(organizer);
    }

    for (Object item : vEvent.getProperties(Property.ATTENDEE)) {
      ICalendarUser attendee = findOrCreateUser((Property) item, event);
      if (attendee != null) {
        event.addAttendee(attendee);
        iCalendarUserRepository.save(attendee);
      }
    }
    iEventRepo.save(event);
    return event;
  }

  public ICalendarUser findOrCreateUser(User user) {
    String email = null;
    if (user.getPartner() != null
        && user.getPartner().getEmailAddress() != null
        && !Strings.isNullOrEmpty(user.getPartner().getEmailAddress().getAddress())) {
      email = user.getPartner().getEmailAddress().getAddress();
    } else if (!Strings.isNullOrEmpty(user.getEmail())) {
      email = user.getEmail();
    } else {
      return null;
    }

    ICalendarUserRepository repo = Beans.get(ICalendarUserRepository.class);
    ICalendarUser icalUser = null;
    icalUser =
        repo.all().filter("self.email = ?1 AND self.user.id = ?2", email, user.getId()).fetchOne();
    if (icalUser == null) {
      icalUser = repo.all().filter("self.user.id = ?1", user.getId()).fetchOne();
    }
    if (icalUser == null) {
      icalUser = repo.all().filter("self.email = ?1", email).fetchOne();
    }
    if (icalUser == null) {
      icalUser = new ICalendarUser();
      icalUser.setEmail(email);
      icalUser.setName(user.getFullName());
      EmailAddress emailAddress = Beans.get(EmailAddressRepository.class).findByAddress(email);
      if (emailAddress != null
          && emailAddress.getPartner() != null
          && emailAddress.getPartner().getUser() != null) {
        icalUser.setUser(emailAddress.getPartner().getUser());
      }
    }

    return icalUser;
  }

  protected ICalendarUser findOrCreateUser(Property source, ICalendarEvent event) {
    URI addr = null;
    if (source instanceof Organizer) {
      addr = ((Organizer) source).getCalAddress();
    }
    if (source instanceof Attendee) {
      addr = ((Attendee) source).getCalAddress();
    }
    if (addr == null) {
      return null;
    }

    String email = mailto(addr.toString(), true);
    ICalendarUserRepository repo = Beans.get(ICalendarUserRepository.class);
    ICalendarUser user = null;
    if (source instanceof Organizer) {
      user = repo.all().filter("self.email = ?1", email).fetchOne();
    } else {
      user =
          repo.all()
              .filter("self.email = ?1 AND self.event.id = ?2", email, event.getId())
              .fetchOne();
    }
    if (user == null) {
      user = new ICalendarUser();
      user.setEmail(email);
      user.setName(email);
      EmailAddress emailAddress = Beans.get(EmailAddressRepository.class).findByAddress(email);
      if (emailAddress != null
          && emailAddress.getPartner() != null
          && emailAddress.getPartner().getUser() != null) {
        user.setUser(emailAddress.getPartner().getUser());
      }
    }
    if (source.getParameter(Parameter.CN) != null) {
      user.setName(source.getParameter(Parameter.CN).getValue());
    }
    if (source.getParameter(Parameter.PARTSTAT) != null) {
      String role = source.getParameter(Parameter.PARTSTAT).getValue();
      if (role.equals("TENTATIVE")) {
        user.setStatusSelect(ICalendarUserRepository.STATUS_MAYBE);
      } else if (role.equals("ACCEPTED")) {
        user.setStatusSelect(ICalendarUserRepository.STATUS_YES);
      } else if (role.equals("DECLINED")) {
        user.setStatusSelect(ICalendarUserRepository.STATUS_NO);
      }
    }

    return user;
  }

  public <T extends Property> T updateUser(T target, ICalendarUser user) {

    if (user == null || user.getEmail() == null) {
      return null;
    }

    String email = mailto(user.getEmail(), false);
    String name = user.getName();

    if (target instanceof Organizer) {
      ((Organizer) target).setCalAddress(createUri(email));
    }
    if (target instanceof Attendee) {
      ((Attendee) target).setCalAddress(createUri(email));
    }
    if (name != null) {
      target.getParameters().add(new Cn(name));
    }

    return target;
  }

  protected String mailto(final String mail, boolean strip) {
    if (mail == null) {
      return null;
    }
    String res = mail.trim();
    if (strip) {
      if (res.toLowerCase().startsWith("mailto:")) {
        res = res.substring(7);
      }
    } else if (!res.toLowerCase().startsWith("mailto:")) {
      res = "mailto:" + res;
    }
    return res;
  }

  protected Date toDate(LocalDateTime dt, boolean allDay) {
    if (dt == null) return null;
    if (allDay)
      return new Date(java.util.Date.from(dt.toInstant(OffsetDateTime.now().getOffset())));
    return new DateTime(java.util.Date.from(dt.toInstant(OffsetDateTime.now().getOffset())));
  }

  protected URI createUri(String uri) {
    try {
      return Uris.create(uri);
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }

  protected VEvent createVEvent(ICalendarEvent event) throws SocketException, ParseException {
    boolean allDay = event.getAllDay() == Boolean.TRUE;

    Date start = toDate(event.getStartDateTime(), allDay);
    Date end = toDate(event.getEndDateTime(), allDay);

    VEvent vevent = new VEvent();

    PropertyList<Property> items = vevent.getProperties();

    items.add(new DtStart(start));
    if (end != null) {
      items.add(new DtEnd(end));
    }
    if (event.getSubject() != null) {
      items.add(new Summary(event.getSubject()));
    }
    if (event.getDescription() != null) {
      items.add(new Description(event.getDescription()));
    }
    if (event.getStatus() != null) {
      items.add(new Status(event.getStatus()));
    }
    if (event.getLocation() != null) {
      items.add(new Location(event.getLocation()));
    }
    if (event.getGeo() != null) {
      items.add(new Geo(event.getGeo()));
    }
    if (event.getUid() == null) {
      items.add(nextUid());
    } else {
      items.add(new Uid(event.getUid()));
    }
    if (event.getUrl() != null) {
      items.add(new Url(createUri(event.getUrl())));
    }
    if (event.getUpdatedOn() != null) {
      DateTime date =
          new DateTime(
              java.util.Date.from(
                  event.getUpdatedOn().toInstant(OffsetDateTime.now().getOffset())));
      date.setUtc(true);
      LastModified lastModified = new LastModified(date);
      items.add(lastModified);
    } else {
      DateTime date =
          new DateTime(
              java.util.Date.from(
                  event.getCreatedOn().toInstant(OffsetDateTime.now().getOffset())));
      date.setUtc(true);
      LastModified lastModified = new LastModified(date);
      items.add(lastModified);
    }

    Organizer organizer = updateUser(new Organizer(), event.getOrganizer());
    if (organizer != null) {
      items.add(organizer);
    }

    if (event.getAttendees() != null) {
      for (ICalendarUser user : event.getAttendees()) {
        Attendee attendee = updateUser(new Attendee(), user);
        if (attendee != null) {
          items.add(attendee);
        }
      }
    }

    return vevent;
  }

  /**
   * Export the calendar to the given file.
   *
   * @param calendar the source {@link ICalendar}
   * @param file the target file
   * @throws IOException
   * @throws ParseException
   * @throws ValidationException
   */
  public void export(ICalendar calendar, File file)
      throws IOException, ParseException, ValidationException {
    Preconditions.checkNotNull(calendar, "calendar can't be null");
    Preconditions.checkNotNull(file, "input file can't be null");

    final Writer writer = new FileWriter(file);
    try {
      export(calendar, writer);
    } finally {
      writer.close();
    }
  }

  /**
   * Export the calendar to the given output writer.
   *
   * @param calendar the source {@link ICalendar}
   * @param writer the output writer
   * @throws IOException
   * @throws ParseException
   * @throws ValidationException
   */
  public void export(ICalendar calendar, Writer writer)
      throws IOException, ParseException, ValidationException {
    Preconditions.checkNotNull(calendar, "calendar can't be null");
    Preconditions.checkNotNull(writer, "writer can't be null");
    Preconditions.checkNotNull(getICalendarEvents(calendar), "can't export empty calendar");

    Calendar cal = newCalendar();
    cal.getProperties().add(new XProperty(X_WR_CALNAME, calendar.getName()));

    for (ICalendarEvent item : getICalendarEvents(calendar)) {
      VEvent event = createVEvent(item);
      cal.getComponents().add(event);
    }

    CalendarOutputter outputter = new CalendarOutputter();
    outputter.output(cal, writer);
  }

  public void sync(ICalendar calendar, boolean all, int weeks)
      throws MalformedURLException, ICalendarException {
    if (all || calendar.getLastSynchronizationDateT() == null) {
      sync(calendar, null, null);
    } else {
      int nbOfWeeks = weeks <= 0 ? calendar.getSynchronizationDuration() : weeks;
      LocalDateTime now = Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime();
      sync(calendar, now.minusWeeks(nbOfWeeks), now.plusWeeks(nbOfWeeks));
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected void sync(ICalendar calendar, LocalDateTime startDate, LocalDateTime endDate)
      throws ICalendarException, MalformedURLException {
    PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
    Protocol protocol = getProtocol(calendar.getIsSslConnection());
    URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
    ICalendarStore store = new ICalendarStore(url, RESOLVER);
    try {
      String password = getCalendarDecryptPassword(calendar.getPassword());

      if (calendar.getLogin() != null
          && calendar.getPassword() != null
          && store.connect(calendar.getLogin(), password)) {
        List<CalDavCalendarCollection> colList = store.getCollections();
        if (!colList.isEmpty()) {
          calendar = doSync(calendar, colList.get(0), startDate, endDate);
          calendar.setLastSynchronizationDateT(
              Beans.get(AppBaseService.class).getTodayDateTime().toLocalDateTime());
          Beans.get(ICalendarRepository.class).save(calendar);
        }
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CALENDAR_NOT_VALID));
      }
    } catch (Exception e) {
      throw new ICalendarException(e);
    } finally {
      store.disconnect();
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  protected ICalendar doSync(
      ICalendar calendar,
      CalDavCalendarCollection collection,
      LocalDateTime startDate,
      LocalDateTime endDate)
      throws IOException, URISyntaxException, ParseException, ObjectStoreException,
          ConstraintViolationException, DavException, ParserConfigurationException, ParserException,
          AxelorException {

    final boolean keepRemote = calendar.getKeepRemote() == Boolean.TRUE;

    final Map<String, VEvent> modifiedRemoteEvents = new HashMap<>();
    final List<ICalendarEvent> modifiedLocalEvents = getICalendarEvents(calendar);
    final Set<String> allRemoteUids = new HashSet<>();
    final Set<VEvent> updatedEvents = new HashSet<>();
    List<VEvent> events = null;
    Instant lastSynchro = null;

    if (calendar.getLastSynchronizationDateT() != null) {
      lastSynchro =
          calendar.getLastSynchronizationDateT().toInstant(OffsetDateTime.now().getOffset());
    } else {
      lastSynchro =
          Beans.get(AppBaseService.class)
              .getTodayDateTime()
              .toLocalDateTime()
              .toInstant(OffsetDateTime.now().getOffset());
    }

    if (startDate == null || endDate == null) {
      events = ICalendarStore.getModifiedEvents(collection, null, allRemoteUids);
    } else {
      events =
          ICalendarStore.getModifiedEventsInRange(
              collection, lastSynchro, allRemoteUids, startDate, endDate);
    }

    if (events == null || events.isEmpty()) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.CALENDAR_NO_EVENTS_FOR_SYNC_ERROR));
    }

    for (VEvent item : events) {
      modifiedRemoteEvents.put(item.getUid().getValue(), item);
    }

    for (ICalendarEvent item : modifiedLocalEvents) {
      VEvent source = createVEvent(item);
      VEvent target = modifiedRemoteEvents.get(source.getUid().getValue());

      // If uid is empty, the event is new
      if (StringUtils.isBlank(item.getUid())) {
        item.setUid(source.getUid().getValue());
        Calendar cal = newCalendar();
        cal.getComponents().add(source);
        collection.addCalendar(cal);
        allRemoteUids.add(item.getUid());
      }
      // else it has been modified
      else {
        // if target is null, then it hasn't been modified or it has been deleted
        if (target == null) {
          target = source;
        } else {
          updateEvent(source, target, keepRemote);
          modifiedRemoteEvents.remove(target.getUid().getValue());
        }
        updatedEvents.add(target);
      }
    }

    // Process remaining modified remote events, find and update or create a
    // corresponding ICalendarEvent
    for (Map.Entry<String, VEvent> entry : modifiedRemoteEvents.entrySet()) {
      findOrCreateEvent(entry.getValue(), calendar);
    }

    // update remote events
    for (VEvent item : updatedEvents) {
      Calendar cal = newCalendar();
      cal.getComponents().add(item);
      collection.updateCalendar(cal);
    }

    // remove deleted remote events
    removeDeletedEventsInRange(allRemoteUids, calendar, startDate, endDate);
    return calendar;
  }

  @Transactional
  protected void removeDeletedEventsInRange(
      Set<String> allRemoteUids,
      ICalendar calendar,
      LocalDateTime startDate,
      LocalDateTime endDate) {

    QueryBuilder<ICalendarEvent> queryBuilder = QueryBuilder.of(ICalendarEvent.class);
    queryBuilder.add("self.uid NOT in (:uids)").bind("uids", allRemoteUids);
    queryBuilder.add("self.calendar = :calendar").bind("calendar", calendar);
    queryBuilder.add("self.archived = :archived OR self.archived IS NULL").bind("archived", false);

    if (startDate != null && endDate != null) {
      queryBuilder
          .add(
              "self.startDateTime BETWEEN :start AND :end OR self.endDateTime BETWEEN :start AND :end")
          .bind("start", startDate)
          .bind("end", endDate);
    }

    ICalendarEventRepository repo = Beans.get(ICalendarEventRepository.class);

    for (ICalendarEvent event : queryBuilder.build().fetch()) {
      if (ICalendarRepository.ICAL_ONLY.equals(calendar.getSynchronizationSelect())) {
        repo.remove(event);
      } else {
        event.setArchived(true);
      }
    }
  }

  protected VEvent updateEvent(VEvent source, VEvent target, boolean keepRemote)
      throws IOException, URISyntaxException, ParseException {

    final String[] names = {
      Property.UID,
      Property.URL,
      Property.SUMMARY,
      Property.DESCRIPTION,
      Property.DTSTART,
      Property.DTEND,
      Property.ORGANIZER,
      Property.ATTENDEE
    };

    if (keepRemote) {
      VEvent tmp = target;
      target = source;
      source = tmp;
    } else {
      if (source.getLastModified() != null && target.getLastModified() != null) {
        ZoneId zoneId = OffsetDateTime.now().getOffset();
        if (source.getLastModified().getTimeZone() != null) {
          zoneId = source.getLastModified().getTimeZone().toZoneId();
        }
        LocalDateTime lastModifiedSource =
            LocalDateTime.ofInstant(source.getLastModified().getDate().toInstant(), zoneId);
        LocalDateTime lastModifiedTarget =
            LocalDateTime.ofInstant(target.getLastModified().getDate().toInstant(), zoneId);
        if (lastModifiedSource.isBefore(lastModifiedTarget)) {
          VEvent tmp = target;
          target = source;
          source = tmp;
        }
      } else if (target.getLastModified() != null) {
        VEvent tmp = target;
        target = source;
        source = tmp;
      }
      if (source != target) {
        for (String name : names) {
          Property s = source.getProperty(name);
          Property t = target.getProperty(name);
          if (s == null && t == null) {
            continue;
          }
          if (t == null) {
            t = s;
          }
          if (s == null) {
            target.getProperties().remove(t);
          } else {
            t.setValue(s.getValue());
          }
        }
      }
    }
    return target;
  }

  public PathResolver getPathResolver(int typeSelect) {
    switch (typeSelect) {
      case ICalendarRepository.ICAL_SERVER:
        return PathResolver.ICAL_SERVER;

      case ICalendarRepository.CALENDAR_SERVER:
        return PathResolver.CALENDAR_SERVER;

      case ICalendarRepository.GCAL:
        return PathResolver.GCAL;

      case ICalendarRepository.ZIMBRA:
        return PathResolver.ZIMBRA;

      case ICalendarRepository.KMS:
        return PathResolver.KMS;

      case ICalendarRepository.CGP:
        return PathResolver.CGP;

      case ICalendarRepository.CHANDLER:
        return PathResolver.CHANDLER;

      default:
        return null;
    }
  }

  public ICalendarEvent createEvent(
      LocalDateTime fromDateTime,
      LocalDateTime toDateTime,
      User user,
      String description,
      int type,
      String subject) {
    ICalendarEvent event = new ICalendarEvent();
    event.setSubject(subject);
    event.setStartDateTime(fromDateTime);
    event.setEndDateTime(toDateTime);
    event.setTypeSelect(type);
    event.setUser(user);
    event.setCalendar(user.getiCalendar());
    if (!Strings.isNullOrEmpty(description)) {
      event.setDescription(description);
    }
    return event;
  }

  public net.fortuna.ical4j.model.Calendar removeCalendar(
      CalDavCalendarCollection collection, String uid)
      throws FailedOperationException, ObjectStoreException {
    net.fortuna.ical4j.model.Calendar calendar = collection.getCalendar(uid);

    DeleteMethod deleteMethod = new DeleteMethod(collection.getPath() + uid + ".ics");
    try {
      collection.getStore().getClient().execute(deleteMethod);
    } catch (IOException e) {
      throw new ObjectStoreException(e);
    }
    if (!deleteMethod.succeeded()) {
      throw new FailedOperationException(deleteMethod.getStatusLine().toString());
    }

    return calendar;
  }

  public net.fortuna.ical4j.model.Calendar getCalendar(String uid, ICalendar calendar)
      throws ICalendarException, MalformedURLException {
    net.fortuna.ical4j.model.Calendar cal = null;
    PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
    Protocol protocol = getProtocol(calendar.getIsSslConnection());
    URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
    ICalendarStore store = new ICalendarStore(url, RESOLVER);
    try {
      if (store.connect(calendar.getLogin(), calendar.getPassword())) {
        List<CalDavCalendarCollection> colList = store.getCollections();
        if (!colList.isEmpty()) {
          CalDavCalendarCollection collection = colList.get(0);
          cal = collection.getCalendar(uid);
        }
      } else {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CALENDAR_NOT_VALID));
      }
    } catch (Exception e) {
      throw new ICalendarException(e);
    } finally {
      store.disconnect();
    }
    return cal;
  }

  public void removeEventFromIcal(ICalendarEvent event)
      throws MalformedURLException, ICalendarException {
    if (event.getCalendar() != null && !Strings.isNullOrEmpty(event.getUid())) {
      ICalendar calendar = event.getCalendar();
      PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
      Protocol protocol = getProtocol(calendar.getIsSslConnection());
      URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
      ICalendarStore store = new ICalendarStore(url, RESOLVER);
      try {
        if (store.connect(
            calendar.getLogin(), getCalendarDecryptPassword(calendar.getPassword()))) {
          List<CalDavCalendarCollection> colList = store.getCollections();
          if (!colList.isEmpty()) {
            CalDavCalendarCollection collection = colList.get(0);
            final Map<String, VEvent> remoteEvents = new HashMap<>();

            for (VEvent item : ICalendarStore.getEvents(collection)) {
              remoteEvents.put(item.getUid().getValue(), item);
            }

            VEvent target = remoteEvents.get(event.getUid());
            if (target != null) removeCalendar(collection, target.getUid().getValue());
          }
        } else {
          throw new AxelorException(
              TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
              I18n.get(IExceptionMessage.CALENDAR_NOT_VALID));
        }
      } catch (Exception e) {
        throw new ICalendarException(e);
      } finally {
        store.disconnect();
      }
    }
  }

  @Transactional
  public void removeOldEvents(List<ICalendarEvent> oldEvents) {

    for (ICalendarEvent event : oldEvents) {
      iEventRepo.remove(event);
    }
  }

  protected List<ICalendarEvent> getICalendarEvents(ICalendar calendar) {
    LocalDateTime lastSynchro = calendar.getLastSynchronizationDateT();
    if (lastSynchro != null) {
      return iEventRepo
          .all()
          .filter(
              "COALESCE(self.archived, false) = false AND self.calendar = ?1 AND COALESCE(self.updatedOn, self.createdOn) > ?2",
              calendar,
              lastSynchro)
          .fetch();
    }
    return iEventRepo
        .all()
        .filter("COALESCE(self.archived, false) = false AND self.calendar = ?1", calendar)
        .fetch();
  }

  public String getCalendarEncryptPassword(String password) {

    return mailAccountService.getEncryptPassword(password);
  }

  public String getCalendarDecryptPassword(String password) {

    return mailAccountService.getDecryptPassword(password);
  }
}

/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
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
package com.axelor.apps.crm.service;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.jackrabbit.webdav.client.methods.DeleteMethod;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.app.AppSettings;
import com.axelor.apps.base.db.ICalendarEvent;
import com.axelor.apps.base.db.ICalendarUser;
import com.axelor.apps.base.db.Team;
import com.axelor.apps.base.db.repo.ICalendarEventRepository;
import com.axelor.apps.base.db.repo.ICalendarUserRepository;
import com.axelor.apps.base.ical.ICalendarException;
import com.axelor.apps.base.ical.ICalendarService;
import com.axelor.apps.base.ical.ICalendarStore;
import com.axelor.apps.crm.db.Calendar;
import com.axelor.apps.crm.db.CalendarManagement;
import com.axelor.apps.crm.db.Event;
import com.axelor.apps.crm.db.ICalendar;
import com.axelor.apps.crm.db.repo.CalendarRepository;
import com.axelor.apps.crm.db.repo.EventRepository;
import com.axelor.apps.crm.exception.IExceptionMessage;
import com.axelor.apps.message.db.EmailAddress;
import com.axelor.apps.message.db.repo.EmailAddressRepository;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import net.fortuna.ical4j.connector.FailedOperationException;
import net.fortuna.ical4j.connector.ObjectStoreException;
import net.fortuna.ical4j.connector.dav.CalDavCalendarCollection;
import net.fortuna.ical4j.connector.dav.PathResolver;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ConstraintViolationException;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.XProperty;

public class CalendarService extends ICalendarService{

	private final Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	static final String X_WR_CALNAME = "X-WR-CALNAME";

	protected CalendarRepository calendarRepo;
	protected ICalendarUserRepository iCalendarUserRepo;

	@Inject
	public CalendarService(CalendarRepository calendarRepo, ICalendarUserRepository iCalendarUserRepo) {
		this.calendarRepo = calendarRepo;
		this.iCalendarUserRepo = iCalendarUserRepo;
	}

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
	
	
	public PathResolver getPathResolver(int typeSelect)  {
		switch (typeSelect) {
		case ICalendar.ICAL_SERVER :
			return PathResolver.ICAL_SERVER;

		case ICalendar.CALENDAR_SERVER :
			return PathResolver.CALENDAR_SERVER;

		case ICalendar.GCAL :
			return PathResolver.GCAL;

		case ICalendar.ZIMBRA :
			return PathResolver.ZIMBRA;

		case ICalendar.KMS :
			return PathResolver.KMS;

		case ICalendar.CGP :
			return PathResolver.CGP;
					
		case ICalendar.CHANDLER :
			return PathResolver.CHANDLER;
			
		default:
			return null;
		}
	}
	
	
	public Protocol getProtocol(boolean isSslConnection)  {
		
		if(isSslConnection)  {
			return Protocol.getProtocol("https");
		}
		else  {
			return Protocol.getProtocol("http");
		}
		
	}
	
	
	@Transactional
	public void importCalendar(Calendar cal, File file) throws IOException, ParserException  {
		
		log.debug("Import calendar {} ::: {}", cal.getName(), file.getName());
		this.loadCRM(cal, file);
		calendarRepo.save(cal);
	}
	

	@Transactional
	public void loadCRM(Calendar calendar, File file) throws IOException, ParserException {
		Preconditions.checkNotNull(calendar, "calendar can't be null");
		Preconditions.checkNotNull(file, "input file can't be null");
		Preconditions.checkArgument(file.exists(), "no such file: " + file);

		final Reader reader = new FileReader(file);
		try {
			loadCRM(calendar, reader);
		} finally {
			reader.close();
		}
	}
	
	@Transactional
	public void loadCRM(Calendar calendar, Reader reader) throws IOException, ParserException {
		Preconditions.checkNotNull(calendar, "calendar can't be null");
		Preconditions.checkNotNull(reader, "reader can't be null");

		final CalendarBuilder builder = new CalendarBuilder();
		final net.fortuna.ical4j.model.Calendar cal = builder.build(reader);

		if (calendar.getName() == null && cal.getProperty(X_WR_CALNAME) != null) {
			calendar.setName(cal.getProperty(X_WR_CALNAME).getValue());
		}

		for (Object item : cal.getComponents(Component.VEVENT)) {
			Event event = findOrCreateEventCRM((VEvent) item);
			calendar.addEventsCrm(event);
		}
	}
	
	@Transactional
	protected Event findOrCreateEventCRM(VEvent vEvent) {

		String uid = vEvent.getUid().getValue();
		DtStart dtStart = vEvent.getStartDate();
		DtEnd dtEnd = vEvent.getEndDate();

		EventRepository repo = Beans.get(EventRepository.class);
		Event event = repo.all().filter("self.uid = ?1", uid).fetchOne();
		if (event == null) {
			event = new Event();
			event.setUid(uid);
		}
		if(event.getTypeSelect() == null || event.getTypeSelect() == 0){
			event.setTypeSelect(EventRepository.TYPE_EVENT);
		}
		event.setStartDateTime(new LocalDateTime(dtStart.getDate()));
		event.setEndDateTime(new LocalDateTime(dtEnd.getDate()));
		event.setAllDay(!(dtStart.getDate() instanceof DateTime));

		event.setSubject(getValue(vEvent, Property.SUMMARY));
		event.setDescription(getValue(vEvent, Property.DESCRIPTION));
		event.setLocation(getValue(vEvent, Property.LOCATION));
		event.setGeo(getValue(vEvent, Property.GEO));
		event.setUrl(getValue(vEvent, Property.URL));
		event.setSubjectTeam(event.getSubject());
		if(Clazz.PRIVATE.getValue().equals(getValue(vEvent, Property.CLASS))){
			event.setVisibilitySelect(ICalendarEventRepository.VISIBILITY_PRIVATE);
		}
		else{
			event.setVisibilitySelect(ICalendarEventRepository.VISIBILITY_PUBLIC);
		}
		if(Transp.TRANSPARENT.getValue().equals(getValue(vEvent, Property.TRANSP))){
			event.setDisponibilitySelect(ICalendarEventRepository.DISPONIBILITY_AVAILABLE);
		}
		else{
			event.setDisponibilitySelect(ICalendarEventRepository.DISPONIBILITY_BUSY);
		}
		if(event.getVisibilitySelect() == ICalendarEventRepository.VISIBILITY_PRIVATE){
			event.setSubjectTeam(I18n.get("Available"));
			if(event.getDisponibilitySelect() == ICalendarEventRepository.DISPONIBILITY_BUSY){
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

		return event;
	}	
	
	protected ICalendarUser findOrCreateUser(Property source, Event event) {
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
		}
		else{
			user = repo.all().filter("self.email = ?1 AND self.event.id = ?2", email, event.getId()).fetchOne();
		}
		if (user == null) {
			user = new ICalendarUser();
			user.setEmail(email);
			user.setName(email);
			EmailAddress emailAddress = Beans.get(EmailAddressRepository.class).findByAddress(email);
			if(emailAddress != null && emailAddress.getPartner() != null && emailAddress.getPartner().getUser() != null){
				user.setUser(emailAddress.getPartner().getUser());
			}
		}
		if (source.getParameter(Parameter.CN) != null) {
			user.setName(source.getParameter(Parameter.CN).getValue());
		}
		if(source.getParameter(Parameter.PARTSTAT) != null){
			String role = source.getParameter(Parameter.PARTSTAT).getValue();
			if(role.equals("TENTATIVE")){
				user.setStatusSelect(ICalendarUserRepository.STATUS_MAYBE);
			}
			else if(role.equals("ACCEPTED")){
				user.setStatusSelect(ICalendarUserRepository.STATUS_YES);
			}
			else if(role.equals("DECLINED")){
				user.setStatusSelect(ICalendarUserRepository.STATUS_NO);
			}
		}

		return user;
	}
	
	public ICalendarUser findOrCreateUser(User user) {
		String email = null;
		if (user.getPartner() != null && user.getPartner().getEmailAddress() != null
				&& !Strings.isNullOrEmpty(user.getPartner().getEmailAddress().getAddress())) {
			email = user.getPartner().getEmailAddress().getAddress();
		}
		else if (!Strings.isNullOrEmpty(user.getEmail())) {
			email = user.getEmail();
		}
		else {
			return null;
		}

		ICalendarUserRepository repo = Beans.get(ICalendarUserRepository.class);
		ICalendarUser icalUser = null;
		icalUser = repo.all().filter("self.email = ?1 AND self.user.id = ?2", email, user.getId()).fetchOne();
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
			if(emailAddress != null && emailAddress.getPartner() != null && emailAddress.getPartner().getUser() != null){
				icalUser.setUser(emailAddress.getPartner().getUser());
			}
		}

		return icalUser;
	}

	
	
//	public void createVCard()  {
//		
//		List<Property> props = new ArrayList<Property>();
//		props.add(new Source(URI.create("ldap://ldap.example.com/cn=Babs%20Jensen,%20o=Babsco,%20c=US")));
//		props.add(new Name("Babs Jensen's Contact Information"));
//		props.add(Kind.INDIVIDUAL);
//		// add a custom property..
//		props.add(new Property("test") {
//		    @Override
//		    public String getValue() {
//		        return null;
//		    }
//
//		    @Override
//		    public void validate() throws ValidationException {
//		    }
//		});
//
//		VCard vcard = new VCard(props);
//		return vcard;
//	}
	
	public boolean testConnect(Calendar cal) throws MalformedURLException, ObjectStoreException
	{
		boolean connected = false;
		PathResolver RESOLVER = getPathResolver(cal.getTypeSelect());
		Protocol protocol = getProtocol(cal.getIsSslConnection());
		URL url = new URL(protocol.getScheme(), cal.getUrl(), cal.getPort(), "");
		ICalendarStore store = new ICalendarStore(url, RESOLVER);
		
		try 
		{
			connected = store.connect(cal.getLogin(), cal.getPassword());
		}
		finally {
			store.disconnect();
		}
		return connected;
	}


	public void export(Calendar calendar) throws IOException, ValidationException, ParseException {
		String path = AppSettings.get().get("file.upload.dir");
		if (!path.endsWith("/")) {
			path += "/";
        }	
		String name = calendar.getName();
		if (!name.endsWith(".ics")) {
			name += ".ics";
        }
		FileOutputStream fout = new FileOutputStream(path + name );
		Preconditions.checkNotNull(calendar, "calendar can't be null");
		Preconditions.checkNotNull(calendar.getEventsCrm(), "can't export empty calendar");

		net.fortuna.ical4j.model.Calendar cal = newCalendar();
		cal.getProperties().add(new XProperty(X_WR_CALNAME, calendar.getName()));

		for (ICalendarEvent item : calendar.getEventsCrm()) {
			VEvent event = createVEvent(item);
			cal.getComponents().add(event);
		}
		
		CalendarOutputter outputter = new CalendarOutputter();
		outputter.output(cal, fout);
	}
	
	public File export(net.fortuna.ical4j.model.Calendar calendar) throws IOException, ValidationException, ParseException {
		String path = AppSettings.get().get("file.upload.dir");
		if (!path.endsWith("/")) {
			path += "/";
        }
		String name = calendar.getProperty(X_WR_CALNAME).getValue();
		if (!name.endsWith(".ics")) {
			name += ".ics";
        }
		File file = new File(path + name );	
		Writer writer = new FileWriter(file);
		CalendarOutputter outputter = new CalendarOutputter();
		outputter.output(calendar, writer);
		writer.close();
		return file;
	}
	
	@Transactional
	public void sync(Calendar calendar)
			throws ICalendarException, MalformedURLException {
		PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
		Protocol protocol = getProtocol(calendar.getIsSslConnection());
		URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
		ICalendarStore store = new ICalendarStore(url, RESOLVER);
		try {
			if(calendar.getLogin() != null && calendar.getPassword() != null && store.connect(calendar.getLogin(), calendar.getPassword())){
				List<CalDavCalendarCollection> colList = store.getCollections();
				if(!colList.isEmpty()){
					calendar = doSync(calendar, colList.get(0));
					calendarRepo.save(calendar);
				}
			}
			else{
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.CALENDAR_NOT_VALID)), IException.CONFIGURATION_ERROR);
			}
		} catch (Exception e) {
			throw new ICalendarException(e);
		}
		finally {
			store.disconnect();
		}
	}

	@SuppressWarnings("unchecked")
	protected Calendar doSync(Calendar calendar, CalDavCalendarCollection collection)
			throws IOException, URISyntaxException, ParseException, ObjectStoreException, ConstraintViolationException {

		final String[] names = {
			Property.UID,
			Property.URL,
			Property.SUMMARY,
			Property.DESCRIPTION,
			Property.DTSTART,
			Property.DTEND,
			Property.ORGANIZER,
			Property.CLASS,
			Property.TRANSP,
			Property.ATTENDEE
		};

		final boolean keepRemote = calendar.getKeepRemote() == Boolean.TRUE;

		final Map<String, VEvent> remoteEvents = new HashMap<>();
		final Map<VEvent, Integer> localEvents = new HashMap<>();
		final Set<String> synced = new HashSet<>();
		for (VEvent item : ICalendarStore.getEvents(collection)) {
			remoteEvents.put(item.getUid().getValue(), item);
		}

		for (ICalendarEvent item : calendar.getEventsCrm()) {
			VEvent source = createVEvent(item);
			VEvent target = remoteEvents.get(source.getUid().getValue());
			if (target == null && Strings.isNullOrEmpty(item.getUid())) {
				target = source;
			}
			
			if(target != null){
				if (keepRemote) {
					VEvent tmp = target;
					target = source;
					source = tmp;
				}
				else{
					if(source.getLastModified() != null && target.getLastModified() != null){
						LocalDateTime lastModifiedSource = new LocalDateTime(source.getLastModified().getDateTime());
						LocalDateTime lastModifiedTarget = new LocalDateTime(target.getLastModified().getDateTime());
						if(lastModifiedSource.isBefore(lastModifiedTarget)){
							VEvent tmp = target;
							target = source;
							source = tmp;
						}
					}
					else if(target.getLastModified() != null){
						VEvent tmp = target;
						target = source;
						source = tmp;
					}
				}
				Event item2 = (Event) item;
				localEvents.put(target, item2.getTypeSelect()); // Associate event with typeSelect for further use 
				synced.add(target.getUid().getValue());

				if (source == target) {
					continue;
				}

				for (String name : names) {
					if(!name.equals(Property.ATTENDEE)){
						Property s = source.getProperty(name);
						Property t = target.getProperty(name);
						PropertyList items = target.getProperties();
						if (s == null && t == null) {
							continue;
						}
						else if (t == null) {
							t = s;
							items.add(t);
						}
						else if (s == null) {
							target.getProperties().remove(t);
						} else {
							t.setValue(s.getValue());
						}
						
					}
					else{
						PropertyList sourceList = source.getProperties(Property.ATTENDEE);
						PropertyList targetList = target.getProperties(Property.ATTENDEE);
						target.getProperties().removeAll(targetList);
						target.getProperties().addAll(sourceList);
						target.getProperties();
					}
				}
			}
		}

		for (String uid : remoteEvents.keySet()) {
			if (!synced.contains(uid)) {
				VEvent vEvent = remoteEvents.get(uid);
				// adds startDate of vEvent to the Trigger if it exists in vEvent 
				if (!vEvent.getAlarms().isEmpty()) {
					Trigger t = (Trigger) vEvent.getAlarms().getComponent("VALARM").getProperty("TRIGGER");
					if (t.getDateTime() == null) {
						DtStart start = (DtStart) vEvent.getProperty("DTSTART");
						t.setDateTime((DateTime) start.getDate());
					}
				}
				localEvents.put(vEvent, 2); // events imported from remote calendar are by default 'meetings' (typeSelect = 2)
			}
		}

		// update local events
		final List<Event> iEvents = new ArrayList<>();
		for (Map.Entry<VEvent, Integer> item : localEvents.entrySet()) {
			Event iEvent = findOrCreateEventCRM(item.getKey());
			iEvent.setTypeSelect(item.getValue()); // set 'typeSelect' to new event created
			iEvents.add(iEvent);
		}
		calendar.getEventsCrm().clear();
		for (Event event : iEvents) {
			calendar.addEventsCrm(event);
		}

		// update remote events
		for (VEvent item : localEvents.keySet()) {
			if (!synced.contains(item.getUid().getValue())) {
				continue;
			}
			net.fortuna.ical4j.model.Calendar cal = newCalendar();
			cal.getComponents().add(item);
			collection.addCalendar(cal);
		}

		return calendar;
	}
	
	public void removeEventFromIcal(Event event) throws MalformedURLException, ICalendarException{
		if(event.getCalendarCrm() != null && !Strings.isNullOrEmpty(event.getUid())){
			Calendar calendar  = event.getCalendarCrm();
			PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
			Protocol protocol = getProtocol(calendar.getIsSslConnection());
			URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
			ICalendarStore store = new ICalendarStore(url, RESOLVER);
			try {
				if(store.connect(calendar.getLogin(), calendar.getPassword())){
					List<CalDavCalendarCollection> colList = store.getCollections();
					if(!colList.isEmpty()){
						CalDavCalendarCollection collection = colList.get(0);
						final Map<String, VEvent> remoteEvents = new HashMap<>();

						for (VEvent item : ICalendarStore.getEvents(collection)) {
							remoteEvents.put(item.getUid().getValue(), item);
						}

						VEvent target = remoteEvents.get(event.getUid());
						removeCalendar(collection,target.getUid().getValue());
					}
				}
				else{
					throw new AxelorException(String.format(I18n.get(IExceptionMessage.CALENDAR_NOT_VALID)), IException.CONFIGURATION_ERROR);
				}
			} catch (Exception e) {
				throw new ICalendarException(e);
			}
			finally {
				store.disconnect();
			}
		}
	}
	
	public net.fortuna.ical4j.model.Calendar removeCalendar(CalDavCalendarCollection collection,String uid) throws FailedOperationException, ObjectStoreException {
        net.fortuna.ical4j.model.Calendar calendar = collection.getCalendar(uid);

        DeleteMethod deleteMethod = new DeleteMethod( collection.getPath() + uid + ".ics");
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
	
	public net.fortuna.ical4j.model.Calendar getCalendar(String uid, Calendar calendar) throws ICalendarException, MalformedURLException{
		net.fortuna.ical4j.model.Calendar cal = null;
		PathResolver RESOLVER = getPathResolver(calendar.getTypeSelect());
		Protocol protocol = getProtocol(calendar.getIsSslConnection());
		URL url = new URL(protocol.getScheme(), calendar.getUrl(), calendar.getPort(), "");
		ICalendarStore store = new ICalendarStore(url, RESOLVER);
		try {
			if(store.connect(calendar.getLogin(), calendar.getPassword())){
				List<CalDavCalendarCollection> colList = store.getCollections();
				if(!colList.isEmpty()){
					CalDavCalendarCollection collection = colList.get(0);
					cal = collection.getCalendar(uid);
				}
			}
			else{
				throw new AxelorException(String.format(I18n.get(IExceptionMessage.CALENDAR_NOT_VALID)), IException.CONFIGURATION_ERROR);
			}
		} catch (Exception e) {
			throw new ICalendarException(e);
		}
		finally {
			store.disconnect();
		}
		return cal;
	}

	public List<Long> showSharedCalendars(User user){
		Team team = user.getActiveTeam();
		Set<User> followedUsers = user.getFollowersCalUserSet();
		List<Long> calendarIdlist = new ArrayList<Long>();
		
		for (User userIt : followedUsers) {
			for (CalendarManagement calendarManagement : userIt.getCalendarManagementList()) {
				if((user.equals(calendarManagement.getUser())) || (team != null && team.equals(calendarManagement.getTeam()))){
					if(calendarManagement.getAllCalendars()){
						List<Calendar> calendarList = Beans.get(CalendarRepository.class).all().filter("self.user.id = ?1",
								userIt.getId()).fetch();
						for (Calendar calendar : calendarList) {
							calendarIdlist.add(calendar.getId());
						}
					}
					else if(calendarManagement.getIcalCalendars()){
						for (Calendar calendar : calendarManagement.getCalendarSet()) {
							calendarIdlist.add(calendar.getId());
						}
					}
				}
			}
		}	
		
		List<Calendar> calList = Beans.get(CalendarRepository.class).all().filter("self.user.id = ?1", user.getId()).fetch();
		for (Calendar calendar : calList) {
			calendarIdlist.add(calendar.getId());
		}
		return calendarIdlist;
	}

	public ActionViewBuilder buildActionViewMyEvents(User user) {
		return buildActionViewEvents(user, Sets.newHashSet(user), I18n.get("My Calendar"),
				"event-calendar-color-by-calendar");
	}

	public ActionViewBuilder buildActionViewTeamEvents(User user) {
		Team team = user.getActiveTeam();
		Set<User> userSet;

		if (team == null || team.getUserSet() == null || team.getUserSet().isEmpty()) {
			userSet = Sets.newHashSet(user);
		} else {
			userSet = team.getUserSet();
		}

		return buildActionViewEvents(user, userSet, I18n.get("Team Calendar"), "event-calendar-color-by-user");
	}

	/**
	 * Build an action view for an event calendar.
	 * 
	 * @param user
	 * @param userSet
	 * @param title
	 * @param calendar
	 * @return
	 */
	private ActionViewBuilder buildActionViewEvents(User user, Set<User> userSet, String title, String calendar) {
		Set<Long> userIdSet = new HashSet<>();

		for (User userIt : userSet) {
			userIdSet.add(userIt.getId());
		}

		List<String> domainItemList = new ArrayList<>();
		domainItemList.add("self.user.id IN (:userIdSet)");
		domainItemList.add("self.calendarCrm.user.id IN (:userIdSet)");

		ActionViewBuilder actionViewBuilder = ActionView.define(title);
		actionViewBuilder.model(Event.class.getName());
		actionViewBuilder.add("calendar", calendar);
		actionViewBuilder.add("grid", "event-grid");
		actionViewBuilder.add("form", "event-form");
		actionViewBuilder.context("_typeSelect", 2);
		actionViewBuilder.context("_internalUser", user.getId());
		actionViewBuilder.context("userIdSet", userIdSet);

		List<ICalendarUser> iCalendarUserList = iCalendarUserRepo.all().filter("self.user IN (:userSet)")
				.bind("userSet", userSet).fetch();

		if (!iCalendarUserList.isEmpty()) {
			domainItemList.add("self.organizer IN (:iCalendarUserList)");
			actionViewBuilder.context("iCalendarUserList", iCalendarUserList);

			for (int i = 0; i < iCalendarUserList.size(); ++i) {
				// Why is an id required here instead of an ICalendarUser?
				String key = String.format("iCalendarUserId%d", i);
				domainItemList.add(String.format(":%s MEMBER OF self.attendees", key));
				actionViewBuilder.context(key, iCalendarUserList.get(i).getId());
			}
		}

		String domain = Joiner.on(" OR ").join(domainItemList);
		actionViewBuilder.domain(domain);

		return actionViewBuilder;
	}

	public ActionViewBuilder buildActionViewSharedEvents(User user) {
		Set<User> userSet = Sets.newHashSet(user);
		Set<Long> userIdSet = new HashSet<>();
		List<String> domainItemList = new ArrayList<>();
		domainItemList.add("self.user.id IN (:userIdSet)");
		domainItemList.add("self.calendarCrm.user.id IN (:userIdSet)");

		ActionViewBuilder actionViewBuilder = ActionView.define(I18n.get("Shared Calendar"));
		actionViewBuilder.model(Event.class.getName());
		actionViewBuilder.add("calendar", "event-calendar-color-by-user");
		actionViewBuilder.add("grid", "event-grid");
		actionViewBuilder.add("form", "event-form");
		actionViewBuilder.context("_typeSelect", 2);
		actionViewBuilder.context("_internalUser", user.getId());
		actionViewBuilder.context("userIdSet", userIdSet);

		List<ICalendarUser> iCalendarUserList = iCalendarUserRepo.all().filter("self.user = :user").bind("user", user)
				.fetch();
		int index = 0;

		for (User userIt : user.getFollowersCalUserSet()) {
			for (CalendarManagement calendarManagement : userIt.getCalendarManagementList()) {
				if (user.equals(calendarManagement.getUser())
						|| user.getActiveTeam() != null && user.getActiveTeam().equals(calendarManagement.getTeam())) {

					if (calendarManagement.getAllCalendars()) {
						userSet.add(userIt);
						iCalendarUserList.addAll(iCalendarUserRepo.all().filter("self.user = :user", userIt).fetch());
					} else {
						if (calendarManagement.getErpCalendars()) {
							String userIdKey = String.format("userId%d", index);
							domainItemList
									.add(String.format("self.user.id = :%s AND self.calendarCrm IS NULL", userIdKey));
							actionViewBuilder.context(userIdKey, userIt.getId());

							List<ICalendarUser> iCalendarUserItList = iCalendarUserRepo.all()
									.filter("self.user = :user").bind("user", userIt).fetch();

							for (int i = 0; i < iCalendarUserItList.size(); ++i) {
								String iCalendarUserIdKey = String.format("iCalendarUserId%dx%d", index, i);
								domainItemList.add(String.format(
										"(:%s MEMBER OF self.attendees OR self.organizer.id = :%s) "
												+ "AND self.calendarCrm IS NULL",
										iCalendarUserIdKey, iCalendarUserIdKey));
								actionViewBuilder.context(iCalendarUserIdKey, iCalendarUserItList.get(i).getId());
							}
						}
						if (calendarManagement.getIcalCalendars()) {
							String iCalendarUserListKey = String.format("iCalendarUserList%d", index);
							domainItemList.add(String.format("self.calendarCrm IN (:%s)", iCalendarUserListKey));
							actionViewBuilder.context(iCalendarUserListKey, calendarManagement.getCalendarSet());
						}
					}
				}
				++index;
			}
		}

		for (User userIt : userSet) {
			userIdSet.add(userIt.getId());
		}

		if (!iCalendarUserList.isEmpty()) {
			domainItemList.add("self.organizer IN (:iCalendarUserList)");
			actionViewBuilder.context("iCalendarUserList", iCalendarUserList);

			for (int i = 0; i < iCalendarUserList.size(); ++i) {
				String key = String.format("iCalendarUserId%d", i);
				domainItemList.add(String.format(":%s MEMBER OF self.attendees", key));
				actionViewBuilder.context(key, iCalendarUserList.get(i).getId());
			}
		}

		String domain = Joiner.on(" OR ").join(domainItemList);
		actionViewBuilder.domain(domain);

		return actionViewBuilder;
	}

}

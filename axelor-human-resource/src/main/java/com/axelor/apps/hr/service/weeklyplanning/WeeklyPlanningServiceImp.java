package com.axelor.apps.hr.service.weeklyplanning;

import java.util.List;

import org.joda.time.LocalDate;

import com.axelor.apps.hr.db.DayPlanning;
import com.axelor.apps.hr.db.WeeklyPlanning;
import com.axelor.exception.AxelorException;
import com.google.inject.persist.Transactional;

public class WeeklyPlanningServiceImp implements WeeklyPlanningService{

	@Override
	@Transactional(rollbackOn={Exception.class})
	public WeeklyPlanning initPlanning(WeeklyPlanning planning){
		String[] dayTab= new String[]{"monday","tuesday","wednesday","thursday","friday","saturday","sunday"};
		for (int i = 0; i < dayTab.length; i++) {
			DayPlanning day = new DayPlanning();
			day.setName(dayTab[i]);
			planning.addWeekDay(day);
		}
		return planning;
	}

	@Override
	@Transactional(rollbackOn={Exception.class})
	public WeeklyPlanning checkPlanning(WeeklyPlanning planning) throws AxelorException{

		List<DayPlanning> listDay = planning.getWeekDays();
		for (DayPlanning dayPlanning : listDay) {

			if(dayPlanning.getMorningFrom() != null && dayPlanning.getMorningTo() != null){
				if(dayPlanning.getMorningFrom().isAfter(dayPlanning.getMorningTo()))
				{
					String message = "Invalid times in morning on "+dayPlanning.getName();
					throw new AxelorException(message, 5);
				}
			}

			if(dayPlanning.getMorningTo() != null && dayPlanning.getAfternoonFrom() != null){
				if(dayPlanning.getMorningTo().isAfter(dayPlanning.getAfternoonFrom()))
				{
					String message = "Invalid times between morning and afternoon on "+dayPlanning.getName();
					throw new AxelorException(message, 5);
				}
			}

			if(dayPlanning.getAfternoonFrom() != null && dayPlanning.getAfternoonTo() != null){
				if(dayPlanning.getAfternoonFrom().isAfter(dayPlanning.getAfternoonTo()))
				{
					String message = "Invalid times in afternoon on "+dayPlanning.getName();
					throw new AxelorException(message, 5);
				}
			}

			if((dayPlanning.getMorningFrom() == null && dayPlanning.getMorningTo() != null)
				|| (dayPlanning.getMorningTo() == null && dayPlanning.getMorningFrom() != null)
				|| (dayPlanning.getAfternoonFrom() == null && dayPlanning.getAfternoonTo() != null)
				|| (dayPlanning.getAfternoonTo() == null && dayPlanning.getAfternoonFrom() != null))
			{
				String message = "Some times are null and should not on "+dayPlanning.getName();
				throw new AxelorException(message, 5);
			}
		}
		return planning;
	}

	@Override
	public double workingDayValue(WeeklyPlanning planning, LocalDate date){
		double value = 0;
		DayPlanning dayPlanning = findDayPlanning(planning,date);
		if(dayPlanning.getMorningFrom()!= null && dayPlanning.getMorningTo()!= null){
			value+=0.5;
		}
		if(dayPlanning.getAfternoonFrom()!= null && dayPlanning.getAfternoonTo()!= null){
			value+=0.5;
		}
		return value;
	}
	
	public DayPlanning findDayPlanning(WeeklyPlanning planning, LocalDate date){
		int dayOfWeek = date.getDayOfWeek();
		switch (dayOfWeek) {
		case 1:
			return findDayWithName(planning,"monday");
			
		case 2:
			return findDayWithName(planning,"tuesday");
			
		case 3:
			return findDayWithName(planning,"wednesday");
			
		case 4:
			return findDayWithName(planning,"thursday");
			
		case 5:
			return findDayWithName(planning,"friday");
			
		case 6:
			return findDayWithName(planning,"saturday");
			
		case 7:
			return findDayWithName(planning,"sunday");
			

		default:
			return findDayWithName(planning,"null");
		}
	}
	
	public DayPlanning findDayWithName(WeeklyPlanning planning, String name){
		List<DayPlanning> dayPlanningList = planning.getWeekDays();
		for (DayPlanning dayPlanning : dayPlanningList) {
			if(dayPlanning.getName().equals(name)){
				return dayPlanning;
			}
		}
		return null;
	}
}

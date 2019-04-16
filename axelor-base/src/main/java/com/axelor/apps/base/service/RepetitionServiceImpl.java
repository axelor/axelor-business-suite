/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service;

import com.axelor.apps.base.db.Repetition;
import com.axelor.apps.base.db.repo.RepetitionRepository;
import com.axelor.apps.tool.date.DateTool;
import com.axelor.i18n.I18n;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public class RepetitionServiceImpl implements RepetitionService {

  private static final String PERIOD = ".";
  private static final String COMMA = ",";
  private static final String SPACE = " ";

  /* SUMMARY */
  @Override
  public String computeSummary(Repetition repetition) {
    if (repetition.getFrequencySelect() == null) {
      return "";
    }

    StringBuilder summary = new StringBuilder();

    Integer everyCount = repetition.getEvery();
    switch (repetition.getFrequencySelect()) {
      case RepetitionRepository.FREQUENCY_DAILY:
        summary.append(
            String.format(
                I18n.get("REPETITION_Every day", "Every {0} days", everyCount), everyCount));
        break;

      case RepetitionRepository.FREQUENCY_WEEKLY:
        summary.append(
            String.format(
                I18n.get("REPETITION_Every week", "Every {0} weeks", everyCount), everyCount));
        summary.append(COMMA).append(SPACE);

        summary.append(
            I18n.get("THE_SINGULAR", "THE_PLURAL", 2)); // force plural for other languages
        summary.append(SPACE);

        summary.append(getDaysString(repetition).toLowerCase());
        break;

      case RepetitionRepository.FREQUENCY_MONTHLY:
        summary.append(
            String.format(
                I18n.get("REPETITION_Every month", "Every {0} months", everyCount), everyCount));
        summary.append(COMMA).append(SPACE);

        String monthlyTypeSelect = repetition.getMonthlyTypeSelect();
        if (monthlyTypeSelect != null) {
          switch (monthlyTypeSelect) {
            case RepetitionRepository.MONTHLY_TYPE_EACH:
              summary.append(
                  I18n.get("THE_SINGULAR", "THE_PLURAL", 2)); // force plural for other languages
              summary.append(SPACE);

              summary.append(getDaysOfMonthString(repetition));
              summary.append(SPACE);

              summary.append(I18n.get("of the month"));
              break;

            case RepetitionRepository.MONTHLY_TYPE_ON_THE:
              summary.append(I18n.get("THE_SINGULAR"));
              summary.append(SPACE);

              summary.append(getMonthDayString(repetition));
              summary.append(SPACE);

              summary.append(getDayString(repetition));
              break;

            default:
              throw new IllegalStateException("Unexpected value: " + monthlyTypeSelect);
          }
        }
        break;

      case RepetitionRepository.FREQUENCY_YEARLY:
        summary.append(
            String.format(
                I18n.get("REPETITION_Every year", "Every {0} years", everyCount), everyCount));
        summary.append(COMMA).append(SPACE);

        summary.append(
            I18n.get("THE_SINGULAR", "THE_PLURAL", 2)); // force plural for other languages
        summary.append(SPACE);

        summary.append(getDaysOfMonthString(repetition));
        summary.append(SPACE);

        summary.append(I18n.get("of"));
        summary.append(SPACE);

        summary.append(getMonthsString(repetition).toLowerCase());
        break;

      default:
        throw new IllegalStateException("Unexpected value: " + repetition.getFrequencySelect());
    }

    summary.append(PERIOD);
    return summary.toString();
  }

  private String getDaysString(Repetition repetition) {
    String daySelect = repetition.getDaySelect();
    if (daySelect != null) {
      String days =
          Stream.of(daySelect.replaceAll(SPACE, "").split(COMMA))
              .map(StringUtils::capitalize)
              .map(I18n::get)
              .collect(Collectors.joining(COMMA));

      return replaceLastCommaWithAnd(days);
    }

    return "";
  }

  private String getDaysOfMonthString(Repetition repetition) {
    String dayOfMonthSelect = repetition.getDayOfMonthSelect();
    if (dayOfMonthSelect != null) {
      String daysOfMonth = dayOfMonthSelect.replaceAll(SPACE, "");

      return replaceLastCommaWithAnd(daysOfMonth);
    }

    return "";
  }

  protected String getDayString(Repetition repetition) {
    String daySelect = repetition.getDaySelect();
    if (daySelect != null) {
      return I18n.get(StringUtils.capitalize(daySelect)).toLowerCase();
    }

    return "";
  }

  protected String getMonthDayString(Repetition repetition) {
    String monthDaySelect = repetition.getMonthDaySelect();
    if (monthDaySelect != null) {
      return I18n.get(StringUtils.capitalize(monthDaySelect)).toLowerCase();
    }

    return "";
  }

  private String getMonthsString(Repetition repetition) {
    String months =
        Stream.of(repetition.getMonthSelect().replaceAll(SPACE, "").split(COMMA))
            .map(StringUtils::capitalize)
            .map(I18n::get)
            .collect(Collectors.joining(COMMA));

    return replaceLastCommaWithAnd(months);
  }

  private String replaceLastCommaWithAnd(String str) {
    str = str.replaceAll(SPACE, "");
    str = str.replaceAll(COMMA, COMMA + SPACE);

    int lastCommaIndex = str.lastIndexOf(COMMA);
    if (lastCommaIndex >= 0) {
      str =
          new StringBuilder(str)
              .replace(lastCommaIndex, lastCommaIndex + 1, SPACE + I18n.get("and"))
              .toString();
    }

    return str;
  }

  /* MAIN */
  @Override
  public List<LocalDate> getDates(Repetition repetition, LocalDate startDate, LocalDate endDate) {
    if (endDate.isBefore(startDate)) {
      throw new DateTimeException("End date is before start date.");
    }

    switch (repetition.getFrequencySelect()) {
      case RepetitionRepository.FREQUENCY_DAILY:
        return getDatesForDaily(repetition, startDate, endDate);

      case RepetitionRepository.FREQUENCY_WEEKLY:
        return getDatesForWeekly(repetition, startDate, endDate);

      case RepetitionRepository.FREQUENCY_MONTHLY:
        switch (repetition.getMonthlyTypeSelect()) {
          case RepetitionRepository.MONTHLY_TYPE_EACH:
            return getDatesForMonthlyEach(repetition, startDate, endDate);

          case RepetitionRepository.MONTHLY_TYPE_ON_THE:
            return getDatesForMonthlyOnThe(repetition, startDate, endDate);

          default:
            throw new IllegalStateException(
                "Unexpected value: " + repetition.getMonthlyTypeSelect());
        }

      case RepetitionRepository.FREQUENCY_YEARLY:
        return getDatesForYearly(repetition, startDate, endDate);

      default:
        throw new IllegalStateException("Unexpected value: " + repetition.getFrequencySelect());
    }
  }

  private List<LocalDate> getDatesForDaily(
      Repetition repetition, LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> dates = new HashSet<>();

    LocalDate currentDate = startDate;
    do {
      dates.add(currentDate);

      currentDate = currentDate.plusDays(repetition.getEvery());
    } while (currentDate.compareTo(endDate) <= 0);

    return new ArrayList<>(dates);
  }

  private List<LocalDate> getDatesForWeekly(
      Repetition repetition, LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> dates = new HashSet<>();

    List<Integer> days = getDays(repetition);

    LocalDate currentDate = startDate;
    do {
      LocalDate firstDayOfThisWeek =
          currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
      firstDayOfThisWeek = currentDate.isAfter(firstDayOfThisWeek) ? currentDate : firstDayOfThisWeek;

      for (Integer day : days) {
        LocalDate date = firstDayOfThisWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)));
        if (DateTool.isBetween(startDate, endDate, date)) {
          dates.add(date);
        }
      }

      currentDate = currentDate.plusWeeks(repetition.getEvery());
    } while (currentDate.compareTo(endDate) <= 0);

    return new ArrayList<>(dates);
  }

  private List<LocalDate> getDatesForMonthlyEach(
      Repetition repetition, LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> dates = new HashSet<>();

    List<Integer> daysOfMonth = getDaysOfMonth(repetition);

    LocalDate currentDate = startDate;
    do {
      LocalDate lastDayOfThisMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth());
      lastDayOfThisMonth = endDate.isBefore(lastDayOfThisMonth) ? endDate : lastDayOfThisMonth;

      for (Integer dayOfMonth : daysOfMonth) {
        try {
          LocalDate date = currentDate.withDayOfMonth(dayOfMonth);

          if (DateTool.isBetween(startDate, lastDayOfThisMonth, date)) {
            dates.add(date);
          }
        } catch (DateTimeException e) {
          // skip date
        }
      }

      currentDate = currentDate.plusMonths(repetition.getEvery());
    } while (currentDate.compareTo(endDate) <= 0);

    return new ArrayList<>(dates);
  }

  private List<LocalDate> getDatesForMonthlyOnThe(
      Repetition repetition, LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> dates = new HashSet<>();

    int monthDay = getMonthDay(repetition);
    int day = getDay(repetition);

    LocalDate currentDate = startDate;
    do {
      LocalDate lastDayOfThisMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth());
      lastDayOfThisMonth = endDate.isBefore(lastDayOfThisMonth) ? endDate : lastDayOfThisMonth;

      LocalDate date =
          currentDate.with(TemporalAdjusters.dayOfWeekInMonth(monthDay, DayOfWeek.of(day)));
      if (DateTool.isBetween(startDate, lastDayOfThisMonth, date)) {
        dates.add(date);
      }

      currentDate = currentDate.plusMonths(repetition.getEvery());
    } while (currentDate.compareTo(endDate) <= 0);

    return new ArrayList<>(dates);
  }

  private List<LocalDate> getDatesForYearly(
      Repetition repetition, LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> dates = new HashSet<>();

    List<Integer> months = getMonths(repetition);
    List<Integer> daysOfMonth = getDaysOfMonth(repetition);

    LocalDate currentDate = startDate;
    do {
      LocalDate lastDayOfThisYear = currentDate.with(TemporalAdjusters.lastDayOfYear());
      lastDayOfThisYear = endDate.isBefore(lastDayOfThisYear) ? endDate : lastDayOfThisYear;

      for (Integer month : months) {
        for (Integer dayOfMonth : daysOfMonth) {
          LocalDate date = null;

          try {
            date =
                currentDate
                    // set currentDate to first day of month...
                    .with(TemporalAdjusters.firstDayOfMonth())
                    // ...to avoid exception on month change
                    .withMonth(month)
                    .withDayOfMonth(dayOfMonth);
          } catch (DateTimeException e) {
            // skip date
          }

          if (date != null && DateTool.isBetween(currentDate, lastDayOfThisYear, date)) {
            dates.add(date);
          }
        }
      }

      currentDate = currentDate.plusYears(repetition.getEvery());
    } while (currentDate.compareTo(endDate) <= 0);

    return new ArrayList<>(dates);
  }

  private List<Integer> getDays(Repetition repetition) {
    return Stream.of(repetition.getDaySelect().replaceAll(SPACE, "").split(COMMA))
        .map(dayOfWeek -> DayOfWeek.valueOf(dayOfWeek.toUpperCase()).getValue())
        .collect(Collectors.toList());
  }

  private int getDay(Repetition repetition) {
    return getDays(repetition).get(0);
  }

  private int getMonthDay(Repetition repetition) {
    switch (repetition.getMonthDaySelect()) {
      case RepetitionRepository.MONTH_DAY_FIRST:
        return 1;

      case RepetitionRepository.MONTH_DAY_SECOND:
        return 2;

      case RepetitionRepository.MONTH_DAY_THIRD:
        return 3;

      case RepetitionRepository.MONTH_DAY_FOURTH:
        return 4;

      case RepetitionRepository.MONTH_DAY_FIFTH:
        return 5;

      case RepetitionRepository.MONTH_DAY_LAST:
        return -1;

      default:
        throw new IllegalStateException("Unexpected value: " + repetition.getMonthDaySelect());
    }
  }

  private List<Integer> getMonths(Repetition repetition) {
    return Stream.of(repetition.getMonthSelect().replaceAll(SPACE, "").split(COMMA))
        .map(month -> Month.valueOf(month.toUpperCase()).getValue())
        .collect(Collectors.toList());
  }

  private List<Integer> getDaysOfMonth(Repetition repetition) {
    return Stream.of(repetition.getDayOfMonthSelect().replaceAll(SPACE, "").split(COMMA))
        .mapToInt(Integer::parseInt)
        .boxed()
        .collect(Collectors.toList());
  }

  /* UTILS */
  @Override
  public String getFrequencyWord(Repetition repetition) {
    if (repetition.getFrequencySelect() == null) {
      return "";
    }

    int everyCount = repetition.getEvery();

    switch (repetition.getFrequencySelect()) {
      case RepetitionRepository.FREQUENCY_DAILY:
        return I18n.get("day", "days", everyCount);

      case RepetitionRepository.FREQUENCY_WEEKLY:
        return I18n.get("week", "weeks", everyCount);

      case RepetitionRepository.FREQUENCY_MONTHLY:
        return I18n.get("month", "months", everyCount);

      case RepetitionRepository.FREQUENCY_YEARLY:
        return I18n.get("year", "years", everyCount);

      default:
        return "";
    }
  }

  @Override
  public String sort(Repetition repetition, String selectField) {
    switch (selectField) {
      case "daySelect":
        if (StringUtils.isNotEmpty(repetition.getDaySelect())) {
          return Stream.of(repetition.getDaySelect().replaceAll(SPACE, "").split(COMMA))
              .sorted(
                  Comparator.comparingInt(day -> DayOfWeek.valueOf(day.toUpperCase()).getValue()))
              .collect(Collectors.joining(COMMA));
        }
        return "";

      case "dayOfMonthSelect":
        if (StringUtils.isNotEmpty(repetition.getDayOfMonthSelect())) {
          return Stream.of(repetition.getDayOfMonthSelect().replaceAll(SPACE, "").split(COMMA))
              .mapToInt(Integer::parseInt)
              .sorted()
              .mapToObj(String::valueOf)
              .collect(Collectors.joining(COMMA));
        }
        return "";

      case "monthSelect":
        if (StringUtils.isNotEmpty(repetition.getMonthSelect())) {
          return Stream.of(repetition.getMonthSelect().replaceAll(SPACE, "").split(COMMA))
              .sorted(
                  Comparator.comparingInt(month -> Month.valueOf(month.toUpperCase()).getValue()))
              .collect(Collectors.joining(COMMA));
        }
        return "";

      default:
        throw new IllegalStateException("Unexpected value: " + selectField);
    }
  }
}

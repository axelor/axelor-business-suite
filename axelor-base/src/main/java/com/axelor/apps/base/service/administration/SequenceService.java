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
package com.axelor.apps.base.service.administration;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Sequence;
import com.axelor.apps.base.db.SequenceLettersTypeSelect;
import com.axelor.apps.base.db.SequenceTypeSelect;
import com.axelor.apps.base.db.SequenceVersion;
import com.axelor.apps.base.db.repo.SequenceRepository;
import com.axelor.apps.base.db.repo.SequenceVersionRepository;
import com.axelor.apps.base.exceptions.IExceptionMessage;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.tool.StringTool;
import com.axelor.db.Model;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.repo.MetaSelectItemRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.lang.invoke.MethodHandles;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ThreadSafe
@Singleton
public class SequenceService {

  private static final String DRAFT_PREFIX = "#";

  private static final String PATTERN_FULL_YEAR = "%YYYY";
  private static final String PATTERN_YEAR = "%YY";
  private static final String PATTERN_MONTH = "%M";
  private static final String PATTERN_FULL_MONTH = "%FM";
  private static final String PATTERN_DAY = "%D";
  private static final String PATTERN_WEEK = "%WY";
  private static final String PADDING_STRING = "0";

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private SequenceVersionRepository sequenceVersionRepository;
  private AppBaseService appBaseService;
  @Inject private SequenceRepository sequenceRepo;

  @Inject
  public SequenceService(
      SequenceVersionRepository sequenceVersionRepository, AppBaseService appBaseService) {
    this.sequenceVersionRepository = sequenceVersionRepository;
    this.appBaseService = appBaseService;
  }

  public static boolean isYearValid(Sequence sequence) {
    boolean yearlyResetOk = sequence.getYearlyResetOk();

    if (!yearlyResetOk) {
      return true;
    }

    String seqPrefixe = StringUtils.defaultString(sequence.getPrefixe(), "");
    String seqSuffixe = StringUtils.defaultString(sequence.getSuffixe(), "");
    String seq = seqPrefixe + seqSuffixe;

    // FIXME
    if (yearlyResetOk && !seq.contains(PATTERN_YEAR) && !seq.contains(PATTERN_FULL_YEAR)) {
      return false;
    }

    return true;
  }

  public static boolean isMonthValid(Sequence sequence) {
    boolean monthlyResetOk = sequence.getMonthlyResetOk();

    if (!monthlyResetOk) {
      return true;
    }

    String seqPrefixe = StringUtils.defaultString(sequence.getPrefixe(), "");
    String seqSuffixe = StringUtils.defaultString(sequence.getSuffixe(), "");
    String seq = seqPrefixe + seqSuffixe;

    // FIXME
    if (monthlyResetOk
        && ((!seq.contains(PATTERN_MONTH) && !seq.contains(PATTERN_FULL_MONTH))
            || (!seq.contains(PATTERN_YEAR) && !seq.contains(PATTERN_FULL_YEAR)))) {
      return false;
    }

    return true;
  }

  public static boolean isSequenceLengthValid(Sequence sequence) {
    String seqPrefixe = StringUtils.defaultString(sequence.getPrefixe(), "").replace("%", "");
    String seqSuffixe = StringUtils.defaultString(sequence.getSuffixe(), "").replace("%", "");

    return (seqPrefixe.length() + seqSuffixe.length() + sequence.getPadding()) <= 14;
  }

  public Sequence getSequence(String code, Company company) {
    if (code == null) {
      return null;
    }

    if (company == null) {
      return sequenceRepo.findByCodeSelect(code);
    }

    return sequenceRepo.find(code, company);
  }

  public String getSequenceNumber(String code) {
    return this.getSequenceNumber(code, null);
  }

  public String getSequenceNumber(String code, Company company) {
    Sequence sequence = getSequence(code, company);

    if (sequence == null) {
      return null;
    }

    return this.getSequenceNumber(sequence, appBaseService.getTodayDate(company));
  }

  public boolean hasSequence(String code, Company company) {
    return getSequence(code, company) != null;
  }

  public String getSequenceNumber(Sequence sequence) {
    return getSequenceNumber(sequence, appBaseService.getTodayDate(sequence.getCompany()));
  }

  @Transactional
  public String getSequenceNumber(Sequence sequence, LocalDate refDate) {
    SequenceVersion sequenceVersion = getVersion(sequence, refDate);

    String seqPrefixe = StringUtils.defaultString(sequence.getPrefixe(), "");
    String seqSuffixe = StringUtils.defaultString(sequence.getSuffixe(), "");
    String sequenceValue;

    if (sequence.getSequenceTypeSelect() == SequenceTypeSelect.NUMBERS) {
      sequenceValue =
          StringUtils.leftPad(
              sequenceVersion.getNextNum().toString(), sequence.getPadding(), PADDING_STRING);

    } else {
      sequenceValue = findNextLetterSequence(sequenceVersion);
    }

    sequenceVersion.setNextNum(sequenceVersion.getNextNum() + sequence.getToBeAdded());
    String nextSeq =
        (seqPrefixe + sequenceValue + seqSuffixe)
            .replace(PATTERN_FULL_YEAR, Integer.toString(refDate.get(ChronoField.YEAR_OF_ERA)))
            .replace(PATTERN_YEAR, refDate.format(DateTimeFormatter.ofPattern("yy")))
            .replace(PATTERN_MONTH, Integer.toString(refDate.getMonthValue()))
            .replace(PATTERN_FULL_MONTH, refDate.format(DateTimeFormatter.ofPattern("MM")))
            .replace(PATTERN_DAY, Integer.toString(refDate.getDayOfMonth()))
            .replace(
                PATTERN_WEEK, Integer.toString(refDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));

    LOG.debug("nextSeq : : : : {}", nextSeq);

    sequenceVersionRepository.save(sequenceVersion);
    return nextSeq;
  }

  private String findNextLetterSequence(SequenceVersion sequenceVersion) {
    long n = sequenceVersion.getNextNum();
    char[] buf = new char[(int) Math.floor(Math.log(25 * (n + 1)) / Math.log(26))];
    for (int i = buf.length - 1; i >= 0; i--) {
      n--;
      buf[i] = (char) ('A' + n % 26);
      n /= 26;
    }

    if (sequenceVersion.getSequence().getSequenceLettersTypeSelect()
        == SequenceLettersTypeSelect.UPPERCASE) {
      return new String(buf);
    }

    return new String(buf).toLowerCase();
  }

  public SequenceVersion getVersion(Sequence sequence, LocalDate refDate) {
    LOG.debug("Reference date : : : : {}", refDate);

    if (Boolean.TRUE.equals(sequence.getMonthlyResetOk())) {
      return getVersionByMonth(sequence, refDate);
    }

    if (Boolean.TRUE.equals(sequence.getYearlyResetOk())) {
      return getVersionByYear(sequence, refDate);
    }

    return getVersionByDate(sequence, refDate);
  }

  protected SequenceVersion getVersionByDate(Sequence sequence, LocalDate refDate) {
    SequenceVersion sequenceVersion = sequenceVersionRepository.findByDate(sequence, refDate);

    if (sequenceVersion == null) {
      sequenceVersion = new SequenceVersion(sequence, refDate, null, 1L);
    }

    return sequenceVersion;
  }

  protected SequenceVersion getVersionByMonth(Sequence sequence, LocalDate refDate) {
    SequenceVersion sequenceVersion =
        sequenceVersionRepository.findByMonth(sequence, refDate.getMonthValue(), refDate.getYear());

    if (sequenceVersion == null) {
      sequenceVersion =
          new SequenceVersion(
              sequence,
              refDate.withDayOfMonth(1),
              refDate.withDayOfMonth(refDate.lengthOfMonth()),
              1L);
    }

    return sequenceVersion;
  }

  protected SequenceVersion getVersionByYear(Sequence sequence, LocalDate refDate) {
    SequenceVersion sequenceVersion =
        sequenceVersionRepository.findByYear(sequence, refDate.getYear());

    if (sequenceVersion == null) {
      sequenceVersion =
          new SequenceVersion(
              sequence,
              refDate.withDayOfMonth(1),
              refDate.withDayOfMonth(refDate.lengthOfMonth()),
              1L);
    }

    return sequenceVersion;
  }

  public String getDefaultTitle(Sequence sequence) {
    MetaSelectItem item =
        Beans.get(MetaSelectItemRepository.class)
            .all()
            .filter(
                "self.select.name = ? AND self.value = ?",
                "sequence.generic.code.select",
                sequence.getCodeSelect())
            .fetchOne();

    return item.getTitle();
  }

  public String getDraftSequenceNumber(Model model) throws AxelorException {
    if (model.getId() == null) {
      throw new AxelorException(
          model,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SEQUENCE_NOT_SAVED_RECORD));
    }

    return String.format("%s%d", DRAFT_PREFIX, model.getId());
  }

  /**
   * Gets draft sequence number with leading zeros.
   *
   * @param model
   * @param zeroPadding
   * @return
   * @throws AxelorException
   */
  public String getDraftSequenceNumber(Model model, int zeroPadding) throws AxelorException {
    if (model.getId() == null) {
      throw new AxelorException(
          model,
          TraceBackRepository.CATEGORY_INCONSISTENCY,
          I18n.get(IExceptionMessage.SEQUENCE_NOT_SAVED_RECORD));
    }

    return String.format(
        "%s%s",
        DRAFT_PREFIX, StringTool.fillStringLeft(String.valueOf(model.getId()), '0', zeroPadding));
  }

  /**
   * Checks whether a sequence number is empty or draft.
   *
   * <p>Also consider '*' as draft character for backward compatibility.
   *
   * @param sequenceNumber
   * @return
   */
  public boolean isEmptyOrDraftSequenceNumber(String sequenceNumber) {
    return Strings.isNullOrEmpty(sequenceNumber)
        || sequenceNumber.matches(String.format("[\\%s\\*]\\d+", DRAFT_PREFIX));
  }

  public String computeFullName(Sequence sequence) {
    StringBuilder fn = new StringBuilder();

    if (sequence.getPrefixe() != null) {
      fn.append(sequence.getPrefixe());
    }

    for (int i = 0; i < sequence.getPadding(); i++) {
      fn.append("X");
    }

    if (sequence.getSuffixe() != null) {
      fn.append(sequence.getSuffixe());
    }

    fn.append(" - ");
    fn.append(sequence.getName());

    return fn.toString();
  }
}

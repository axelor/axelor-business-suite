/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
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
package com.axelor.apps.base.service.excelreport.components;

import com.axelor.apps.base.service.excelreport.config.ExcelReportConstants;
import com.axelor.common.ObjectUtils;
import com.itextpdf.awt.geom.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;

public class ExcelReportPictureServiceImpl implements ExcelReportPictureService {

  @Override
  public void getPictures(
      Sheet sheet,
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap,
      List<CellRangeAddress> mergedCellsRangeAddressList,
      String sheetName) {
    ImmutablePair<Integer, Integer> pair;
    ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> triple;
    List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> tripleList =
        new ArrayList<>();

    Drawing<?> drawing = sheet.getDrawingPatriarch();
    if (ObjectUtils.notEmpty(drawing)) {
      for (Shape shape : ((XSSFDrawing) drawing).getShapes()) {
        if (shape instanceof Picture) {
          Picture picture = (XSSFPicture) shape;
          pair =
              new ImmutablePair<>(
                  picture.getClientAnchor().getRow1(), picture.getClientAnchor().getRow2());
          triple =
              new ImmutableTriple<>(
                  picture, this.getDimensions(sheet, picture, mergedCellsRangeAddressList), pair);

          tripleList.add(triple);
        }
      }
      pictureInputMap.put(sheetName, tripleList);
    }
  }

  @Override
  public Dimension getDimensions(
      Sheet sheet, Picture picture, List<CellRangeAddress> mergedCellsList) {
    int width = 0;
    int height = 0;

    int firstRow;
    int lastRow;
    int firstColumn;
    int lastColumn;

    Set<CellRangeAddress> mergedCellsSet = new HashSet<>(mergedCellsList);
    CellRangeAddress cellR = null;
    firstRow = picture.getClientAnchor().getRow1();
    firstColumn = picture.getClientAnchor().getCol1();

    for (CellRangeAddress cellRange : mergedCellsSet) {
      if (cellRange.isInRange(firstRow, firstColumn)) {
        cellR = cellRange;
        break;
      }
    }

    if (ObjectUtils.notEmpty(cellR)) {
      lastRow = cellR.getLastRow();
      lastColumn = cellR.getLastColumn();
      for (int i = firstRow; i <= lastRow; i++) {
        if (ObjectUtils.notEmpty(sheet.getRow(i))) height += sheet.getRow(i).getHeight() / 20f;
      }
      for (int i = firstColumn; i <= lastColumn; i++) {

        width += sheet.getColumnWidthInPixels(i);
      }
    }

    return new Dimension(width / 2f, height);
  }

  @Override
  public void resetPictureMap(
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap) {
    List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> tripleList =
        pictureInputMap.get(ExcelReportConstants.TEMPLATE_SHEET_TITLE);

    if (ObjectUtils.isEmpty(tripleList)) return;

    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> triple : tripleList) {
      triple.getLeft().getClientAnchor().setRow1(triple.getRight().getLeft());
      triple.getLeft().getClientAnchor().setRow2(triple.getRight().getRight());
    }

    if (ObjectUtils.isEmpty(tripleList)) return;

    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> triple : tripleList) {
      triple.getLeft().getClientAnchor().setRow1(triple.getRight().getLeft());
      triple.getLeft().getClientAnchor().setRow2(triple.getRight().getRight());
    }
  }

  @Override
  public void setPictureRowShiftMap(
      Map<String, List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>>
          pictureInputMap,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      String sheetName,
      String sheetType,
      int rowThreshold,
      int record,
      int totalRecord) {
    List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> pictureTripleList =
        pictureInputMap.get(sheetType);
    ClientAnchor anchor;

    if (ObjectUtils.isEmpty(pictureTripleList)) {
      return;
    }
    if (!sheetType.equalsIgnoreCase(ExcelReportConstants.TEMPLATE_SHEET_TITLE)) {
      return;
    }

    List<MutablePair<Integer, Integer>> pairList = new ArrayList<>();
    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> pictureTriple :
        pictureTripleList) {
      Picture picture = pictureTriple.getLeft();

      if (picture.getClientAnchor().getRow1() > rowThreshold) {
        anchor = pictureTriple.getLeft().getClientAnchor();
        pairList.add(MutablePair.of(anchor.getRow1(), record));
      }
    }
    fillMap(pictureRowShiftMap, sheetName, record, totalRecord, pairList);
  }

  private void fillMap(
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      String sheetName,
      int record,
      int totalRecord,
      List<MutablePair<Integer, Integer>> pairList) {
    boolean set = false;
    Integer totalRecordPerPicture = 0;
    if (pictureRowShiftMap.containsKey(sheetName)) {
      for (MutablePair<Integer, Integer> pair : pairList) {
        for (Pair<Integer, Integer> currentPair : pictureRowShiftMap.get(sheetName)) {
          if (currentPair.getKey().equals(pair.getKey())) {
            totalRecordPerPicture = currentPair.getValue() + pair.getValue();
            totalRecordPerPicture =
                (totalRecordPerPicture > totalRecord + record)
                    ? currentPair.getValue()
                    : totalRecordPerPicture;
            currentPair.setValue(totalRecordPerPicture);
            set = true;
            break;
          }
        }
        if (Boolean.FALSE.equals(set)) {
          pictureRowShiftMap.get(sheetName).add(pair);
        }
        set = false;
      }
    } else {
      pictureRowShiftMap.put(sheetName, pairList);
    }
  }

  @Override
  public int getLastPictureRow(
      List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>>
          pictureTripleList) {
    int lastPictureRow = 0;
    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> pair :
        pictureTripleList) {
      if (lastPictureRow < pair.getLeft().getClientAnchor().getRow2())
        lastPictureRow = pair.getLeft().getClientAnchor().getRow2();
    }
    return lastPictureRow;
  }

  @Override
  public void setPictureRowOffset(
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> pictureTripleList,
      int rowOffset,
      String sheetName,
      String sheetType) {
    ClientAnchor anchor;

    if (sheetType.equalsIgnoreCase(ExcelReportConstants.TEMPLATE_SHEET_TITLE)) {
      List<MutablePair<Integer, Integer>> pairList = pictureRowShiftMap.get(sheetName);
      List<MutablePair<Integer, Integer>> newPairList = new ArrayList<>();
      for (MutablePair<Integer, Integer> pair : pairList) {
        newPairList.add(MutablePair.of(pair.getLeft() + rowOffset, pair.getRight()));
      }
      pictureRowShiftMap.get(sheetName).clear();
      pictureRowShiftMap.get(sheetName).addAll(newPairList);
    }

    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> triple :
        pictureTripleList) {
      anchor = triple.getLeft().getClientAnchor();
      anchor.setRow1(anchor.getRow1() + rowOffset);
      anchor.setRow2(anchor.getRow2() + rowOffset);
    }
  }

  @Override
  public void writePictures(
      Sheet sheet,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      List<ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>>> pictureTripleList,
      String sheetType) {
    Workbook workbook = sheet.getWorkbook();
    Picture picture;

    if (ObjectUtils.isEmpty(pictureTripleList)) {
      return;
    }

    for (ImmutableTriple<Picture, Dimension, ImmutablePair<Integer, Integer>> triple :
        pictureTripleList) {
      picture = triple.getLeft();
      int pictureIndex =
          workbook.addPicture(picture.getPictureData().getData(), Workbook.PICTURE_TYPE_PNG);
      CreationHelper helper = workbook.getCreationHelper();
      Drawing<?> drawing = sheet.createDrawingPatriarch();
      ClientAnchor anchor = helper.createClientAnchor();

      anchor.setCol1(picture.getClientAnchor().getCol1());
      anchor.setCol2(picture.getClientAnchor().getCol2() + 1);
      int offset = getOffset(sheet, pictureRowShiftMap, sheetType, picture);
      anchor.setRow1(picture.getClientAnchor().getRow1() + offset);
      anchor.setRow2(picture.getClientAnchor().getRow2() + offset + 1);
      drawing.createPicture(anchor, pictureIndex);
    }
  }

  private int getOffset(
      Sheet sheet,
      Map<String, List<MutablePair<Integer, Integer>>> pictureRowShiftMap,
      String sheetType,
      Picture picture) {
    int offset = 0;
    if (sheetType.equalsIgnoreCase(ExcelReportConstants.TEMPLATE_SHEET_TITLE)) {
      int firstRow = picture.getClientAnchor().getRow1();
      Optional<MutablePair<Integer, Integer>> optionalPair =
          pictureRowShiftMap.get(sheet.getSheetName()).stream()
              .filter(p -> p.getLeft() == firstRow)
              .findFirst();
      if (optionalPair.isPresent()) offset = optionalPair.get().getRight();
    }
    return offset;
  }
}

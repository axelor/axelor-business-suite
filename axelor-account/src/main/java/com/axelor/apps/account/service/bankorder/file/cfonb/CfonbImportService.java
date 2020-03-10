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
package com.axelor.apps.account.service.bankorder.file.cfonb;

import com.axelor.apps.account.db.CfonbConfig;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.CfonbConfigService;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.tool.file.FileTool;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CfonbImportService {

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  protected CfonbConfigService cfonbConfigService;
  protected AppAccountService appAccountService;

  protected CfonbConfig cfonbConfig;
  protected List<String> importFile;

  @Inject
  public CfonbImportService(
      CfonbConfigService cfonbConfigService, AppAccountService appAccountService) {

    this.cfonbConfigService = cfonbConfigService;
    this.appAccountService = appAccountService;
  }

  protected void init(CfonbConfig cfonbConfig) {

    this.cfonbConfig = cfonbConfig;
  }

  protected void init(Company company) throws AxelorException {

    this.init(cfonbConfigService.getCfonbConfig(company));
  }

  /**
   * @param fileName
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   * @throws AxelorException
   * @throws IOException
   */
  public List<String[]> importCFONB(String fileName, Company company, int operation)
      throws AxelorException, IOException {
    return this.importCFONB(fileName, company, operation, 999);
  }

  /**
   * Récupération par lots
   *
   * @param fileName
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   * @throws AxelorException
   * @throws IOException
   */
  public Map<List<String[]>, String> importCFONBByLot(
      String fileName, Company company, int operation) throws AxelorException, IOException {
    return this.importCFONBByLot(fileName, company, operation, 999);
  }

  /**
   * @param fileName
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   * @throws AxelorException
   * @throws IOException
   */
  public List<String[]> importCFONB(
      String fileName, Company company, int operation, int optionalOperation)
      throws AxelorException, IOException {

    //		un enregistrement "en-tête" (code 31)
    // 		un enregistrement "détail" (code 34)
    // 		un enregistrement "fin" (code 39)

    this.testCompanyImportCFONBField(company);

    this.importFile = FileTool.reader(fileName);

    if (appAccountService.getAppAccount().getTransferAndDirectDebitInterbankCode() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.CFONB_IMPORT_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
    }

    String headerCFONB = null;
    List<String> multiDetailsCFONB = null;
    String endingCFONB = null;
    List<String[]> importDataList = new ArrayList<String[]>();

    // Pour chaque sequence, on récupère les enregistrements, et on les vérifie.
    // Ensuite on supprime les lignes traitées du fichier chargé en mémoire
    // Et on recommence l'opération jusqu'à ne plus avoir de ligne à traiter
    while (this.importFile != null && this.importFile.size() != 0) {
      headerCFONB = this.getHeaderCFONB(this.importFile, operation, optionalOperation);
      if (headerCFONB == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_2),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      this.importFile.remove(headerCFONB);

      multiDetailsCFONB = this.getDetailsCFONB(this.importFile, operation, optionalOperation);
      if (multiDetailsCFONB.isEmpty()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_3),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      for (String detail : multiDetailsCFONB) {
        this.importFile.remove(detail);
      }

      endingCFONB = this.getEndingCFONB(this.importFile, operation, optionalOperation);
      if (endingCFONB == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_4),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      this.importFile.remove(endingCFONB);

      this.testLength(headerCFONB, multiDetailsCFONB, endingCFONB, company);

      importDataList.addAll(
          this.getDetailDataAndCheckAmount(
              operation, headerCFONB, multiDetailsCFONB, endingCFONB, fileName));
    }
    return importDataList;
  }

  /**
   * Récupération par lots
   *
   * @param fileName
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   * @throws AxelorException
   * @throws IOException
   */
  public Map<List<String[]>, String> importCFONBByLot(
      String fileName, Company company, int operation, int optionalOperation)
      throws AxelorException, IOException {

    //		un enregistrement "en-tête" (code 31)
    // 		un enregistrement "détail" (code 34)
    // 		un enregistrement "fin" (code 39)

    this.testCompanyImportCFONBField(company);

    this.importFile = FileTool.reader(fileName);

    if (appAccountService.getAppAccount().getTransferAndDirectDebitInterbankCode() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.CFONB_IMPORT_1),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION));
    }

    String headerCFONB = null;
    List<String> multiDetailsCFONB = null;
    String endingCFONB = null;
    Map<List<String[]>, String> importDataList = new HashMap<List<String[]>, String>();

    // Pour chaque sequence, on récupère les enregistrements, et on les vérifie.
    // Ensuite on supprime les lignes traitées du fichier chargé en mémoire
    // Et on recommence l'opération jusqu'à ne plus avoir de ligne à traiter
    while (this.importFile != null && this.importFile.size() != 0) {
      headerCFONB = this.getHeaderCFONB(this.importFile, operation, optionalOperation);
      if (headerCFONB == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_2),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      this.importFile.remove(headerCFONB);

      multiDetailsCFONB = this.getDetailsCFONB(this.importFile, operation, optionalOperation);
      if (multiDetailsCFONB.isEmpty()) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_3),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      for (String detail : multiDetailsCFONB) {
        this.importFile.remove(detail);
      }

      endingCFONB = this.getEndingCFONB(this.importFile, operation, optionalOperation);
      if (endingCFONB == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            I18n.get(IExceptionMessage.CFONB_IMPORT_4),
            I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
            fileName);
      }
      this.importFile.remove(endingCFONB);

      this.testLength(headerCFONB, multiDetailsCFONB, endingCFONB, company);

      importDataList.put(
          this.getDetailDataAndCheckAmount(
              operation, headerCFONB, multiDetailsCFONB, endingCFONB, fileName),
          this.getHeaderDate(headerCFONB));
    }
    return importDataList;
  }

  protected List<String[]> getDetailDataAndCheckAmount(
      int operation,
      String headerCFONB,
      List<String> multiDetailsCFONB,
      String endingCFONB,
      String fileName)
      throws AxelorException {
    List<String[]> importDataList = new ArrayList<String[]>();
    switch (operation) {
      case 0:
        for (String detailCFONB : multiDetailsCFONB) {
          importDataList.add(this.getDetailData(detailCFONB));
        }
        this.checkTotalAmount(multiDetailsCFONB, endingCFONB, fileName, 228, 240);
        break;
      case 1:
        for (String detailCFONB : multiDetailsCFONB) {
          importDataList.add(this.getDetailData(detailCFONB));
        }
        this.checkTotalAmount(multiDetailsCFONB, endingCFONB, fileName, 228, 240);
        break;
      default:
        break;
    }
    return importDataList;
  }

  protected void checkTotalAmount(
      List<String> multiDetailsCFONB,
      String endingCFONB,
      String fileName,
      int amountPosStart,
      int amountPosEnd)
      throws AxelorException {
    int totalAmount = 0;
    for (String detailCFONB : multiDetailsCFONB) {
      totalAmount += Integer.parseInt(detailCFONB.substring(amountPosStart, amountPosEnd));
    }

    int totalRecord = Integer.parseInt(endingCFONB.substring(amountPosStart, amountPosEnd));

    log.debug(
        "Controle du montant total des enregistrement détail ({}) et du montant de l'enregistrement total ({})",
        new Object[] {totalAmount, totalRecord});

    if (totalAmount != totalRecord) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.CFONB_IMPORT_5),
          I18n.get(com.axelor.apps.base.exceptions.IExceptionMessage.EXCEPTION),
          fileName,
          endingCFONB);
    }
  }

  protected void testLength(
      String headerCFONB, List<String> multiDetailsCFONB, String endingCFONB, Company company)
      throws AxelorException {
    //		cfonbToolService.testLength(headerCFONB, 240);
    //		cfonbToolService.testLength(endingCFONB, 240);
    //		for(String detailCFONB : multiDetailsCFONB)  {
    //			cfonbToolService.testLength(detailCFONB, 240);
    //		}
  }

  /**
   * Fonction permettant de récupérer les infos de rejet d'un prélèvement ou virement
   *
   * @param detailCFONB Un enregistrement 'détail' d'un rejet de prélèvement au format CFONB
   * @return Les infos de rejet d'un prélèvement ou virement
   */
  protected String[] getDetailData(String detailCFONB) {
    String[] detailData = new String[4];
    log.debug("detailCFONB : {}", detailCFONB);

    detailData[0] = detailCFONB.substring(214, 220); // Date de rejet
    detailData[1] =
        detailCFONB.substring(152, 183).split("/")[0].trim(); // Ref prélèvement ou remboursement
    detailData[2] =
        detailCFONB.substring(228, 240).substring(0, 10)
            + "."
            + detailCFONB.substring(228, 240).substring(10); // Montant rejeté
    detailData[3] = detailCFONB.substring(226, 228); // Motif du rejet

    log.debug(
        "Obtention des données d'un enregistrement détail CFONB: Date de rejet = {}, Ref prélèvement = {}, Montant rejeté = {}, Motif du rejet = {}",
        new Object[] {detailData[0], detailData[1], detailData[2], detailData[3]});

    return detailData;
  }

  /**
   * Fonction permettant de récupérer la date de rejet de l'en-tête d'un lot de rejet de prélèvement
   * ou virement
   *
   * @param detailCFONB Un enregistrement 'détail' d'un rejet de prélèvement au format CFONB
   * @return Les infos de rejet d'un prélèvement ou virement
   */
  protected String getHeaderDate(String headerCFONB) {
    return headerCFONB.substring(10, 16);
  }

  /**
   * Procédure permettant de vérifier la conformité des champs en rapport avec les imports CFONB
   * d'une société
   *
   * @param company Une société
   * @throws AxelorException
   */
  public void testCompanyImportCFONBField(Company company) throws AxelorException {

    this.init(company);

    cfonbConfigService.getHeaderRecordCodeImportCFONB(this.cfonbConfig);
    cfonbConfigService.getDetailRecordCodeImportCFONB(this.cfonbConfig);
    cfonbConfigService.getEndingRecordCodeImportCFONB(this.cfonbConfig);
    cfonbConfigService.getTransferOperationCodeImportCFONB(this.cfonbConfig);
    cfonbConfigService.getDirectDebitOperationCodeImportCFONB(this.cfonbConfig);
  }

  /**
   * @param file
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   */
  protected String getHeaderCFONB(List<String> file, int operation, int optionalOperation) {
    String recordCode = this.getHeaderRecordCode(operation);
    String optionalRecordCode = this.getHeaderRecordCode(optionalOperation);
    String operationCode = this.getImportOperationCode(operation);
    String optionalOperationCode = this.getImportOperationCode(optionalOperation);

    log.debug(
        "Obtention enregistrement en-tête CFONB: recordCode = {}, operationCode = {}, optionalRecordCode = {}, optionalOperationCode = {}",
        new Object[] {recordCode, operationCode, optionalRecordCode, optionalOperationCode});

    for (String s : file) {
      log.debug("file line : {}", s);
      log.debug("s.substring(0, 2) : {}", s.substring(0, 2));
      if (s.substring(0, 2).equals(recordCode) || s.substring(0, 2).equals(optionalRecordCode)) {
        log.debug("s.substring(8, 10) : {}", s.substring(8, 10));
        log.debug("s.substring(2, 4) : {}", s.substring(2, 4));
        if ((s.substring(8, 10).equals(operationCode) && optionalOperation == 999)
            || s.substring(2, 4).equals(operationCode)
            || s.substring(2, 4).equals(optionalOperationCode)) {
          return s;
        }
      } else {
        break;
      }
    }
    return null;
  }

  /**
   * Fonction permettant de récupérer le code d'enregistrement en-tête
   *
   * @param company Une société
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return 999 si operation non correct
   */
  protected String getHeaderRecordCode(int operation) {
    if (operation == 0 || operation == 1 || operation == 2) {
      return this.cfonbConfig.getHeaderRecordCodeImportCFONB();
    } else if (operation == 3 || operation == 4) {
      return this.cfonbConfig.getSenderRecordCodeExportCFONB();
    }
    return "999";
  }

  /**
   * @param file
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   */
  protected List<String> getDetailsCFONB(List<String> file, int operation, int optionalOperation) {

    List<String> stringList = new ArrayList<String>();
    String recordCode = this.getDetailRecordCode(operation);
    String operationCode = this.getImportOperationCode(operation);
    String optionalRecordCode = this.getDetailRecordCode(optionalOperation);
    String optionalOperationCode = this.getImportOperationCode(optionalOperation);

    log.debug(
        "Obtention enregistrement détails CFONB: recordCode = {}, operationCode = {}, optionalRecordCode = {}, optionalOperationCode = {}",
        new Object[] {recordCode, operationCode, optionalRecordCode, optionalOperationCode});

    for (String s : file) {
      if (s.substring(0, 2).equals(recordCode) || s.substring(0, 2).equals(optionalRecordCode)) {
        if ((s.substring(8, 10).equals(operationCode) && optionalOperation == 999)
            || s.substring(2, 4).equals(operationCode)
            || s.substring(2, 4).equals(optionalOperationCode)) {
          stringList.add(s);
        }
      } else {
        break;
      }
    }

    return stringList;
  }

  /**
   * Fonction permettant de récupérer le code d'enregistrement détail
   *
   * @param company Une société
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return 999 si operation non correct
   */
  protected String getDetailRecordCode(int operation) {
    if (operation == 0 || operation == 1 || operation == 2) {
      return this.cfonbConfig.getDetailRecordCodeImportCFONB();
    } else if (operation == 3 || operation == 4) {
      return this.cfonbConfig.getRecipientRecordCodeExportCFONB();
    }
    return "999";
  }

  /**
   * @param file
   * @param company
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return
   */
  protected String getEndingCFONB(List<String> file, int operation, int optionalOperation) {
    String operationCode = this.getImportOperationCode(operation);
    String recordCode = this.getEndingRecordCode(operation);
    String optionalRecordCode = this.getEndingRecordCode(optionalOperation);
    String optionalOperationCode = this.getImportOperationCode(optionalOperation);

    log.debug(
        "Obtention enregistrement fin CFONB: recordCode = {}, operationCode = {}, optionalRecordCode = {}, optionalOperationCode = {}",
        new Object[] {recordCode, operationCode, optionalRecordCode, optionalOperationCode});
    for (String s : file) {
      if (s.substring(0, 2).equals(recordCode) || s.substring(0, 2).equals(optionalRecordCode)) {
        if ((s.substring(8, 10).equals(operationCode) && optionalOperation == 999)
            || s.substring(2, 4).equals(operationCode)
            || s.substring(2, 4).equals(optionalOperationCode)) {
          return s;
        }
      } else {
        break;
      }
    }
    return null;
  }

  /**
   * Fonction permettant de récupérer le code d'enregistrement fin
   *
   * @param company Une société
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return 999 si operation non correct
   */
  protected String getEndingRecordCode(int operation) {
    if (operation == 0 || operation == 1 || operation == 2) {
      return this.cfonbConfig.getEndingRecordCodeImportCFONB();
    } else if (operation == 3 || operation == 4) {
      return this.cfonbConfig.getTotalRecordCodeExportCFONB();
    }
    return "999";
  }

  /**
   * Méthode permettant de récupérer le code "opération" défini par société en fonction du type
   * d'opération souhaité
   *
   * @param company La société
   * @param operation Le type d'opération :
   *     <ul>
   *       <li>0 = Virement
   *       <li>1 = Prélèvement
   *     </ul>
   *
   * @return Le code opération
   */
  protected String getImportOperationCode(int operation) {
    String operationCode = "";
    switch (operation) {
      case 0:
        operationCode = this.cfonbConfig.getTransferOperationCodeImportCFONB();
        break;
      case 1:
        operationCode = this.cfonbConfig.getDirectDebitOperationCodeImportCFONB();
        break;
      default:
        break;
    }
    return operationCode;
  }
}

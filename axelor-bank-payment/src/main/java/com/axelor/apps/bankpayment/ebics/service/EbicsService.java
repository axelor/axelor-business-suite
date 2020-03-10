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
package com.axelor.apps.bankpayment.ebics.service;

import com.axelor.app.AppSettings;
import com.axelor.apps.bankpayment.db.BankOrderFileFormat;
import com.axelor.apps.bankpayment.db.EbicsPartner;
import com.axelor.apps.bankpayment.db.EbicsPartnerService;
import com.axelor.apps.bankpayment.db.EbicsRequestLog;
import com.axelor.apps.bankpayment.db.EbicsUser;
import com.axelor.apps.bankpayment.db.repo.EbicsPartnerRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsRequestLogRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsUserRepository;
import com.axelor.apps.bankpayment.ebics.client.EbicsProduct;
import com.axelor.apps.bankpayment.ebics.client.EbicsSession;
import com.axelor.apps.bankpayment.ebics.client.FileTransfer;
import com.axelor.apps.bankpayment.ebics.client.KeyManagement;
import com.axelor.apps.bankpayment.ebics.client.OrderType;
import com.axelor.apps.bankpayment.ebics.io.IOUtils;
import com.axelor.apps.bankpayment.exception.IExceptionMessage;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jdom.JDOMException;

public class EbicsService {

  @Inject private EbicsUserRepository userRepo;

  @Inject private EbicsRequestLogRepository logRepo;

  @Inject private EbicsUserService userService;

  @Inject private MetaFiles metaFiles;

  private EbicsProduct defaultProduct;

  static {
    org.apache.xml.security.Init.init();
    java.security.Security.addProvider(new BouncyCastleProvider());
  }

  @Inject
  public EbicsService() {

    AppSettings settings = AppSettings.get();
    String name = settings.get("application.name") + " " + settings.get("application.version");
    String language = settings.get("application.locale");
    String instituteID = settings.get("application.author");

    defaultProduct = new EbicsProduct(name, language, instituteID);
  }

  public String makeDN(EbicsUser ebicsUser) {

    String email = null;
    String companyName = defaultProduct.getInstituteID();
    User user = ebicsUser.getAssociatedUser();

    if (user != null) {
      email = user.getEmail();
      if (user.getActiveCompany() != null) {
        companyName = user.getActiveCompany().getName();
      }
    }

    return makeDN(ebicsUser.getName(), email, "FR", companyName);
  }

  protected String makeDN(String name, String email, String country, String organization) {

    StringBuffer buffer = new StringBuffer();
    buffer.append("CN=" + name);

    if (country != null) {
      buffer.append(", " + "C=" + country.toUpperCase());
    }
    if (organization != null) {
      buffer.append(", " + "O=" + organization);
    }
    if (email != null) {
      buffer.append(", " + "E=" + email);
    }

    return buffer.toString();
  }

  public RSAPublicKey getPublicKey(String modulus, String exponent)
      throws NoSuchAlgorithmException, InvalidKeySpecException {

    RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(modulus), new BigInteger(exponent));
    KeyFactory factory = KeyFactory.getInstance("RSA");
    RSAPublicKey pub = (RSAPublicKey) factory.generatePublic(spec);
    /*Signature verifier = Signature.getInstance("SHA1withRSA");
    verifier.initVerify(pub);
    boolean okay = verifier.verify(signature);*/

    return pub;
  }

  public RSAPrivateKey getPrivateKey(byte[] encoded)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) kf.generatePrivate(keySpec);
  }

  /**
   * Sends an INI request to the ebics bank server
   *
   * @param userId the user ID
   * @param product the application product
   * @throws AxelorException
   * @throws JDOMException
   * @throws IOException
   */
  @Transactional
  public void sendINIRequest(EbicsUser ebicsUser, EbicsProduct product) throws AxelorException {

    if (ebicsUser.getStatusSelect()
        != EbicsUserRepository.STATUS_WAITING_SENDING_SIGNATURE_CERTIFICATE) {
      return;
    }

    try {
      userService.getNextOrderId(ebicsUser);
      EbicsSession session = new EbicsSession(ebicsUser);
      if (product == null) {
        product = defaultProduct;
      }
      session.setProduct(product);

      KeyManagement keyManager = new KeyManagement(session);
      keyManager.sendINI();

      ebicsUser.setStatusSelect(EbicsUserRepository.STATUS_WAITING_AUTH_AND_ENCRYPT_CERTIFICATES);
      userRepo.save(ebicsUser);

    } catch (Exception e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }
  }

  /**
   * Sends a HIA request to the ebics server.
   *
   * @param userId the user ID.
   * @param product the application product.
   * @throws AxelorException
   */
  @Transactional
  public void sendHIARequest(EbicsUser ebicsUser, EbicsProduct product) throws AxelorException {

    if (ebicsUser.getStatusSelect()
        != EbicsUserRepository.STATUS_WAITING_AUTH_AND_ENCRYPT_CERTIFICATES) {
      return;
    }
    userService.getNextOrderId(ebicsUser);

    EbicsSession session = new EbicsSession(ebicsUser);
    if (product == null) {
      product = defaultProduct;
    }
    session.setProduct(product);
    KeyManagement keyManager = new KeyManagement(session);

    try {
      keyManager.sendHIA();
      ebicsUser.setStatusSelect(EbicsUserRepository.STATUS_ACTIVE_CONNECTION);
      userRepo.save(ebicsUser);
    } catch (IOException | AxelorException | JDOMException e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }
  }

  /**
   * Sends a HPB request to the ebics server.
   *
   * @param userId the user ID.
   * @param product the application product.
   * @throws AxelorException
   */
  @Transactional
  public X509Certificate[] sendHPBRequest(EbicsUser user, EbicsProduct product)
      throws AxelorException {

    EbicsSession session = new EbicsSession(user);
    if (product == null) {
      product = defaultProduct;
    }
    session.setProduct(product);

    KeyManagement keyManager = new KeyManagement(session);
    try {
      return keyManager.sendHPB();
    } catch (Exception e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }
  }

  /**
   * Sends the SPR order to the bank.
   *
   * @param userId the user ID
   * @param product the session product
   * @throws AxelorException
   */
  @Transactional
  public void sendSPRRequest(EbicsUser ebicsUser, EbicsProduct product) throws AxelorException {

    EbicsSession session = new EbicsSession(ebicsUser);
    if (product == null) {
      product = defaultProduct;
    }
    session.setProduct(product);

    KeyManagement keyManager = new KeyManagement(session);
    try {
      keyManager.lockAccess();
      ebicsUser.setStatusSelect(EbicsUserRepository.STATUS_WAITING_SENDING_SIGNATURE_CERTIFICATE);
      userService.getNextOrderId(ebicsUser);
      userRepo.save(ebicsUser);
    } catch (Exception e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }
  }

  /**
   * Send a file to the EBICS bank server.
   *
   * @param transportUser
   * @param signatoryUser
   * @param product
   * @param file
   * @param format
   * @param signature
   * @throws AxelorException
   */
  public void sendFULRequest(
      EbicsUser transportUser,
      EbicsUser signatoryUser,
      EbicsProduct product,
      File file,
      BankOrderFileFormat format,
      File signature)
      throws AxelorException {
    Preconditions.checkNotNull(transportUser);
    Preconditions.checkNotNull(transportUser.getEbicsPartner());
    Preconditions.checkNotNull(format);
    List<EbicsPartnerService> ebicsPartnerServiceList =
        transportUser.getEbicsPartner().getBoEbicsPartnerServiceList();
    String ebicsCodification;

    if (ebicsPartnerServiceList == null || ebicsPartnerServiceList.isEmpty()) {
      ebicsCodification = format.getOrderFileFormatSelect();
    } else {
      ebicsCodification = findEbicsCodification(transportUser.getEbicsPartner(), format);
    }

    sendFULRequest(transportUser, signatoryUser, product, file, ebicsCodification, signature);
  }

  protected String findEbicsCodification(EbicsPartner ebicsPartner, BankOrderFileFormat format)
      throws AxelorException {
    Preconditions.checkNotNull(ebicsPartner);
    Preconditions.checkNotNull(format);

    if (ebicsPartner.getBoEbicsPartnerServiceList() != null) {
      for (EbicsPartnerService service : ebicsPartner.getBoEbicsPartnerServiceList()) {
        if (format.equals(service.getBankOrderFileFormat())) {
          return service.getEbicsCodification();
        }
      }
    }

    throw new AxelorException(
        I18n.get(IExceptionMessage.EBICS_NO_SERVICE_CONFIGURED),
        TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
        ebicsPartner.getPartnerId(),
        format.getName());
  }

  /**
   * Sends a file to the ebics bank sever
   *
   * @param path the file path to send
   * @param userId the user ID that sends the file.
   * @param product the application product.
   * @throws AxelorException
   */
  protected void sendFULRequest(
      EbicsUser transportUser,
      EbicsUser signatoryUser,
      EbicsProduct product,
      File file,
      String format,
      File signature)
      throws AxelorException {

    EbicsSession session = new EbicsSession(transportUser, signatoryUser);
    boolean test = isTest(transportUser);
    if (test) {
      session.addSessionParam("TEST", "true");
    }
    if (file == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR, "File is required to send FUL request");
    }
    EbicsPartner ebicsPartner = transportUser.getEbicsPartner();

    if (ebicsPartner.getEbicsTypeSelect() == EbicsPartnerRepository.EBICS_TYPE_TS) {
      if (signature == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            "Signature file is required to send FUL request");
      }
      if (signatoryUser == null) {
        throw new AxelorException(
            TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
            "Signatory user is required to send FUL request");
      }
    }

    session.addSessionParam("EBCDIC", "false");
    session.addSessionParam("FORMAT", format);

    if (product == null) {
      product = defaultProduct;
    }
    session.setProduct(product);
    FileTransfer transferManager = new FileTransfer(session);

    try {
      if (ebicsPartner.getEbicsTypeSelect() == EbicsPartnerRepository.EBICS_TYPE_TS) {
        transferManager.sendFile(
            IOUtils.getFileContent(file.getAbsolutePath()),
            OrderType.FUL,
            IOUtils.getFileContent(signature.getAbsolutePath()));
      } else {
        transferManager.sendFile(
            IOUtils.getFileContent(file.getAbsolutePath()), OrderType.FUL, null);
      }
      userService.getNextOrderId(transportUser);
    } catch (IOException | AxelorException e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }

    try {
      if (ebicsPartner.getUsePSR()) {
        sendFDLRequest(
            transportUser,
            product,
            null,
            null,
            ebicsPartner.getpSRBankStatementFileFormat().getStatementFileFormatSelect());
      }
    } catch (AxelorException e) {
      TraceBackService.trace(e);
    }
  }

  public File sendFDLRequest(
      EbicsUser user, EbicsProduct product, Date start, Date end, String fileFormat)
      throws AxelorException {

    return fetchFile(OrderType.FDL, user, product, start, end, fileFormat);
  }

  public File sendHTDRequest(EbicsUser user, EbicsProduct product, Date start, Date end)
      throws AxelorException {

    return fetchFile(OrderType.HTD, user, product, start, end, null);
  }

  public File sendPTKRequest(EbicsUser user, EbicsProduct product, Date start, Date end)
      throws AxelorException {

    return fetchFile(OrderType.PTK, user, product, start, end, null);
  }

  public File sendHPDRequest(EbicsUser user, EbicsProduct product, Date start, Date end)
      throws AxelorException {

    return fetchFile(OrderType.HPD, user, product, start, end, null);
  }

  protected File fetchFile(
      OrderType orderType,
      EbicsUser user,
      EbicsProduct product,
      Date start,
      Date end,
      String fileFormat)
      throws AxelorException {

    EbicsSession session = new EbicsSession(user);
    File file = null;
    try {
      boolean test = isTest(user);
      if (test) {
        session.addSessionParam("TEST", "true");
      }
      if (fileFormat != null) {
        session.addSessionParam("FORMAT", fileFormat);
      }
      if (product == null) {
        product = defaultProduct;
      }
      session.setProduct(product);

      FileTransfer transferManager = new FileTransfer(session);

      file = File.createTempFile(user.getName(), "." + orderType.getOrderType());
      transferManager.fetchFile(orderType, start, end, new FileOutputStream(file));

      addResponseFile(user, file);

      userService.getNextOrderId(user);

    } catch (AxelorException e) {
      TraceBackService.trace(e);
      throw e;
    } catch (IOException e) {
      TraceBackService.trace(e);
      throw new AxelorException(e, TraceBackRepository.TYPE_TECHNICAL);
    }

    return file;
  }

  protected boolean isTest(EbicsUser user) throws AxelorException {

    EbicsPartner partner = user.getEbicsPartner();

    return partner.getTestMode();
  }

  @Transactional
  public void addResponseFile(EbicsUser user, File file) throws IOException {

    EbicsRequestLog requestLog =
        logRepo.all().filter("self.ebicsUser = ?1", user).order("-id").fetchOne();
    if (requestLog != null && file != null && file.length() > 0) {
      requestLog.setResponseFile(metaFiles.upload(file));
      logRepo.save(requestLog);
    }
  }
}

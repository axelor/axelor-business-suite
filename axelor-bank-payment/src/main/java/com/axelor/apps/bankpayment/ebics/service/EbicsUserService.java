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

import com.axelor.apps.bankpayment.db.EbicsPartner;
import com.axelor.apps.bankpayment.db.EbicsRequestLog;
import com.axelor.apps.bankpayment.db.EbicsUser;
import com.axelor.apps.bankpayment.db.repo.EbicsRequestLogRepository;
import com.axelor.apps.bankpayment.db.repo.EbicsUserRepository;
import com.axelor.apps.bankpayment.ebics.client.EbicsRootElement;
import com.axelor.apps.bankpayment.ebics.client.EbicsUtils;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.LocalDateTime;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jdom.JDOMException;

public class EbicsUserService {

  @Inject private EbicsService ebicsService;

  @Inject private EbicsRequestLogRepository requestLogRepo;

  @Inject private EbicsUserRepository ebicsUserRepo;

  /**
   * EBICS Specification 2.4.2 - 14.1 Version A005/A006 of the electronic signature:
   *
   * <p>For the signature processes A005 an interval of 1536 bit (minimum) and 4096 bit (maximum) is
   * defined for the key length.
   *
   * <p>The digital signature mechanisms A005 is both based on the industry standard [PKCS1] using
   * the hash algorithm SHA-256. They are both signature mechanisms without message recovery.
   *
   * <p>A hash algorithm maps bit sequences of arbitrary length (input bit sequences) to byte
   * sequences of a fixed length, determined by the Hash algorithm. The result of the execution of a
   * Hash algorithm to a bit sequence is defined as hash value.
   *
   * <p>The hash algorithm SHA-256 is specified in [FIPS H2]. SHA-256 maps input bit sequences of
   * arbitrary length to byte sequences of 32 byte length. The padding of input bit sequences to a
   * length being a multiple of 64 byte is part of the hash algorithm. The padding even is applied
   * if the input bit sequence already has a length that is a multiple of 64 byte.
   *
   * <p>SHA-256 processes the input bit sequences in blocks of 64 byte length. The hash value of a
   * bit sequence x under the hash algorithm SHA-256 is referred to as follows: SHA-256(x).
   *
   * <p>The digital signature mechanism A005 is identical to EMSA-PKCS1-v1_5 using the hash
   * algorithm SHA-256. The byte length H of the hash value is 32.
   *
   * <p>According [PKCS1] (using the method EMSA-PKCS1-v1_5) the following steps shall be performed
   * for the computation of a signature for message M with bit length m.
   *
   * <ol>
   *   <li>The hash value HASH(M) of the byte length H shall be computed. In the case of A005
   *       SHA-256(M) with a length of 32 bytes.
   *   <li>The DSI for the signature algorithm shall be generated.
   *   <li>A signature shall be computed using the DSI with the standard algorithm for the signature
   *       generation described in section 14.1.3.1 of the EBICS specification (V 2.4.2).
   * </ol>
   *
   * <p>The {@link Signature} is a digital signature scheme with appendix (SSA) combining the RSA
   * algorithm with the EMSA-PKCS1-v1_5 encoding method.
   *
   * <p>The {@code digest} will be signed with the RSA user signature key using the {@link
   * Signature} that will be instantiated with the <b>SHA-256</b> algorithm. This signature is then
   * put in a {@link UserSignature} XML object that will be sent to the EBICS server.
   */
  public byte[] sign(EbicsUser ebicsUser, byte[] digest)
      throws IOException, GeneralSecurityException {

    Signature signature =
        Signature.getInstance("SHA256WithRSA", BouncyCastleProvider.PROVIDER_NAME);
    signature.initSign(ebicsService.getPrivateKey(ebicsUser.getA005Certificate().getPrivateKey()));
    signature.update(removeOSSpecificChars(digest));
    return signature.sign();
  }

  /**
   * EBICS Specification 2.4.2 - 11.1.1 Process:
   *
   * <p>Identification and authentication signatures are based on the RSA signature process. The
   * following parameters determine the identification and authentication signature process:
   *
   * <ol>
   *   <li>Length of the (secret) RSA key
   *   <li>Hash algorithm
   *   <li>Padding process
   *   <li>Canonisation process.
   * </ol>
   *
   * <p>For the identification and authentication process, EBICS defines the process “X002” with the
   * following parameters:
   *
   * <ol>
   *   <li>Key length in Kbit >=1Kbit (1024 bit) and lesser than 16Kbit
   *   <li>Hash algorithm SHA-256
   *   <li>Padding process: PKCS#1
   *   <li>Canonisation process: http://www.w3.org/TR/2001/REC-xml-c14n-20010315
   * </ol>
   *
   * <p>From EBICS 2.4 on, the customer system must use the hash value of the public bank key X002
   * in a request.
   *
   * <p>Notes:
   *
   * <ol>
   *   <li>The key length is defined else where.
   *   <li>The padding is performed by the {@link Signature} class.
   *   <li>The digest is already canonized in the {@link SignedInfo#sign(byte[]) sign(byte[])}
   * </ol>
   */
  public byte[] authenticate(EbicsUser ebicsUser, byte[] digest) throws GeneralSecurityException {
    Signature signature;
    signature = Signature.getInstance("SHA256WithRSA", BouncyCastleProvider.PROVIDER_NAME);
    signature.initSign(ebicsService.getPrivateKey(ebicsUser.getX002Certificate().getPrivateKey()));
    signature.update(digest);
    return signature.sign();
  }

  /**
   * EBICS Specification 2.4.2 - 7.1 Process description:
   *
   * <p>In particular, so-called “white-space characters” such as spaces, tabs, carriage returns and
   * line feeds (“CR/LF”) are not permitted.
   *
   * <p>All white-space characters should be removed from entry buffer {@code buf}.
   *
   * @param buf the given byte buffer
   * @param offset the offset
   * @param length the length
   * @return The byte buffer portion corresponding to the given length and offset
   */
  public static byte[] removeOSSpecificChars(byte[] content) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (byte b : content) {
      switch (b) {
        case '\r':
        case '\n':
        case 0x1A: // CTRL-Z / EOF
          // ignore this characters
          break;
        default:
          output.write(b);
      }
    }
    return output.toByteArray();
  }

  /**
   * EBICS IG CFONB VF 2.1.4 2012 02 24 - 2.1.3.2 Calcul de la signature:
   *
   * <p>Il convient d’utiliser PKCS1 V1.5 pour chiffrer la clé de chiffrement.
   *
   * <p>EBICS Specification 2.4.2 - 15.2 Workflows at the recipient’s end:
   *
   * <p><b>Decryption of the DES key</b>
   *
   * <p>The leading 256 null bits of the EDEK are removed and the remaining 768 bits are decrypted
   * with the recipient’s secret key of the RSA key system. PDEK is then present. The secret DES key
   * DEK is obtained from the lowest-value 128 bits of PDEK, this is split into the individual keys
   * DEK<SUB>left</SUB> and DEK<SUB>right</SUB>.
   */
  public byte[] decrypt(EbicsUser user, byte[] encryptedData, byte[] transactionKey)
      throws AxelorException, GeneralSecurityException, IOException {
    Cipher cipher;
    int blockSize;
    ByteArrayOutputStream outputStream;

    cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding", BouncyCastleProvider.PROVIDER_NAME);
    cipher.init(
        Cipher.DECRYPT_MODE, ebicsService.getPrivateKey(user.getE002Certificate().getPrivateKey()));
    blockSize = cipher.getBlockSize();
    outputStream = new ByteArrayOutputStream();
    for (int j = 0; j * blockSize < transactionKey.length; j++) {
      outputStream.write(cipher.doFinal(transactionKey, j * blockSize, blockSize));
    }

    return decryptData(encryptedData, outputStream.toByteArray());
  }

  /**
   * Decrypts the <code>encryptedData</code> using the decoded transaction key.
   *
   * <p>EBICS Specification 2.4.2 - 15.2 Workflows at the recipient’s end:
   *
   * <p><b>Decryption of the message</b>
   *
   * <p>The encrypted original message is decrypted in CBC mode in accordance with the 2-key triple
   * DES process via the secret DES key (comprising DEK<SUB>left</SUB> and DEK<SUP>right<SUB>). In
   * doing this, the following initialization value ICV is again used.
   *
   * <p><b>Removal of the padding information</b>
   *
   * <p>The method “Padding with Octets” according to ANSI X9.23 is used to remove the padding
   * information from the decrypted message. The original message is then available in decrypted
   * form.
   *
   * @param input The encrypted data
   * @param key The secret key.
   * @return The decrypted data sent from the EBICS bank.
   * @throws GeneralSecurityException
   * @throws IOException
   */
  protected byte[] decryptData(byte[] input, byte[] key) throws AxelorException {
    return EbicsUtils.decrypt(input, new SecretKeySpec(key, "EAS"));
  }

  @Transactional
  public void logRequest(
      long ebicsUserId, String requestType, String responseCode, EbicsRootElement[] rootElements) {

    EbicsRequestLog requestLog = new EbicsRequestLog();
    requestLog.setEbicsUser(ebicsUserRepo.find(ebicsUserId));
    LocalDateTime time = LocalDateTime.now();
    requestLog.setRequestTime(time);
    requestLog.setRequestType(requestType);
    requestLog.setResponseCode(responseCode);

    try {
      trace(requestLog, rootElements);
    } catch (Exception e) {
      e.printStackTrace();
    }

    requestLogRepo.save(requestLog);
  }

  @Transactional
  public String getNextOrderId(EbicsUser user) throws AxelorException {

    String orderId = user.getNextOrderId();

    if (orderId == null) {
      EbicsPartner partner = user.getEbicsPartner();
      EbicsUser otherUser =
          ebicsUserRepo
              .all()
              .filter(
                  "self.ebicsPartner = ?1 and self.id != ?2 and self.nextOrderId != null",
                  partner,
                  user.getId())
              .order("-nextOrderId")
              .fetchOne();

      char firstLetter = 'A';
      if (otherUser != null) {
        String otherOrderId = otherUser.getNextOrderId();
        firstLetter = otherOrderId.charAt(0);
        firstLetter++;
      }

      orderId = String.valueOf(firstLetter) + "000";
      user.setNextOrderId(orderId);
      ebicsUserRepo.save(user);
    } else {
      orderId = getNextOrderNumber(orderId);
      user.setNextOrderId(orderId);
      ebicsUserRepo.save(user);
    }

    return orderId;
  }

  public String getNextOrderNumber(String orderId) throws AxelorException {

    if (Strings.isNullOrEmpty(orderId) || orderId.matches("[^a-z0-9 ]") || orderId.length() != 4) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get("Invalid order id \"%s\""), orderId);
    }

    if (orderId.substring(1).equals("ZZZ")) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD, I18n.get("Maximum order limit reach"));
    }

    char[] orderIds = orderId.toCharArray();

    if (orderIds[3] != 'Z') {
      orderIds[3] = getNextChar(orderIds[3]);
    } else {
      orderIds[3] = '0';
      if (orderIds[2] != 'Z') {
        orderIds[2] = getNextChar(orderIds[2]);
      } else {
        orderIds[2] = '0';
        if (orderIds[1] != 'Z') {
          orderIds[1] = getNextChar(orderIds[1]);
        }
      }
    }

    return new String(orderIds);
  }

  protected char getNextChar(char c) {

    if (c == '9') {
      return 'A';
    }

    return (char) ((int) c + 1);
  }

  protected void trace(EbicsRequestLog requestLog, EbicsRootElement[] rootElements)
      throws AxelorException, JDOMException, IOException {

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    rootElements[0].save(bout);
    requestLog.setRequestTraceText(bout.toString());
    bout.close();

    bout = new ByteArrayOutputStream();
    rootElements[1].save(bout);
    requestLog.setResponseTraceText(bout.toString());
    bout.close();
  }
}

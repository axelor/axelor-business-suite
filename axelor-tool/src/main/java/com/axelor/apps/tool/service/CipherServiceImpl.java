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
package com.axelor.apps.tool.service;

import com.axelor.app.AppSettings;
import com.axelor.common.StringUtils;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;

public class CipherServiceImpl implements CipherService {

  private static final String UNICODE_FORMAT = "UTF8";
  public static final String DESEDE_ENCRYPTION_SCHEME = "DESede";
  private Cipher cipher;

  @Override
  public String encrypt(String unencryptedString) {
    String encryptedString = null;
    try {

      SecretKey key = this.initEncryptOrDecrypt();

      if (key != null) {
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] plainText = unencryptedString.getBytes(UNICODE_FORMAT);
        byte[] encryptedText = cipher.doFinal(plainText);
        encryptedString = Base64.getEncoder().encodeToString(encryptedText);
      } else {
        return unencryptedString;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return encryptedString;
  }

  @Override
  public String decrypt(String encryptedString) {
    String decryptedText = null;
    try {

      SecretKey key = this.initEncryptOrDecrypt();

      if (key != null) {
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] encryptedText = Base64.getDecoder().decode(encryptedString);
        byte[] plainText = cipher.doFinal(encryptedText);
        decryptedText = new String(plainText);
      } else {
        return encryptedString;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return decryptedText;
  }

  protected SecretKey initEncryptOrDecrypt()
      throws UnsupportedEncodingException, InvalidKeyException, NoSuchAlgorithmException,
          NoSuchPaddingException, InvalidKeySpecException {

    String encryptionScheme = DESEDE_ENCRYPTION_SCHEME;
    String encryptionkey = AppSettings.get().get("application.encryptionkey");
    SecretKey key = null;

    if (StringUtils.notEmpty(encryptionkey)) {
      byte[] arrayBytes = encryptionkey.getBytes(UNICODE_FORMAT);
      KeySpec ks = new DESedeKeySpec(arrayBytes);
      SecretKeyFactory skf = SecretKeyFactory.getInstance(encryptionScheme);
      cipher = Cipher.getInstance(encryptionScheme);
      key = skf.generateSecret(ks);
    }
    return key;
  }
}

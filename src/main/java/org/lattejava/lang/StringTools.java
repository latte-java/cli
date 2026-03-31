/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.lang;

/**
 * This is a toolkit with String helper methods.
 *
 * @author Brian Pontarelli
 */
public class StringTools {
  /**
   * Converts the contents of the given String from hexadecimal to an array of bytes. Each character of the String is a
   * single hex value. Therefore, the pair of characters equals a single byte. This method is little-endian.
   *
   * @param hexString The hex String to convert
   * @return An array of bytes
   */
  public static byte[] fromHex(String hexString) {
    int length = hexString.length();

    if ((length & 0x01) != 0) {
      throw new IllegalArgumentException("odd number of characters.");
    }

    byte[] out = new byte[length >> 1];

    // two characters form the hex value.
    for (int i = 0, j = 0; j < length; i++) {
      int f = Character.digit(hexString.charAt(j++), 16) << 4;
      f = f | Character.digit(hexString.charAt(j++), 16);
      out[i] = (byte) (f & 0xFF);
    }

    return out;
  }

  /**
   * Converts the contents of the given byte array from hexadecimal to a hex String. Each character of the String is a
   * single hex value. Therefore, the the pair of characters equals a single byte.
   *
   * @param hexBytes The hex byte array to convert.
   * @return A hex String.
   */
  public static String toHex(byte... hexBytes) {
    StringBuilder build = new StringBuilder();
    for (byte hexByte : hexBytes) {
      byte b1 = (byte) ((hexByte & 0xF0) >> 4);
      byte b2 = (byte) (hexByte & 0x0F);
      char c1 = Character.forDigit(b1, 16);
      char c2 = Character.forDigit(b2, 16);
      build.append(c1);
      build.append(c2);
    }

    return build.toString();
  }
}

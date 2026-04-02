/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
package org.lattejava.output;

/**
 * Basic Latte output system. This writes to Appendables.
 *
 * @author Brian Pontarelli
 */
public interface Output {
  /**
   * Outputs a debug message along with the given values. This uses printf if there are values and DOES NOT append a
   * newline character to the end of message. If there are no values, this outputs the message via print.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output debug(String message, Object... values);

  /**
   * Outputs a debug message along with the given values. This uses printf if there are values and appends a newline
   * character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output debugln(String message, Object... values);

  /**
   * Outputs a debug message with the stack trace for the given Throwable.
   *
   * @param t The Throwable to output.
   * @return This.
   */
  Output debug(Throwable t);

  /**
   * Disables debug messages.
   *
   * @return This.
   */
  Output disableDebug();

  /**
   * Enables debug messages.
   *
   * @return This.
   */
  Output enableDebug();

  /**
   * Outputs an error message along with the given values. This uses printf if there are values and DOES NOT append a
   * newline character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output error(String message, Object... values);

  /**
   * Outputs an error message along with the given values. This uses printf if there are values and appends a newline
   * character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output errorln(String message, Object... values);

  /**
   * Outputs an info message along with the given values. This uses printf if there are values and DOES NOT append a
   * newline character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output info(String message, Object... values);

  /**
   * Outputs an info message along with the given values. This uses printf if there are values and appends a newline
   * character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output infoln(String message, Object... values);

  /**
   * Outputs an info message along with the given values. This uses printf if there are values and appends a newline
   * character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param color The ANSI 256 color for the entire message.
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output infoln(int color, String message, Object... values);

  /**
   * Outputs a warning message along with the given values. This uses printf if there are values and DOES NOT append a
   * newline character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output warning(String message, Object... values);

  /**
   * Outputs a warning message along with the given values. This uses printf if there are values and appends a newline
   * character to the end of message. If there are no values, this outputs the message via println.
   *
   * @param message The message.
   * @param values  Values for the message.
   * @return This.
   */
  Output warningln(String message, Object... values);
}

/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.database

import org.lattejava.cli.domain.Project

/**
 * Settings for the database plugin.
 *
 * @author Brian Pontarelli
 */
class DatabaseSettings {
  /**
   * The database type (MySQL or PostgreSQL). This is not case-sensitive.
   */
  String type

  /**
   * The name of the database to create or use. Defaults to the name of the project.
   */
  String name

  /**
   * The host where the database is running. Defaults to `localhost`
   */
  String host = "127.0.0.1"

  /**
   * Additional arguments passed to the create statement. Defaults to an empty String.
   */
  String createArguments = ""

  /**
   * String that is appended to the create statement. Defaults to an empty String.
   */
  String createSuffix = ""

  /**
   * The username used to create to the database. The password is configured using either the MySQL options file or the
   * PostgreSQL pgpass file. Defaults to {@code root} for MySQL and {@code postgres} for PostgreSQL.
   */
  String createUsername

  /**
   * The username used to compare databases. Defaults to {@code dev}.
   */
  String compareUsername = "dev"

  /**
   * The password used to compare databases. Defaults to {@code dev}.
   */
  String comparePassword = "dev"

  /**
   * Additional arguments passed to the execute statement. Defaults to an empty String.
   */
  String executeArguments = ""

  /**
   * The username used when executing scripts. Defaults to {@code dev}.
   */
  String executeUsername = "dev"

  /**
   * The password used when executing scripts. Defaults to {@code dev}.
   */
  String executePassword = "dev"

  /**
   * A username to grant all privileges on a newly created database. Defaults to {@code dev}.
   */
  String grantUsername = "dev"

  /**
   * The password used for the user grant on a newly created database. Defaults to {@code dev}.
   */
  String grantPassword = "dev"

  DatabaseSettings(Project project) {
    name = project.name.replace("-", "_")
  }

  DatabaseSettings(String type, String name, String createArguments, String createUsername, String grantUsername, String grantPassword) {
    this.type = type
    this.name = name
    this.createArguments = createArguments
    this.createUsername = createUsername
    this.grantPassword = grantPassword
    this.grantUsername = grantUsername
  }
}

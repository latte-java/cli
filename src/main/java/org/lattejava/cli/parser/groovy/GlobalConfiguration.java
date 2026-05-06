/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser.groovy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.util.LattePaths;

import groovy.lang.GroovyObjectSupport;

/**
 * This class loads an optional global configuration file named config.properties in the XDG config directory
 * ($XDG_CONFIG_HOME/latte/ or ~/.config/latte/). This is a dynamic Groovy object that fails if lookups fail. This
 * ensures that values from the configuration that the project depends on exist.
 *
 * @author Brian Pontarelli
 */
public class GlobalConfiguration extends GroovyObjectSupport {
  public final Properties properties = new Properties();

  public GlobalConfiguration() {
    Path configFile = LattePaths.get().configDir().resolve("config.properties");
    if (Files.isRegularFile(configFile)) {
      try (InputStream is = Files.newInputStream(configFile)) {
        properties.load(is);
      } catch (IOException e) {
        throw new RuntimeFailureException("Unable to load global configuration file " + configFile, e);
      }
    }
  }

  @Override
  public Object getProperty(String property) {
    String value = properties.getProperty(property);
    if (value == null) {
      Path configFile = LattePaths.get().configDir().resolve("config.properties");
      throw new RuntimeFailureException("Missing global configuration property [" + property + "]. You must define this " +
          "property in the global configuration file " + configFile);
    }

    return value;
  }

  @Override
  public void setProperty(String property, Object newValue) {
    throw new RuntimeFailureException("You attempted to set the property [" + property + "] to the value [" + newValue +
        "]. You cannot set/change global configuration properties from a project file.");
  }
}

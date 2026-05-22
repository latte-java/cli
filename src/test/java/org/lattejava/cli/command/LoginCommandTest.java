/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

import org.lattejava.cli.auth.*;
import org.lattejava.cli.runtime.*;
import org.lattejava.output.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * End-to-end test that runs the real {@link LoginCommand} against a local FusionAuth instance running in Docker. The
 * interactive browser step is driven over HTTP by {@link FusionAuthBrowser} instead of opening a window, and tokens are
 * written to a temporary file rather than the user's real configuration.
 * <p>
 * Requires FusionAuth running locally on port 9011. Start it with:
 * <pre>
 *   cd src/test/fusionauth &amp;&amp; docker compose up -d
 * </pre>
 *
 * @author Brian Pontarelli
 */
@Test
public class LoginCommandTest {
  private static final String ISSUER = "http://localhost:9011";

  @BeforeClass
  public void beforeClass() {
    try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder(URI.create(ISSUER + "/api/status")).GET().timeout(Duration.ofSeconds(2)).build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("FusionAuth returned HTTP [" + response.statusCode() + "]");
      }
    } catch (Exception e) {
      throw new RuntimeException("""
          FusionAuth is not running on http://localhost:9011. Start it with:
          
            cd src/test/fusionauth && docker compose up -d
          """, e);
    }
  }

  @Test
  public void loginStoresTokensForOrdinaryUser() throws Exception {
    Path configFile = Files.createTempDirectory("latte-login-test").resolve("config.properties");
    RuntimeConfiguration configuration = new RuntimeConfiguration();
    configuration.args.add(ISSUER);
    Output output = new SystemOutOutput(false);

    LoginCommand command = new LoginCommand(new FusionAuthBrowser("test@lattejava.org", "password"), configFile);
    command.run(configuration, output, null);

    Properties properties = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      properties.load(is);
    }

    String accessToken = properties.getProperty("latte.auth.accessToken");
    assertNotNull(accessToken, "Access token should have been stored");
    assertNotNull(properties.getProperty("latte.auth.refreshToken"), "Refresh token should have been stored");
    assertEquals(JWTs.claim(accessToken, "email"), "test@lattejava.org");
  }

  /**
   * A {@link Browser} that completes the FusionAuth login over HTTP: it fetches the authorize page, submits the login
   * form with the given credentials, and follows the resulting redirects (login &rarr; consent &rarr; the loopback
   * callback), which delivers the authorization code to the loopback server. This replaces the human-driven browser
   * step so the flow can run unattended.
   */
  static final class FusionAuthBrowser implements Browser {
    private static final Pattern FORM_ACTION = Pattern.compile("<form\\b[^>]*\\baction=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_TAG = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_ATTR = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern VALUE_ATTR = Pattern.compile("\\bvalue=\"([^\"]*)\"");

    private final String loginId;
    private final String password;

    FusionAuthBrowser(String loginId, String password) {
      this.loginId = loginId;
      this.password = password;
    }

    private static String formEncode(Map<String, String> fields) {
      StringBuilder builder = new StringBuilder();
      fields.forEach((name, value) -> {
        if (!builder.isEmpty()) {
          builder.append('&');
        }
        builder.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
               .append('=')
               .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
      });
      return builder.toString();
    }

    private static Map<String, String> hiddenInputs(String html) {
      Map<String, String> fields = new LinkedHashMap<>();
      Matcher inputMatcher = INPUT_TAG.matcher(html);
      while (inputMatcher.find()) {
        String tag = inputMatcher.group();
        if (!tag.contains("type=\"hidden\"")) {
          continue;
        }
        Matcher nameMatcher = NAME_ATTR.matcher(tag);
        if (!nameMatcher.find()) {
          continue;
        }
        Matcher valueMatcher = VALUE_ATTR.matcher(tag);
        fields.put(nameMatcher.group(1), valueMatcher.find() ? valueMatcher.group(1) : "");
      }
      return fields;
    }

    @Override
    public void open(String url, Output output) {
      CookieManager cookieManager = new CookieManager();
      cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

      try (HttpClient client = HttpClient.newBuilder()
                                         .cookieHandler(cookieManager)
                                         .followRedirects(HttpClient.Redirect.NORMAL)
                                         .connectTimeout(Duration.ofSeconds(10))
                                         .build()) {
        // Fetch the hosted login page and scrape its form.
        HttpResponse<String> page = client.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        String html = page.body();

        Matcher actionMatcher = FORM_ACTION.matcher(html);
        if (!actionMatcher.find()) {
          throw new RuntimeException("Could not find the login form in the FusionAuth page.");
        }
        URI postURI = URI.create(url).resolve(actionMatcher.group(1));

        // Carry forward every hidden field FusionAuth embedded (OAuth context, tenant, etc.) and add the credentials.
        Map<String, String> fields = hiddenInputs(html);
        fields.put("loginId", loginId);
        fields.put("password", password);

        // Submit the form. The client follows login -> consent -> the loopback callback automatically.
        client.send(
            HttpRequest.newBuilder(postURI)
                       .header("Content-Type", "application/x-www-form-urlencoded")
                       .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
                       .build(),
            HttpResponse.BodyHandlers.ofString()
        );
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new RuntimeException("Headless FusionAuth login failed. Message was [" + e.getMessage() + "]", e);
      }
    }
  }
}

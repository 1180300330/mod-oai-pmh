package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.oaipmh.ResponseHelper;
import org.folio.rest.resource.interfaces.InitAPI;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

/**
 * The class initializes system properties and checks if required configs are specified
 */
public class InitAPIs implements InitAPI {
  private final Logger logger = LoggerFactory.getLogger(InitAPIs.class);

  private static final String CONFIG_PATH = "config" + File.separator + "config.properties";

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> resultHandler) {
    String configPath = System.getProperty("configPath", CONFIG_PATH);
    try (InputStream configFile = InitAPIs.class.getClassLoader().getResourceAsStream(configPath)) {
      if (configFile == null) {
        throw new IllegalStateException(String.format("The config file %s is missing.", configPath));
      }
      final Properties confProperties = new Properties();
      confProperties.load(configFile);

      // Set system property from config file if no specified at runtime
      Properties sysProps = System.getProperties();
      confProperties.forEach((key, value) -> {
        Object existing = sysProps.putIfAbsent(key, value);
        if (logger.isInfoEnabled()) {
          String message = (existing == null) ?
            String.format("The '%s' property was loaded from config file with its default value '%s'", key, value) :
            String.format("The '%s' system property has '%s' value. The default '%s' value from config file is ignored.", key, existing, value);
          logger.info(message);
        }
      });

      // Initialize ResponseWriter and check if jaxb marshaller is ready to operate
      if (!ResponseHelper.getInstance().isJaxbInitialized()) {
        throw new IllegalStateException("The jaxb marshaller failed initialization.");
      }

      OaiPmhImpl.init(resultHandler);
    } catch (Exception e) {
      resultHandler.handle(Future.failedFuture(e));
      logger.error("Unable to populate system properties", e);
    }
  }


}

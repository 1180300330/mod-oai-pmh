package org.folio.oaipmh.helpers;

import io.vertx.core.Context;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.apache.log4j.Logger;
import org.folio.oaipmh.MetadataPrefix;
import org.folio.oaipmh.Request;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.openarchives.oai._2.MetadataType;
import org.openarchives.oai._2.OAIPMH;
import org.openarchives.oai._2.OAIPMHerrorType;
import org.openarchives.oai._2.RecordType;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.oaipmh.Constants.GENERIC_ERROR_MESSAGE;

public abstract class AbstractGetRecordsHelper extends AbstractHelper {

  protected final Logger logger = Logger.getLogger(getClass());

  @Override
  public CompletableFuture<Response> handle(Request request, Context ctx) {
    CompletableFuture<Response> future = new VertxCompletableFuture<>(ctx);
    try {
      List<OAIPMHerrorType> errors = validateRequest(request);
      if (!errors.isEmpty()) {
        OAIPMH oai = buildBaseResponse(request).withErrors(errors);
        future.complete(buildResponseWithErrors(oai));
        return future;
      }

      final HttpClientInterface httpClient = getOkapiClient(request.getOkapiHeaders(), false);
      final String instanceEndpoint = storageHelper.buildItemsEndpoint(request);

      VertxCompletableFuture.from(ctx, httpClient.request(instanceEndpoint, request.getOkapiHeaders(), false))
        .thenCompose(response -> buildRecordsResponse(ctx, httpClient, request, response))
        .thenAccept(value -> {
          httpClient.closeClient();
          future.complete(value);
        })
        .exceptionally(e -> {
          httpClient.closeClient();
          handleException(future, e);
          return null;
        });
    } catch (Exception e) {
      handleException(future, e);
    }
    return future;
  }

  private CompletableFuture<Response> buildNoRecordsFoundOaiResponse(Request request) {
    OAIPMH oaipmh = buildBaseResponse(request).withErrors(createNoRecordsFoundError());
    return completedFuture(buildResponseWithErrors(oaipmh));
  }

  private CompletableFuture<MetadataType> getOaiMetadata(Context ctx, HttpClientInterface httpClient, Request request, String id) {
    try {
      String metadataEndpoint = storageHelper.getMetadataEndpoint(id);
      return VertxCompletableFuture.from(ctx, httpClient.request(metadataEndpoint, request.getOkapiHeaders(), false))
                                   .thenApplyAsync(response -> buildOaiMetadata(request, response));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private CompletableFuture<Response> buildRecordsResponse(Context ctx, HttpClientInterface httpClient, Request request,
                                                           org.folio.rest.tools.client.Response instancesResponse) {
    requiresSuccessStorageResponse(instancesResponse);

    final Map<String, RecordType> records = buildRecords(request, instancesResponse);

    if (records.isEmpty()) {
      return buildNoRecordsFoundOaiResponse(request);
    } else {
      List<CompletableFuture<Void>> cfs = new ArrayList<>();

      records.forEach((id, record) -> cfs.add(getOaiMetadata(ctx, httpClient, request, id).thenAccept(record::withMetadata)));

      return VertxCompletableFuture.from(ctx, CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])))
              .thenApply(v -> {
                OAIPMH oaipmh = buildBaseResponse(request);

                // Return only records with metadata populated
                addRecordsToOaiResponce(oaipmh, records.values().stream()
                                                       .filter(record -> record.getMetadata() != null)
                                                       .collect(Collectors.toList()));
                return buildResponse(oaipmh);
              });
    }
  }

  /**
   * Builds {@link Map} with storage id as key and {@link RecordType} with populated header if there is any,
   * otherwise empty map is returned
   */
  private Map<String, RecordType> buildRecords(Request request, org.folio.rest.tools.client.Response instancesResponse) {
    Map<String, RecordType> records = Collections.emptyMap();
    JsonArray instances = storageHelper.getItems(instancesResponse.getBody());
    if (instances != null && !instances.isEmpty()) {
      // Using LinkedHashMap just to rely on order returned by storage service
      records = new LinkedHashMap<>();
      String identifierPrefix = request.getIdentifierPrefix();

      for (Object entity : instances) {
        JsonObject instance = (JsonObject) entity;
        String storageInstanceId = storageHelper.getItemId(instance);
        RecordType record = new RecordType()
          .withHeader(createHeader(instance)
          .withIdentifier(getIdentifier(identifierPrefix, storageInstanceId)));
        records.put(storageInstanceId, record);
      }
    }
    return records;
  }

  /**
   * Builds {@link MetadataType} based on the response from storage service
   * @param request the request to get metadata prefix
   * @param sourceResponse the response with {@link JsonObject} which contains record metadata
   * @return OAI record metadata
   */
  private MetadataType buildOaiMetadata(Request request, org.folio.rest.tools.client.Response sourceResponse) {
    if (!org.folio.rest.tools.client.Response.isSuccess(sourceResponse.getCode())) {
      logger.error("Record not found. Service responded with error: " + sourceResponse.getError());

      // If no record found (404 status code), we need to skip such record for now (see MODOAIPMH-12)
      if (sourceResponse.getCode() == 404) {
        return null;
      }

      // the rest of the errors we cannot handle
      throw new IllegalStateException(sourceResponse.getError().toString());
    }

    JsonObject source = sourceResponse.getBody();
    MetadataType metadata = new MetadataType();
    MetadataPrefix metadataPrefix = MetadataPrefix.fromName(request.getMetadataPrefix());
    metadata.setAny(metadataPrefix.convert(source.toString()));
    return metadata;
  }

  /**
   * In case the storage service could not return instances we have to send 500 back to client.
   * So the method is intended to validate and throw {@link IllegalStateException} for failure responses
   * @param sourceResponse response from the storage
   */
  private void requiresSuccessStorageResponse(org.folio.rest.tools.client.Response sourceResponse) {
    if (!org.folio.rest.tools.client.Response.isSuccess(sourceResponse.getCode())) {
      logger.error("Source not found. Service responded with error: " + sourceResponse.getError());
      throw new IllegalStateException(sourceResponse.getError().toString());
    }
  }

  private void handleException(CompletableFuture<Response> future, Throwable e) {
    logger.error(GENERIC_ERROR_MESSAGE, e);
    future.completeExceptionally(e);
  }

  private javax.ws.rs.core.Response buildResponse(OAIPMH oai) {
    if (!oai.getErrors().isEmpty()) {
      return buildResponseWithErrors(oai);
    }
    return buildSuccessResponse(oai);
  }

  protected abstract Response buildSuccessResponse(OAIPMH oai);
  protected abstract Response buildResponseWithErrors(OAIPMH oai);
  protected abstract List<OAIPMHerrorType> validateRequest(Request request);
  protected abstract void addRecordsToOaiResponce(OAIPMH oaipmh, Collection<RecordType> records);

}

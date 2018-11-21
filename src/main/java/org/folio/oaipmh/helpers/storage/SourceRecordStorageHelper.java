package org.folio.oaipmh.helpers.storage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.oaipmh.Request;

import java.io.UnsupportedEncodingException;

public class SourceRecordStorageHelper extends AbstractStorageHelper {

  private static final String RECORD_ID = "recordId";
  public static final String SOURCE_STORAGE_RESULT_URI = "/source-storage/result";
  public static final String SOURCE_STORAGE_RECORD_URI = "/source-storage/record/%s";

  @Override
  public JsonArray getItems(JsonObject entries) {
    return entries.getJsonArray("results");
  }

  @Override
  public String getRecordId(JsonObject entry) {
    return entry.getString(RECORD_ID);
  }

  @Override
  public String getInstanceRecordSource(JsonObject entry) {
    JsonObject parsedRecord = entry.getJsonObject("parsedRecord");
    return (parsedRecord != null ) ? parsedRecord.getString("content") : null;
  }

  @Override
  public String getRecordSource(JsonObject record) {
    return getInstanceRecordSource(record);
  }

  @Override
  public String buildRecordsEndpoint(Request request) throws UnsupportedEncodingException {
    return SOURCE_STORAGE_RESULT_URI + buildSearchQuery(request);
  }

  @Override
  protected void addSource(CQLQueryBuilder queryBuilder) {
    queryBuilder.addStrictCriteria("recordType", "MARC");
  }

  @Override
  protected String getIdentifierName() {
    return RECORD_ID;
  }

  @Override
  public String getRecordByIdEndpoint(String id){
    return String.format(SOURCE_STORAGE_RECORD_URI, id);
  }
}

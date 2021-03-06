package org.folio.oaipmh.helpers.storage;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.oaipmh.Request;

import java.io.UnsupportedEncodingException;
import java.util.Optional;

public class SourceRecordStorageHelper extends AbstractStorageHelper {

  private static final String RECORD_ID = "recordId";

  /**
   * Alternative option is to use SourceStorageClient generated by RMB.
   * See https://github.com/folio-org/mod-source-record-storage#rest-client-for-mod-source-record-storage
   */
  public static final String SOURCE_STORAGE_RESULT_URI = "/source-storage/sourceRecords";
  public static final String SOURCE_STORAGE_RECORD_URI = "/source-storage/records/%s";
  private static final String CONTENT = "content";
  private static final String FIELDS = "fields";
  private static final String FILED_999_KEY = "999";
  private static final String SUBFIELDS = "subfields";
  private static final int INSTANCE_ID_POSITION = 1;
  private static final String INSTANCE_ID = "i";
  private static final String PARSED_RECORD = "parsedRecord";

  @Override
  public JsonArray getItems(JsonObject entries) {
    return entries.getJsonArray("sourceRecords");
  }

  @Override
  public String getRecordId(JsonObject entry) {
    return entry.getString(RECORD_ID);
  }

  /**
   * Returns instance id that is linked to record within 999 field
   *
   * @param entry the item returned by source-storage
   * @return instance id
   */
  @Override
  public String getIdentifierId(final JsonObject entry) {
    Optional<JsonArray> parsedRecordFields = Optional.ofNullable(entry.getJsonObject(PARSED_RECORD))
      .map(parsedRecord -> parsedRecord.getJsonObject(CONTENT))
      .map(content -> content.getJsonArray(FIELDS));
    return parsedRecordFields.flatMap(this::getInstanceIdFieldHolder)
      .map(field -> field.getJsonArray(SUBFIELDS))
      .map(subfields -> subfields.getJsonObject(INSTANCE_ID_POSITION))
      .map(instanceId -> instanceId.getString(INSTANCE_ID))
      //if 999 field is missed then it means that record hasn't parsedRecord metadata and will be filtered from result response later
      .orElse("");
  }

  /**
   * Returns json object that contains data of 999 content field
   *
   * @param jsonArray - array of content fields within parsedRecord json field
   * @return Optional of json object that contains data of 999 content field
   */
  private Optional<JsonObject> getInstanceIdFieldHolder(JsonArray jsonArray) {
    return jsonArray.stream()
      .map(obj -> (JsonObject) obj)
      .filter(jsonObj -> jsonObj.containsKey(FILED_999_KEY))
      .map(obj -> obj.getJsonObject(FILED_999_KEY))
      .findFirst();
  }

  @Override
  public String getInstanceRecordSource(JsonObject entry) {
    return Optional.ofNullable(entry.getJsonObject(PARSED_RECORD))
      .map(record -> record.getJsonObject(CONTENT))
      .map(JsonObject::encode)
      .orElse(null);
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
  void addSuppressFromDiscovery(final CQLQueryBuilder queryBuilder) {
    queryBuilder.addStrictCriteria("additionalInfo.suppressDiscovery", "false");
  }

  @Override
  protected String getIdentifierName() {
    return RECORD_ID;
  }

  @Override
  public String getRecordByIdEndpoint(String id) {
    return String.format(SOURCE_STORAGE_RECORD_URI, id);
  }

}

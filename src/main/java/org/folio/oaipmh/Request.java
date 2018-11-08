package org.folio.oaipmh;

import org.folio.rest.tools.utils.TenantTool;
import org.openarchives.oai._2.RequestType;
import org.openarchives.oai._2.VerbType;
import org.openarchives.oai._2_0.oai_identifier.OaiIdentifier;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Class that represents OAI-PMH request and holds http query arguments.
 * It implements builder pattern, so use {@link Builder} instance to build an instance of the request.
 */
public class Request {
  private RequestType oaiRequest;
  private Map<String, String> okapiHeaders;

  /**
   * Builder used to build the request.
   */
  public static class Builder {
    private RequestType oaiRequest = new RequestType();
    private Map<String, String> okapiHeaders;

    public Builder metadataPrefix(String metadataPrefix) {
      oaiRequest.setMetadataPrefix(metadataPrefix);
      return this;
    }

    public Builder identifier(String identifier) {
      oaiRequest.setIdentifier(identifier);
      return this;
    }

    public Builder from(String from) {
      oaiRequest.setFrom(from);
      return this;
    }

    public Builder until(String until) {
      oaiRequest.setUntil(until);
      return this;
    }

    public Builder set(String set) {
      oaiRequest.setSet(set);
      return this;
    }

    public Builder resumptionToken(String resumptionToken) {
      oaiRequest.setResumptionToken(resumptionToken);
      return this;
    }

    public Builder okapiHeaders(Map<String, String> okapiHeaders) {
      this.okapiHeaders = okapiHeaders;
      return this;
    }

    public Builder verb(VerbType verb) {
      this.oaiRequest.setVerb(verb);
      return this;
    }

    public Request build() {
      return new Request(oaiRequest, okapiHeaders);
    }


    public Builder baseURL(String baseURL) {
      oaiRequest.setValue(baseURL);
      return this;
    }
  }


  private Request(RequestType oaiRequest, Map<String, String> okapiHeaders) {
    this.oaiRequest = oaiRequest;
    this.okapiHeaders = okapiHeaders;
  }

  public String getMetadataPrefix() {
    return oaiRequest.getMetadataPrefix();
  }

  public String getIdentifier() {
    return oaiRequest.getIdentifier();
  }


  public String getFrom() {
    return oaiRequest.getFrom();
  }

  public String getUntil() {
    return oaiRequest.getUntil();
  }

  public String getSet() {
    return oaiRequest.getSet();
  }

  public String getResumptionToken() {
    return oaiRequest.getResumptionToken();
  }

  public RequestType getOaiRequest() {
    return oaiRequest;
  }

  public String getStorageIdentifier() {
    return getIdentifier().substring(getIdentifierPrefix().length()) ;
  }

  public String getIdentifierPrefix() {
    try {
      String tenantId = TenantTool.tenantId(okapiHeaders);
      URL url = new URL(oaiRequest.getValue());
      OaiIdentifier oaiIdentifier = new OaiIdentifier();
      oaiIdentifier.setRepositoryIdentifier(url.getHost());
      return oaiIdentifier.getScheme() + oaiIdentifier.getDelimiter()
        + oaiIdentifier.getRepositoryIdentifier()
        + oaiIdentifier.getDelimiter() + tenantId + "/";
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, String> getOkapiHeaders() {
    return okapiHeaders;
  }

  /**
   * Factory method returning an instance of the builder.
   * @return {@link Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }



}

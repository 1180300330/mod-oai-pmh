package org.folio.oaipmh.mappers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.dom.DOMResult;

import org.marc4j.MarcJsonReader;
import org.marc4j.MarcReader;
import org.marc4j.MarcWriter;
import org.marc4j.MarcXmlWriter;
import org.marc4j.marc.Record;
import org.w3c.dom.Node;


public abstract class AbstractMapper implements Mapper {

  protected Node templateConvert(String source) {
    try (InputStream inputStream
           = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))) {
      MarcReader marcJsonReader = new MarcJsonReader(inputStream);
      DOMResult domResult = new DOMResult();
      MarcWriter marcXmlWriter = new MarcXmlWriter(domResult);
      while (marcJsonReader.hasNext()) {
        Record record = marcJsonReader.next();
        marcXmlWriter.write(record);
      }
      marcXmlWriter.close();
      return domResult.getNode().getFirstChild().getFirstChild();
    } catch (IOException e) {
      throw new UncheckedIOException(e); //should never happen
    }
  }

}

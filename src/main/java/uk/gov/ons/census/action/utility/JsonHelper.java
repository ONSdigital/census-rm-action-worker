package uk.gov.ons.census.action.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.entity.FulfilmentsToSend;

import java.io.IOException;

public class JsonHelper {
  private static final ObjectMapper objectMapper;

  static {
    objectMapper =
        new ObjectMapper();
  }

  public static PrintFileDto convertJsonToObject(FulfilmentsToSend fulfilmentToSend) throws IOException {
    try {
      return objectMapper.readValue(fulfilmentToSend.getMessageData(), PrintFileDto.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed converting Json To Object", e);
    }
  }
}

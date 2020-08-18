package uk.gov.ons.census.action.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class UacQidCreated {
  private String uac;
  private String qid;
  private UUID caseId;
  private UUID batchId;
}

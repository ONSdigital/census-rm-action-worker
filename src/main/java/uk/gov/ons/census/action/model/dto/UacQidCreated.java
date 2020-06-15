package uk.gov.ons.census.action.model.dto;

import lombok.Data;

@Data
public class UacQidCreated {
  private String uac;
  private String qid;
  private String caseId;
}

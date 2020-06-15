package uk.gov.ons.census.action.model.dto;

import lombok.Data;

@Data
public class Payload {
  private PrintCaseSelected printCaseSelected;
  private FieldCaseSelected fieldCaseSelected;
  private UacQidCreated uacQidCreated;
}

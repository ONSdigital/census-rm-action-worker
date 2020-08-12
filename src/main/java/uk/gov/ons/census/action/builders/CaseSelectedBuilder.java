package uk.gov.ons.census.action.builders;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.model.dto.Event;
import uk.gov.ons.census.action.model.dto.EventType;
import uk.gov.ons.census.action.model.dto.FieldCaseSelected;
import uk.gov.ons.census.action.model.dto.Payload;
import uk.gov.ons.census.action.model.dto.PrintCaseSelected;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;

@Component
public class CaseSelectedBuilder {
  public ResponseManagementEvent buildPrintMessage(
      UUID batchId, long caseRef, String packCode, UUID actionRuleId) {
    ResponseManagementEvent responseManagementEvent =
        buildEventWithoutPayload(EventType.PRINT_CASE_SELECTED);
    PrintCaseSelected printCaseSelected = new PrintCaseSelected();
    responseManagementEvent.getPayload().setPrintCaseSelected(printCaseSelected);

    printCaseSelected.setActionRuleId(actionRuleId);
    printCaseSelected.setBatchId(batchId.toString());
    printCaseSelected.setCaseRef(caseRef);
    printCaseSelected.setPackCode(packCode);

    return responseManagementEvent;
  }

  public ResponseManagementEvent buildFieldMessage(String caseRef, UUID actionRuleId) {
    ResponseManagementEvent responseManagementEvent =
        buildEventWithoutPayload(EventType.FIELD_CASE_SELECTED);
    FieldCaseSelected fieldCaseSelected = new FieldCaseSelected();
    responseManagementEvent.getPayload().setFieldCaseSelected(fieldCaseSelected);

    fieldCaseSelected.setActionRuleId(actionRuleId);
    fieldCaseSelected.setCaseRef(Long.parseLong(caseRef));

    return responseManagementEvent;
  }

  private ResponseManagementEvent buildEventWithoutPayload(EventType eventType) {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    Event event = new Event();
    responseManagementEvent.setEvent(event);
    Payload payload = new Payload();
    responseManagementEvent.setPayload(payload);

    event.setType(eventType);
    event.setSource("ACTION_WORKER");
    event.setChannel("RM");
    event.setDateTime(OffsetDateTime.now());
    event.setTransactionId(UUID.randomUUID().toString());

    return responseManagementEvent;
  }
}

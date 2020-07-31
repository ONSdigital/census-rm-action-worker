package uk.gov.ons.census.action.builders;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.model.UacQidTuple;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;

@Component
public class PrintFileDtoBuilder {
  private static final Set<ActionType> doesNotRequireUacQidActionTypes =
      Set.of(
          ActionType.P_RL_1RL1A,
          ActionType.P_RL_1RL2BA,
          ActionType.P_RL_2RL1,
          ActionType.P_RL_2RL2B,
          ActionType.P_RL_2RL1A,
          ActionType.P_RL_2RL2BA,
          ActionType.P_RL_1IRL1,
          ActionType.P_RL_1IRL2B,
          ActionType.P_RL_1RL1B,
          ActionType.P_RL_1RL2BB,
          ActionType.P_RL_1RL4
          // Adding to this list will result in no UAC being sent on the printed
          // letter/questionnaire. Please be certain of what you're doing before adding to it.
          );
  private final UacQidLinkBuilder uacQidLinkBuilder;

  public PrintFileDtoBuilder(UacQidLinkBuilder uacQidLinkBuilder) {
    this.uacQidLinkBuilder = uacQidLinkBuilder;
  }

  public PrintFileDto buildPrintFileDto(
      Case selectedCase, String packCode, UUID batchUUID, ActionType actionType) {

    PrintFileDto printFileDto = new PrintFileDto();

    // "EQ launched but not submitted/completed" reminders don't have a UAC-QID pair for security
    // because the respondent has already partially filled in their EQ.
    if (!doesNotRequireUacQidActionTypes.contains(actionType)) {
      UacQidTuple uacQidTuple = uacQidLinkBuilder.getUacQidLinks(selectedCase, actionType);

      printFileDto.setUac(uacQidTuple.getUacQidLink().getUac());
      printFileDto.setQid(uacQidTuple.getUacQidLink().getQid());

      if (uacQidTuple.getUacQidLinkWales().isPresent()) {
        printFileDto.setUacWales(uacQidTuple.getUacQidLinkWales().get().getUac());
        printFileDto.setQidWales(uacQidTuple.getUacQidLinkWales().get().getQid());
      }
    }

    printFileDto.setCaseRef(selectedCase.getCaseRef());
    printFileDto.setAddressLine1(selectedCase.getAddressLine1());
    printFileDto.setAddressLine2(selectedCase.getAddressLine2());
    printFileDto.setAddressLine3(selectedCase.getAddressLine3());
    printFileDto.setTownName(selectedCase.getTownName());
    printFileDto.setPostcode(selectedCase.getPostcode());
    printFileDto.setBatchId(batchUUID.toString());
    printFileDto.setPackCode(packCode);
    printFileDto.setActionType(actionType.toString());
    printFileDto.setFieldCoordinatorId(selectedCase.getFieldCoordinatorId());
    printFileDto.setFieldOfficerId(selectedCase.getFieldOfficerId());
    printFileDto.setOrganisationName(selectedCase.getOrganisationName());

    return printFileDto;
  }
}

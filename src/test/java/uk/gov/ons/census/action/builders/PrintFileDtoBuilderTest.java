package uk.gov.ons.census.action.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import uk.gov.ons.census.action.model.UacQidTuple;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.UacQidLink;

public class PrintFileDtoBuilderTest {
  private static final String ENGLISH_UAC = "ENGLISH_UAC";
  private static final String ENGLISH_QID = "ENGLISH_QID";
  private static final String WELSH_UAC = "WELSH_UAC";
  private static final String WELSH_QID = "WELSH_QID";
  private static final UUID BATCH_UUID = UUID.randomUUID();
  private static final ActionType expectedActionType = ActionType.ICL1E;

  private final HashMap<ActionType, String> actionTypeToPackCodeMap =
      new HashMap<>() {
        {
          put(ActionType.ICHHQE, "P_IC_H1");
          put(ActionType.ICHHQW, "P_IC_H2");
          put(ActionType.ICHHQN, "P_IC_H4");
          put(ActionType.ICL1E, "P_IC_ICL1");
          put(ActionType.ICL2W, "P_IC_ICL2B");
          put(ActionType.ICL4N, "P_IC_ICL4");
        }
      };

  @Test
  public void testGoodBuild() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case testCaze = easyRandom.nextObject(Case.class);
    UacQidLinkBuilder uacQidBuilder = getQidUacBuilder();

    PrintFileDto expectedPrintFileDto = getExpectedPrintFileDto(testCaze, ActionType.ICHHQW, false);
    PrintFileDtoBuilder printFileDtoBuilder = new PrintFileDtoBuilder(uacQidBuilder);

    // When
    PrintFileDto actualPrintFileDto =
        printFileDtoBuilder.buildPrintFileDto(
            testCaze,
            actionTypeToPackCodeMap.get(expectedActionType),
            BATCH_UUID,
            ActionType.ICHHQW,
            UUID.randomUUID());

    // Then
    assertThat(actualPrintFileDto).isEqualToComparingFieldByField(expectedPrintFileDto);
  }

  @Test
  public void testReminderForRespondentLaunchedEqButNotSubmittedHasNoUacQid() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case testCaze = easyRandom.nextObject(Case.class);
    UacQidLinkBuilder uacQidBuilder = getQidUacBuilder();

    PrintFileDto expectedPrintFileDto =
        getExpectedPrintFileDto(testCaze, ActionType.P_RL_1RL1A, true);
    PrintFileDtoBuilder printFileDtoBuilder = new PrintFileDtoBuilder(uacQidBuilder);

    // When
    PrintFileDto actualPrintFileDto =
        printFileDtoBuilder.buildPrintFileDto(
            testCaze,
            actionTypeToPackCodeMap.get(expectedActionType),
            BATCH_UUID,
            ActionType.P_RL_1RL1A,
            UUID.randomUUID());

    // Then
    assertThat(actualPrintFileDto).isEqualToComparingFieldByField(expectedPrintFileDto);
  }

  private UacQidLinkBuilder getQidUacBuilder() {
    UacQidTuple uacQidTuple = new UacQidTuple();
    UacQidLink englishLink = new UacQidLink();
    englishLink.setUac(ENGLISH_UAC);
    englishLink.setQid(ENGLISH_QID);
    uacQidTuple.setUacQidLink(englishLink);

    UacQidLink welshLink = new UacQidLink();
    welshLink.setUac(WELSH_UAC);
    welshLink.setQid(WELSH_QID);
    uacQidTuple.setUacQidLinkWales(Optional.of(welshLink));

    UacQidLinkBuilder uacQidLinkBuilder = mock(UacQidLinkBuilder.class);
    when(uacQidLinkBuilder.getUacQidLinks(any(Case.class), any(ActionType.class), any(UUID.class)))
        .thenReturn(uacQidTuple);

    return uacQidLinkBuilder;
  }

  private PrintFileDto getExpectedPrintFileDto(
      Case caze, ActionType actionType, boolean isReminderWithoutUacQid) {
    PrintFileDto printFileDto = new PrintFileDto();

    if (!isReminderWithoutUacQid) {
      printFileDto.setUac(ENGLISH_UAC);
      printFileDto.setQid(ENGLISH_QID);
      printFileDto.setUacWales(WELSH_UAC);
      printFileDto.setQidWales(WELSH_QID);
    }

    printFileDto.setCaseRef(caze.getCaseRef());
    printFileDto.setAddressLine1(caze.getAddressLine1());
    printFileDto.setAddressLine2(caze.getAddressLine2());
    printFileDto.setAddressLine3(caze.getAddressLine3());
    printFileDto.setTownName(caze.getTownName());
    printFileDto.setPostcode(caze.getPostcode());
    printFileDto.setBatchId(BATCH_UUID);
    printFileDto.setPackCode(actionTypeToPackCodeMap.get(expectedActionType));
    printFileDto.setActionType(actionType.toString());
    printFileDto.setFieldCoordinatorId(caze.getFieldCoordinatorId());
    printFileDto.setFieldOfficerId(caze.getFieldOfficerId());
    printFileDto.setOrganisationName(caze.getOrganisationName());

    return printFileDto;
  }
}

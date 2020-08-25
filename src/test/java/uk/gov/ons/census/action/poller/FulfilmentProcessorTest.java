package uk.gov.ons.census.action.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uk.gov.ons.census.action.builders.CaseSelectedBuilder;
import uk.gov.ons.census.action.builders.UacQidLinkBuilder;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.action.model.entity.ActionHandler;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;
import uk.gov.ons.census.action.model.entity.UacQidLink;

public class FulfilmentProcessorTest {
  private String OUTBOUND_EXCHANGE = "test outbound exchange";
  private String ACTION_CASE_EXCHANGE = "action case exchange";

  @Test
  public void testSendingFulfilments() {
    // Given
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    UacQidLinkBuilder uacQidLinkBuilder = mock(UacQidLinkBuilder.class);
    CaseSelectedBuilder caseSelectedBuilder = mock(CaseSelectedBuilder.class);
    FulfilmentProcessor underTest =
        new FulfilmentProcessor(
            rabbitTemplate,
            uacQidLinkBuilder,
            caseSelectedBuilder,
            OUTBOUND_EXCHANGE,
            ACTION_CASE_EXCHANGE);

    EasyRandom easyRandom = new EasyRandom();
    FulfilmentToProcess fulfilmentToProcess = easyRandom.nextObject(FulfilmentToProcess.class);
    fulfilmentToProcess.setFulfilmentCode("P_OR_H1");
    fulfilmentToProcess.setQuantity(8);
    fulfilmentToProcess.setBatchId(UUID.randomUUID());

    UacQidLink uacQidLink = easyRandom.nextObject(UacQidLink.class);
    when(uacQidLinkBuilder.createNewUacQidPair(any(Case.class), anyString(), any(UUID.class)))
        .thenReturn(uacQidLink);

    ResponseManagementEvent printMessage = easyRandom.nextObject(ResponseManagementEvent.class);
    when(caseSelectedBuilder.buildPrintMessage(any(), anyLong(), any(), any()))
        .thenReturn(printMessage);

    // When
    underTest.process(fulfilmentToProcess);

    // Then
    ArgumentCaptor<PrintFileDto> printFileDtoArgumentCaptor =
        ArgumentCaptor.forClass(PrintFileDto.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(OUTBOUND_EXCHANGE),
            eq(ActionHandler.PRINTER.getRoutingKey()),
            printFileDtoArgumentCaptor.capture());

    PrintFileDto printFileDto = printFileDtoArgumentCaptor.getValue();
    assertThat(printFileDto)
        .isEqualToComparingOnlyGivenFields(
            fulfilmentToProcess,
            "addressLine1",
            "addressLine2",
            "addressLine3",
            "postcode",
            "townName",
            "title",
            "forename",
            "surname",
            "fieldCoordinatorId",
            "fieldOfficerId",
            "organisationName");

    assertThat(printFileDto.getActionType())
        .isEqualTo(fulfilmentToProcess.getFulfilmentType().name());
    assertThat(printFileDto.getCaseRef()).isEqualTo(fulfilmentToProcess.getCaze().getCaseRef());
    assertThat(printFileDto.getPackCode()).isEqualTo(fulfilmentToProcess.getFulfilmentCode());

    assertThat(printFileDto.getUac()).isEqualTo(uacQidLink.getUac());
    assertThat(printFileDto.getQid()).isEqualTo(uacQidLink.getQid());

    verify(uacQidLinkBuilder)
        .createNewUacQidPair(
            eq(fulfilmentToProcess.getCaze()), eq("1"), eq(fulfilmentToProcess.getBatchId()));

    verify(caseSelectedBuilder)
        .buildPrintMessage(
            eq(fulfilmentToProcess.getBatchId()),
            eq(fulfilmentToProcess.getCaze().getCaseRef()),
            eq(fulfilmentToProcess.getFulfilmentCode()),
            eq(null));
    verify(rabbitTemplate).convertAndSend(eq(ACTION_CASE_EXCHANGE), eq(""), eq(printMessage));
  }

  @Test
  public void testSendingFulfilmentsNoUacQid() {
    // Given
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    UacQidLinkBuilder uacQidLinkBuilder = mock(UacQidLinkBuilder.class);
    CaseSelectedBuilder caseSelectedBuilder = mock(CaseSelectedBuilder.class);
    FulfilmentProcessor underTest =
        new FulfilmentProcessor(
            rabbitTemplate,
            uacQidLinkBuilder,
            caseSelectedBuilder,
            OUTBOUND_EXCHANGE,
            ACTION_CASE_EXCHANGE);

    EasyRandom easyRandom = new EasyRandom();
    FulfilmentToProcess fulfilmentToProcess = easyRandom.nextObject(FulfilmentToProcess.class);
    fulfilmentToProcess.setFulfilmentCode("this fulfilment code doesn't need a UAC QID innit");
    fulfilmentToProcess.setQuantity(8);
    fulfilmentToProcess.setBatchId(UUID.randomUUID());

    underTest.process(fulfilmentToProcess);

    ArgumentCaptor<PrintFileDto> printFileDtoArgumentCaptor =
        ArgumentCaptor.forClass(PrintFileDto.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(OUTBOUND_EXCHANGE),
            eq(ActionType.ICL1E.getHandler().getRoutingKey()),
            printFileDtoArgumentCaptor.capture());

    PrintFileDto printFileDto = printFileDtoArgumentCaptor.getValue();
    assertThat(printFileDto)
        .isEqualToComparingOnlyGivenFields(
            fulfilmentToProcess,
            "addressLine1",
            "addressLine2",
            "addressLine3",
            "postcode",
            "townName",
            "title",
            "forename",
            "surname",
            "fieldCoordinatorId",
            "fieldOfficerId",
            "organisationName");

    assertThat(printFileDto.getActionType())
        .isEqualTo(fulfilmentToProcess.getFulfilmentType().name());
    assertThat(printFileDto.getCaseRef()).isEqualTo(fulfilmentToProcess.getCaze().getCaseRef());
    assertThat(printFileDto.getPackCode()).isEqualTo(fulfilmentToProcess.getFulfilmentCode());

    assertThat(printFileDto.getUac()).isNull();
    assertThat(printFileDto.getQid()).isNull();

    verifyNoInteractions(uacQidLinkBuilder);
  }
}

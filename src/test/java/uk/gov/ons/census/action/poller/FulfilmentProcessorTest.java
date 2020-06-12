package uk.gov.ons.census.action.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.ons.census.action.cache.UacQidCache;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentProcessorTest {

  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private UacQidCache uacQidCache;

  @InjectMocks private FulfilmentProcessor underTest;

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  @Test
  public void testSendingFulfilments() {
    EasyRandom easyRandom = new EasyRandom();
    FulfilmentToProcess fulfilmentToProcess = easyRandom.nextObject(FulfilmentToProcess.class);
    fulfilmentToProcess.setFulfilmentCode("P_OR_H1");
    fulfilmentToProcess.setQuantity(8);
    fulfilmentToProcess.setBatchId(UUID.randomUUID());

    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    underTest.process(fulfilmentToProcess);

    ArgumentCaptor<PrintFileDto> printFileDtoArgumentCaptor =
        ArgumentCaptor.forClass(PrintFileDto.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(outboundExchange),
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
            "surname");

    assertThat(printFileDto.getActionType()).isEqualTo(fulfilmentToProcess.getActionType().name());
    assertThat(printFileDto.getCaseRef()).isEqualTo(fulfilmentToProcess.getCaze().getCaseRef());
    assertThat(printFileDto.getPackCode()).isEqualTo(fulfilmentToProcess.getFulfilmentCode());

    assertThat(printFileDto.getUac()).isEqualTo(uacQidDTO.getUac());
    assertThat(printFileDto.getQid()).isEqualTo(uacQidDTO.getQid());
  }

  @Test
  public void testSendingFulfilmentsNoUacQid() {
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
            eq(outboundExchange),
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
            "surname");

    assertThat(printFileDto.getActionType()).isEqualTo(fulfilmentToProcess.getActionType().name());
    assertThat(printFileDto.getCaseRef()).isEqualTo(fulfilmentToProcess.getCaze().getCaseRef());
    assertThat(printFileDto.getPackCode()).isEqualTo(fulfilmentToProcess.getFulfilmentCode());

    assertThat(printFileDto.getUac()).isNull();
    assertThat(printFileDto.getQid()).isNull();

    verifyNoInteractions(uacQidCache);
  }
}

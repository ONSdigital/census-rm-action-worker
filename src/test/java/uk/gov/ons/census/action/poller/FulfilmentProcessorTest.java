package uk.gov.ons.census.action.poller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.action.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.FulfilmentsToSend;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentProcessorTest {

  @Mock private RabbitTemplate rabbitTemplate;

  @InjectMocks private FulfilmentProcessor underTest;

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  @Test
  public void testSendingFulfilments() throws IOException {
    EasyRandom easyRandom = new EasyRandom();
    PrintFileDto printFileDto = new PrintFileDto();

    printFileDto.setActionType("TEST");
    printFileDto.setAddressLine1("BLAH");
    printFileDto.setBatchQuantity(8);
    printFileDto.setBatchId(UUID.randomUUID().toString());
    String stringPrintFileObject = convertObjectToJson(printFileDto);

    FulfilmentsToSend fulfilmentsToSend = easyRandom.nextObject(FulfilmentsToSend.class);
    fulfilmentsToSend.setMessageData(stringPrintFileObject);
    fulfilmentsToSend.setQuantity(printFileDto.getBatchQuantity());
    fulfilmentsToSend.setBatchId(UUID.fromString(printFileDto.getBatchId()));

    underTest.process(fulfilmentsToSend);

    verify(rabbitTemplate)
        .convertAndSend(
            eq(outboundExchange),
            eq(ActionType.ICL1E.getHandler().getRoutingKey()),
            eq(printFileDto));
  }
}

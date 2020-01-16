package uk.gov.ons.census.action.poller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.entity.ActionHandler;
import uk.gov.ons.census.action.model.entity.FulfilmentsToSend;

import java.io.IOException;

import static uk.gov.ons.census.action.utility.JsonHelper.convertJsonToObject;

@Component
public class FulfilmentProcessor {
  private final RabbitTemplate rabbitTemplate;

  public FulfilmentProcessor(RabbitTemplate rabbitTemplate){
    this.rabbitTemplate = rabbitTemplate;
  }

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.action-case-exchange}")
  private String actionCaseExchange;


  public void process(FulfilmentsToSend fulfilmentToSend) throws IOException {

    PrintFileDto fulfilmentPrintFile = convertJsonToObject(fulfilmentToSend);

    fulfilmentPrintFile.setBatchId(fulfilmentToSend.getBatchId().toString());
    fulfilmentPrintFile.setBatchQuantity(fulfilmentToSend.getQuantity());

//    rabbitTemplate.convertAndSend(actionCaseExchange, "", printCaseSelected);

    rabbitTemplate.convertAndSend(
            outboundExchange, ActionHandler.PRINTER.getRoutingKey(), fulfilmentPrintFile);



  }

}

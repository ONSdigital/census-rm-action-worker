package uk.gov.ons.census.action.poller;

import java.util.Map;
import java.util.Optional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.cache.UacQidCache;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionHandler;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;

@Component
public class FulfilmentProcessor {
  private static final Map<String, Integer> fulfilmentCodeToQuestionnaireType =
      Map.ofEntries(
          Map.entry("P_OR_H1", Integer.valueOf(1)),
          Map.entry("P_UAC_UACHHP1", Integer.valueOf(1)),
          Map.entry("P_OR_H2", Integer.valueOf(2)),
          Map.entry("P_UAC_UACHHP2B", Integer.valueOf(2)),
          Map.entry("P_OR_H2W", Integer.valueOf(3)),
          Map.entry("P_UAC_UACHHP4", Integer.valueOf(4)),
          Map.entry("P_OR_H4", Integer.valueOf(4)),
          Map.entry("P_OR_HC1", Integer.valueOf(11)),
          Map.entry("P_OR_HC2", Integer.valueOf(12)),
          Map.entry("P_OR_HC2W", Integer.valueOf(13)),
          Map.entry("P_OR_HC4", Integer.valueOf(14)),
          Map.entry("P_OR_I1", Integer.valueOf(21)),
          Map.entry("P_OR_I2", Integer.valueOf(22)),
          Map.entry("P_OR_I2W", Integer.valueOf(23)),
          Map.entry("P_OR_I4", Integer.valueOf(24)),
          Map.entry("P_UAC_UACIP1", Integer.valueOf(21)),
          Map.entry("P_UAC_UACIP2B", Integer.valueOf(22)),
          Map.entry("P_UAC_UACIP4", Integer.valueOf(24)));

  private final RabbitTemplate rabbitTemplate;
  private final UacQidCache uacQidCache;

  public FulfilmentProcessor(RabbitTemplate rabbitTemplate, UacQidCache uacQidCache) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidCache = uacQidCache;
  }

  @Value("${queueconfig.outbound-exchange}")
  private String outboundExchange;

  @Value("${queueconfig.action-case-exchange}")
  private String actionCaseExchange;

  public void process(FulfilmentToProcess fulfilmentToProcess) {

    PrintFileDto fulfilmentPrintFile = buildPrintFileDto(fulfilmentToProcess);

    rabbitTemplate.convertAndSend(
        outboundExchange, ActionHandler.PRINTER.getRoutingKey(), fulfilmentPrintFile);
  }

  private PrintFileDto buildPrintFileDto(FulfilmentToProcess fulfilmentToProcess) {
    PrintFileDto fulfilmentPrintFile = new PrintFileDto();

    fulfilmentPrintFile.setBatchId(fulfilmentToProcess.getBatchId().toString());
    fulfilmentPrintFile.setBatchQuantity(fulfilmentToProcess.getQuantity());

    fulfilmentPrintFile.setActionType(fulfilmentToProcess.getActionType().name());
    fulfilmentPrintFile.setPackCode(fulfilmentToProcess.getFulfilmentCode());
    fulfilmentPrintFile.setCaseRef(fulfilmentToProcess.getCaze().getCaseRef());

    fulfilmentPrintFile.setAddressLine1(fulfilmentToProcess.getAddressLine1());
    fulfilmentPrintFile.setAddressLine2(fulfilmentToProcess.getAddressLine2());
    fulfilmentPrintFile.setAddressLine3(fulfilmentToProcess.getAddressLine3());
    fulfilmentPrintFile.setTownName(fulfilmentToProcess.getTownName());
    fulfilmentPrintFile.setPostcode(fulfilmentToProcess.getPostcode());

    fulfilmentPrintFile.setTitle(fulfilmentToProcess.getTitle());
    fulfilmentPrintFile.setForename(fulfilmentToProcess.getForename());
    fulfilmentPrintFile.setSurname(fulfilmentToProcess.getSurname());

    Optional<Integer> questionnaireType =
        determineQuestionnaireType(fulfilmentToProcess.getFulfilmentCode());

    if (questionnaireType.isPresent()) {
      UacQidDTO uacQid = uacQidCache.getUacQidPair(questionnaireType.get());
      fulfilmentPrintFile.setQid(uacQid.getQid());
      fulfilmentPrintFile.setUac(uacQid.getUac());

      // TODO: Tell Case Processor that we just linked a UAC-QID to a case
    }

    return fulfilmentPrintFile;
  }

  private Optional<Integer> determineQuestionnaireType(String packCode) {
    return Optional.ofNullable(fulfilmentCodeToQuestionnaireType.get(packCode));
  }
}

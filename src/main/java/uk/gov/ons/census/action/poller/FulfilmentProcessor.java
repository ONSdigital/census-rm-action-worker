package uk.gov.ons.census.action.poller;

import java.util.Map;
import java.util.Optional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.builders.CaseSelectedBuilder;
import uk.gov.ons.census.action.builders.UacQidLinkBuilder;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.action.model.entity.ActionHandler;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;
import uk.gov.ons.census.action.model.entity.UacQidLink;

@Component
public class FulfilmentProcessor {
  private static final Map<String, Integer> fulfilmentCodeToQuestionnaireType =
      Map.ofEntries(
          Map.entry("P_OR_H1", 1),
          Map.entry("P_UAC_UACHHP1", 1),
          Map.entry("P_OR_H2", 2),
          Map.entry("P_UAC_UACHHP2B", 2),
          Map.entry("P_OR_H2W", 3),
          Map.entry("P_UAC_UACHHP4", 4),
          Map.entry("P_OR_H4", 4),
          Map.entry("P_OR_HC1", 11),
          Map.entry("P_OR_HC2", 12),
          Map.entry("P_OR_HC2W", 13),
          Map.entry("P_OR_HC4", 14),
          Map.entry("P_OR_I1", 21),
          Map.entry("P_OR_I2", 22),
          Map.entry("P_OR_I2W", 23),
          Map.entry("P_OR_I4", 24),
          Map.entry("P_UAC_UACIP1", 21),
          Map.entry("P_UAC_UACIP2B", 22),
          Map.entry("P_UAC_UACIP4", 24),
          Map.entry("P_UAC_UACIPA1", 21),
          Map.entry("P_UAC_UACIPA2B", 22),
          Map.entry("P_UAC_UACIPA4", 24),
          Map.entry("P_UAC_UACCEP1", 31),
          Map.entry("P_UAC_UACCEP2B", 32));

  private final RabbitTemplate rabbitTemplate;
  private final UacQidLinkBuilder uacQidLinkBuilder;
  private final CaseSelectedBuilder caseSelectedBuilder;
  private final String outboundExchange;
  private final String actionCaseExchange;

  public FulfilmentProcessor(
      RabbitTemplate rabbitTemplate,
      UacQidLinkBuilder uacQidLinkBuilder,
      CaseSelectedBuilder caseSelectedBuilder,
      @Value("${queueconfig.outbound-exchange}") String outboundExchange,
      @Value("${queueconfig.action-case-exchange}") String actionCaseExchange) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidLinkBuilder = uacQidLinkBuilder;
    this.caseSelectedBuilder = caseSelectedBuilder;
    this.outboundExchange = outboundExchange;
    this.actionCaseExchange = actionCaseExchange;
  }

  public void process(FulfilmentToProcess fulfilmentToProcess) {

    PrintFileDto fulfilmentPrintFile = buildPrintFileDto(fulfilmentToProcess);

    rabbitTemplate.convertAndSend(
        outboundExchange, ActionHandler.PRINTER.getRoutingKey(), fulfilmentPrintFile);

    ResponseManagementEvent printCaseSelected =
        caseSelectedBuilder.buildPrintMessage(
            fulfilmentToProcess.getBatchId(),
            fulfilmentToProcess.getCaze().getCaseRef(),
            fulfilmentToProcess.getFulfilmentCode(),
            null);

    rabbitTemplate.convertAndSend(actionCaseExchange, "", printCaseSelected);
  }

  private PrintFileDto buildPrintFileDto(FulfilmentToProcess fulfilmentToProcess) {
    PrintFileDto fulfilmentPrintFile = new PrintFileDto();

    fulfilmentPrintFile.setBatchId(fulfilmentToProcess.getBatchId());
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

    fulfilmentPrintFile.setOrganisationName(fulfilmentToProcess.getOrganisationName());
    fulfilmentPrintFile.setFieldCoordinatorId(fulfilmentToProcess.getFieldCoordinatorId());
    fulfilmentPrintFile.setFieldOfficerId(fulfilmentToProcess.getFieldOfficerId());

    Optional<Integer> questionnaireType =
        determineQuestionnaireType(fulfilmentToProcess.getFulfilmentCode());

    if (questionnaireType.isPresent()) {
      UacQidLink uacQid =
          uacQidLinkBuilder.createNewUacQidPair(
              fulfilmentToProcess.getCaze(),
              questionnaireType.get().toString(),
              fulfilmentToProcess.getBatchId());
      fulfilmentPrintFile.setQid(uacQid.getQid());
      fulfilmentPrintFile.setUac(uacQid.getUac());
    }

    return fulfilmentPrintFile;
  }

  private Optional<Integer> determineQuestionnaireType(String packCode) {
    return Optional.ofNullable(fulfilmentCodeToQuestionnaireType.get(packCode));
  }
}

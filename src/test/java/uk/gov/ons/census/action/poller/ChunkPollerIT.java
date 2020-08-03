package uk.gov.ons.census.action.poller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.action.model.entity.ActionType.P_OR_HX;
import static uk.gov.ons.census.action.model.entity.ActionType.P_QU_H2;
import static uk.gov.ons.census.action.model.entity.ActionType.P_RL_1RL1_1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.action.messaging.QueueSpy;
import uk.gov.ons.census.action.messaging.RabbitQueueHelper;
import uk.gov.ons.census.action.model.dto.EventType;
import uk.gov.ons.census.action.model.dto.FieldworkFollowup;
import uk.gov.ons.census.action.model.dto.PrintFileDto;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionPlan;
import uk.gov.ons.census.action.model.entity.ActionRule;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.CaseToProcess;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;
import uk.gov.ons.census.action.model.repository.ActionPlanRepository;
import uk.gov.ons.census.action.model.repository.ActionRuleRepository;
import uk.gov.ons.census.action.model.repository.CaseRepository;
import uk.gov.ons.census.action.model.repository.CaseToProcessRepository;
import uk.gov.ons.census.action.model.repository.FulfilmentToProcessRepository;

@ContextConfiguration
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class ChunkPollerIT {
  private static final String MULTIPLE_QIDS_URL = "/multiple_qids";
  private static final String OUTBOUND_PRINTER_QUEUE = "Action.Printer";
  private static final String OUTBOUND_FIELD_QUEUE = "Action.Field";
  private static final String ACTION_CASE_QUEUE = "action.events";
  private static final String CASE_UAC_QID_CREATED_QUEUE = "case.uac-qid-created";
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private ActionRuleRepository actionRuleRepository;
  @Autowired private ActionPlanRepository actionPlanRepository;
  @Autowired private CaseToProcessRepository caseToProcessRepository;
  @Autowired private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  @Rule public WireMockRule mockUacQidService = new WireMockRule(wireMockConfig().port(8899));

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(OUTBOUND_PRINTER_QUEUE);
    rabbitQueueHelper.purgeQueue(OUTBOUND_FIELD_QUEUE);
    rabbitQueueHelper.purgeQueue(ACTION_CASE_QUEUE);
    rabbitQueueHelper.purgeQueue(CASE_UAC_QID_CREATED_QUEUE);
    fulfilmentToProcessRepository.deleteAllInBatch();
    caseToProcessRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
    actionRuleRepository.deleteAllInBatch();
    actionPlanRepository.deleteAllInBatch();
    mockUacQidService.resetAll();
  }

  @Test
  public void testCaseToProcess() throws Exception {
    try (QueueSpy printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
        QueueSpy caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);
        QueueSpy uacQidCreatedQueue = rabbitQueueHelper.listen(CASE_UAC_QID_CREATED_QUEUE)) {
      // Given
      UacQidDTO uacQidDto = stubCreateUacQid(1);
      ActionPlan actionPlan = setUpActionPlan();
      ActionRule actionRule = setUpActionRule(P_RL_1RL1_1, actionPlan);
      Case randomCase = setUpCase(actionPlan, null);

      CaseToProcess caseToProcess = new CaseToProcess();
      caseToProcess.setActionRule(actionRule);
      caseToProcess.setBatchId(UUID.randomUUID());
      caseToProcess.setBatchQuantity(1);
      caseToProcess.setCaze(randomCase);
      caseToProcess = caseToProcessRepository.saveAndFlush(caseToProcess);

      // When the case to process poller executes
      String actualMessage = printerQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualActionToCaseMessage =
          caseSelectedEventQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualUacQidCreateMessage = uacQidCreatedQueue.getQueue().poll(20, TimeUnit.SECONDS);

      // Then
      assertThat(actualUacQidCreateMessage).isNotNull();
      ResponseManagementEvent actualRmUacQidCreateEvent =
          objectMapper.readValue(actualUacQidCreateMessage, ResponseManagementEvent.class);
      assertThat(actualRmUacQidCreateEvent.getEvent().getType())
          .isEqualTo(EventType.RM_UAC_CREATED);
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getCaseId())
          .isEqualTo(randomCase.getCaseId());
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getQid())
          .isEqualTo(uacQidDto.getQid());
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getUac())
          .isEqualTo(uacQidDto.getUac());

      assertThat(actualActionToCaseMessage).isNotNull();
      ResponseManagementEvent actualRmEvent =
          objectMapper.readValue(actualActionToCaseMessage, ResponseManagementEvent.class);
      assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.PRINT_CASE_SELECTED);
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getCaseRef())
          .isEqualTo(randomCase.getCaseRef());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getActionRuleId())
          .isEqualTo(actionRule.getId());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getPackCode())
          .isEqualTo("P_RL_1RL1_1");

      assertThat(actualMessage).isNotNull();
      PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

      assertThat(actualPrintFileDto.getActionType()).isEqualTo(P_RL_1RL1_1.name());
      assertThat(actualPrintFileDto.getPackCode()).isEqualTo("P_RL_1RL1_1");
      assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
      assertThat(actualPrintFileDto)
          .isEqualToIgnoringGivenFields(
              randomCase,
              "uac",
              "qid",
              "uacWales",
              "qidWales",
              "title",
              "forename",
              "surname",
              "batchId",
              "batchQuantity",
              "packCode",
              "actionType");
      assertThat(actualPrintFileDto.getBatchId()).isEqualTo(caseToProcess.getBatchId().toString());
      assertThat(actualPrintFileDto.getBatchQuantity()).isEqualTo(caseToProcess.getBatchQuantity());
    }
  }

  @Test
  public void testWelshCaseToProcess() throws Exception {
    try (QueueSpy printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
        QueueSpy caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);
        QueueSpy uacQidCreatedQueue = rabbitQueueHelper.listen(CASE_UAC_QID_CREATED_QUEUE)) {
      // Given
      UacQidDTO uacQidDto = stubCreateUacQid(2);
      UacQidDTO welshUacQidDto = stubCreateUacQid(3);
      ActionPlan actionPlan = setUpActionPlan();
      ActionRule actionRule = setUpActionRule(P_QU_H2, actionPlan);
      Case randomCase = setUpWelshCase(actionPlan, null);

      CaseToProcess caseToProcess = new CaseToProcess();
      caseToProcess.setActionRule(actionRule);
      caseToProcess.setBatchId(UUID.randomUUID());
      caseToProcess.setBatchQuantity(1);
      caseToProcess.setCaze(randomCase);
      caseToProcess = caseToProcessRepository.saveAndFlush(caseToProcess);

      // When the case to process poller executes
      String actualMessage = printerQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualActionToCaseMessage =
          caseSelectedEventQueue.getQueue().poll(20, TimeUnit.SECONDS);
      ResponseManagementEvent actualRmUacQidCreateEvent = null;
      ResponseManagementEvent actualWelshRmUacQidCreateEvent = null;
      for (int i = 0; i < 2; i++) {
        String actualUacQidCreateMessage = uacQidCreatedQueue.getQueue().poll(20, TimeUnit.SECONDS);
        ResponseManagementEvent actualRmEvent =
            objectMapper.readValue(actualUacQidCreateMessage, ResponseManagementEvent.class);
        assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.RM_UAC_CREATED);
        assertThat(actualRmEvent.getPayload().getUacQidCreated().getCaseId())
            .isEqualTo(randomCase.getCaseId());

        if (actualRmEvent.getPayload().getUacQidCreated().getQid().startsWith("02")) {
          actualRmUacQidCreateEvent = actualRmEvent;
        } else if (actualRmEvent.getPayload().getUacQidCreated().getQid().startsWith("03")) {
          actualWelshRmUacQidCreateEvent = actualRmEvent;
        }
      }

      // Then
      assertThat(actualRmUacQidCreateEvent).isNotNull();
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getQid())
          .isEqualTo(uacQidDto.getQid());
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getUac())
          .isEqualTo(uacQidDto.getUac());

      assertThat(actualWelshRmUacQidCreateEvent).isNotNull();
      assertThat(actualWelshRmUacQidCreateEvent.getPayload().getUacQidCreated().getQid())
          .isEqualTo(welshUacQidDto.getQid());
      assertThat(actualWelshRmUacQidCreateEvent.getPayload().getUacQidCreated().getUac())
          .isEqualTo(welshUacQidDto.getUac());

      assertThat(actualActionToCaseMessage).isNotNull();
      ResponseManagementEvent actualRmEvent =
          objectMapper.readValue(actualActionToCaseMessage, ResponseManagementEvent.class);
      assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.PRINT_CASE_SELECTED);
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getCaseRef())
          .isEqualTo(randomCase.getCaseRef());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getActionRuleId())
          .isEqualTo(actionRule.getId());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getPackCode())
          .isEqualTo("P_QU_H2");

      assertThat(actualMessage).isNotNull();
      PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

      assertThat(actualPrintFileDto.getActionType()).isEqualTo(P_QU_H2.name());
      assertThat(actualPrintFileDto.getPackCode()).isEqualTo("P_QU_H2");
      assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
      assertThat(actualPrintFileDto)
          .isEqualToIgnoringGivenFields(
              randomCase,
              "uac",
              "qid",
              "uacWales",
              "qidWales",
              "title",
              "forename",
              "surname",
              "batchId",
              "batchQuantity",
              "packCode",
              "actionType");
      assertThat(actualPrintFileDto.getBatchId()).isEqualTo(caseToProcess.getBatchId().toString());
      assertThat(actualPrintFileDto.getBatchQuantity()).isEqualTo(caseToProcess.getBatchQuantity());
    }
  }

  @Test
  public void testCaseToProcessForFieldworkFollowup() throws Exception {
    try (QueueSpy fieldQueue = rabbitQueueHelper.listen(OUTBOUND_FIELD_QUEUE);
        QueueSpy caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE)) {
      // Given
      ActionPlan actionPlan = setUpActionPlan();
      ActionRule actionRule = setUpActionRule(ActionType.FIELD, actionPlan);
      Case randomCase = setUpCase(actionPlan, null);

      CaseToProcess caseToProcess = new CaseToProcess();
      caseToProcess.setActionRule(actionRule);
      caseToProcess.setBatchId(UUID.randomUUID());
      caseToProcess.setBatchQuantity(1);
      caseToProcess.setCaze(randomCase);
      caseToProcessRepository.saveAndFlush(caseToProcess);

      // When the case to process poller executes
      String actualActionToCaseMessage =
          caseSelectedEventQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualMessage = fieldQueue.getQueue().poll(20, TimeUnit.SECONDS);

      // Then
      assertThat(actualActionToCaseMessage).isNotNull();
      ResponseManagementEvent actualRmEvent =
          objectMapper.readValue(actualActionToCaseMessage, ResponseManagementEvent.class);
      assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.FIELD_CASE_SELECTED);
      assertThat(actualRmEvent.getPayload().getFieldCaseSelected().getCaseRef())
          .isEqualTo(randomCase.getCaseRef());
      assertThat(actualRmEvent.getPayload().getFieldCaseSelected().getActionRuleId())
          .isEqualTo(actionRule.getId());

      FieldworkFollowup actualFieldworkFollowup =
          objectMapper.readValue(actualMessage, FieldworkFollowup.class);
      assertThat(actualFieldworkFollowup.getCaseRef())
          .isEqualTo(Long.toString(randomCase.getCaseRef()));
      assertThat(actualFieldworkFollowup.getCeActualResponses())
          .isEqualTo(randomCase.getCeActualResponses());
      assertThat(actualFieldworkFollowup.getHandDelivery()).isEqualTo(randomCase.isHandDelivery());
    }
  }

  @Test
  public void testCaseToProcessCeEstab() throws Exception {
    try (QueueSpy printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
        QueueSpy caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);
        QueueSpy uacQidCreatedQueue = rabbitQueueHelper.listen(CASE_UAC_QID_CREATED_QUEUE)) {
      // Given
      UacQidDTO uacQidDto = stubCreateUacQid(1);
      Thread.sleep(30000); // wait for wiremockery

      ActionPlan actionPlan = setUpActionPlan();
      Case randomCase = setUpCase(actionPlan, 5);
      ActionRule actionRule = setUpActionRule(ActionType.CE_IC03, actionPlan);

      // When the action plan triggers
      List<String> actualPrintMessages = new LinkedList<>();
      List<String> actualUacCreatedMessages = new LinkedList<>();
      for (int i = 0; i < 5; i++) {
        String actualPrintMessage = printerQueue.getQueue().poll(20, TimeUnit.SECONDS);
        String actualUacCreatedMessage = uacQidCreatedQueue.getQueue().poll(20, TimeUnit.SECONDS);
        assertThat(actualPrintMessage).isNotNull();
        actualPrintMessages.add(actualPrintMessage);
        assertThat(actualUacCreatedMessage).isNotNull();
        actualUacCreatedMessages.add(actualUacCreatedMessage);
      }
      String actualCaseSelectedMessage =
          caseSelectedEventQueue.getQueue().poll(20, TimeUnit.SECONDS);

      // Then
      assertThat(actualPrintMessages.size()).isEqualTo(5);
      for (String actualPrintMessage : actualPrintMessages) {
        PrintFileDto actualPrintFileDto =
            objectMapper.readValue(actualPrintMessage, PrintFileDto.class);
        assertThat(actualPrintFileDto.getActionType()).isEqualTo(ActionType.CE_IC03.name());
        assertThat(actualPrintFileDto.getPackCode()).isEqualTo(ActionType.CE_IC03.getPackCode());
        assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
        assertThat(actualPrintFileDto)
            .isEqualToIgnoringGivenFields(
                randomCase,
                "uac",
                "qid",
                "uacWales",
                "qidWales",
                "title",
                "forename",
                "surname",
                "batchId",
                "batchQuantity",
                "packCode",
                "actionType");
      }

      assertThat(actualCaseSelectedMessage).isNotNull();
      ResponseManagementEvent actualRmEvent =
          objectMapper.readValue(actualCaseSelectedMessage, ResponseManagementEvent.class);
      assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.PRINT_CASE_SELECTED);
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getCaseRef())
          .isEqualTo(randomCase.getCaseRef());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getActionRuleId())
          .isEqualTo(actionRule.getId());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getPackCode())
          .isEqualTo(ActionType.CE_IC03.getPackCode());

      assertThat(actualUacCreatedMessages.size()).isEqualTo(5);
      for (String actualUacCreatedMessage : actualUacCreatedMessages) {
        ResponseManagementEvent actualRmUacQidCreateEvent =
            objectMapper.readValue(actualUacCreatedMessage, ResponseManagementEvent.class);
        assertThat(actualRmUacQidCreateEvent.getEvent().getType())
            .isEqualTo(EventType.RM_UAC_CREATED);
        assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getCaseId())
            .isEqualTo(randomCase.getCaseId());
        assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getQid())
            .isEqualTo(uacQidDto.getQid());
        assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getUac())
            .isEqualTo(uacQidDto.getUac());
      }
    }
  }

  @Test
  public void testFulfilmentToProcess() throws Exception {
    try (QueueSpy printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
        QueueSpy caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);
        QueueSpy uacQidCreatedQueue = rabbitQueueHelper.listen(CASE_UAC_QID_CREATED_QUEUE)) {
      // Given
      UacQidDTO uacQidDto = stubCreateUacQid(1);
      ActionPlan actionPlan = setUpActionPlan();
      Case randomCase = setUpCase(actionPlan, null);
      FulfilmentToProcess fulfilmentToProcess = new FulfilmentToProcess();
      fulfilmentToProcess.setFulfilmentCode("P_OR_H1");
      fulfilmentToProcess.setActionType(P_OR_HX);
      fulfilmentToProcess.setAddressLine1(randomCase.getAddressLine1());
      fulfilmentToProcess.setAddressLine2(randomCase.getAddressLine2());
      fulfilmentToProcess.setAddressLine3(randomCase.getAddressLine3());
      fulfilmentToProcess.setTownName(randomCase.getTownName());
      fulfilmentToProcess.setPostcode(randomCase.getPostcode());
      fulfilmentToProcess.setTitle("Dr");
      fulfilmentToProcess.setForename("Hannibal");
      fulfilmentToProcess.setSurname("Lecter");
      fulfilmentToProcess.setFieldCoordinatorId("fieldCord1");
      fulfilmentToProcess.setFieldOfficerId("MrFieldOfficer");
      fulfilmentToProcess.setOrganisationName("Area51");
      fulfilmentToProcess.setCaze(randomCase);
      fulfilmentToProcess.setBatchId(UUID.randomUUID());
      fulfilmentToProcess.setQuantity(1);
      fulfilmentToProcess = fulfilmentToProcessRepository.saveAndFlush(fulfilmentToProcess);

      // When the fulfilment to process poller executes
      String actualMessage = printerQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualActionToCaseMessage =
          caseSelectedEventQueue.getQueue().poll(20, TimeUnit.SECONDS);
      String actualUacQidCreateMessage = uacQidCreatedQueue.getQueue().poll(20, TimeUnit.SECONDS);

      // Then
      assertThat(actualUacQidCreateMessage).isNotNull();
      ResponseManagementEvent actualRmUacQidCreateEvent =
          objectMapper.readValue(actualUacQidCreateMessage, ResponseManagementEvent.class);
      assertThat(actualRmUacQidCreateEvent.getEvent().getType())
          .isEqualTo(EventType.RM_UAC_CREATED);
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getCaseId())
          .isEqualTo(randomCase.getCaseId());
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getQid())
          .isEqualTo(uacQidDto.getQid());
      assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getUac())
          .isEqualTo(uacQidDto.getUac());

      assertThat(actualActionToCaseMessage).isNotNull();
      ResponseManagementEvent actualRmEvent =
          objectMapper.readValue(actualActionToCaseMessage, ResponseManagementEvent.class);
      assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.PRINT_CASE_SELECTED);
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getCaseRef())
          .isEqualTo(randomCase.getCaseRef());
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getActionRuleId()).isNull();
      assertThat(actualRmEvent.getPayload().getPrintCaseSelected().getPackCode())
          .isEqualTo("P_OR_H1");

      assertThat(actualMessage).isNotNull();
      PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

      assertThat(actualPrintFileDto.getActionType()).isEqualTo(P_OR_HX.name());
      assertThat(actualPrintFileDto.getPackCode()).isEqualTo("P_OR_H1");
      assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
      assertThat(actualPrintFileDto)
          .isEqualToIgnoringGivenFields(
              randomCase,
              "uac",
              "qid",
              "uacWales",
              "qidWales",
              "title",
              "forename",
              "surname",
              "batchId",
              "batchQuantity",
              "packCode",
              "actionType");
      assertThat(actualPrintFileDto.getBatchId())
          .isEqualTo(fulfilmentToProcess.getBatchId().toString());
      assertThat(actualPrintFileDto.getBatchQuantity())
          .isEqualTo(fulfilmentToProcess.getQuantity());
    }
  }

  private UacQidDTO stubCreateUacQid(int questionnaireType) throws JsonProcessingException {
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    uacQidDTO.setQid(String.format("%02d", questionnaireType) + uacQidDTO.getQid());
    UacQidDTO[] uacQidDTOList = new UacQidDTO[1];
    uacQidDTOList[0] = uacQidDTO;
    String uacQidDtoJson = objectMapper.writeValueAsString(uacQidDTOList);
    stubFor(
        get(urlPathEqualTo(MULTIPLE_QIDS_URL))
            .withQueryParam("questionnaireType", equalTo(Integer.toString(questionnaireType)))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody(uacQidDtoJson)));
    return uacQidDTO;
  }

  private ActionPlan setUpActionPlan() {
    ActionPlan actionPlan = new ActionPlan();
    actionPlan.setId(UUID.randomUUID());
    actionPlan.setDescription("Test Reminder Letters");
    actionPlan.setName("Test Reminder Letters");
    actionPlan.setActionRules(null);

    return actionPlanRepository.saveAndFlush(actionPlan);
  }

  private ActionRule setUpActionRule(ActionType actionType, ActionPlan actionPlan) {
    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setTriggerDateTime(OffsetDateTime.now());
    actionRule.setHasTriggered(false);
    actionRule.setActionType(actionType);
    actionRule.setActionPlan(actionPlan);

    actionRule.setClassifiersClause(" case_type != 'HI'");

    return actionRuleRepository.saveAndFlush(actionRule);
  }

  private Case setUpCase(ActionPlan actionPlan, Integer ceExpectedCapacity) {
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setActionPlanId(actionPlan.getId());
    randomCase.setReceiptReceived(false);
    randomCase.setRefusalReceived(null);
    randomCase.setAddressInvalid(false);
    randomCase.setTreatmentCode("HH_LF2R1E");
    randomCase.setCaseType("HH");
    randomCase.setRegion("E1000");
    randomCase.setFieldCoordinatorId("fieldCord1");
    randomCase.setFieldOfficerId("MrFieldOfficer");
    randomCase.setOrganisationName("Area51");
    randomCase.setCeExpectedCapacity(ceExpectedCapacity);
    randomCase.setSkeleton(false);
    return caseRepository.saveAndFlush(randomCase);
  }

  private Case setUpWelshCase(ActionPlan actionPlan, Integer ceExpectedCapacity) {
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setActionPlanId(actionPlan.getId());
    randomCase.setReceiptReceived(false);
    randomCase.setRefusalReceived(null);
    randomCase.setAddressInvalid(false);
    randomCase.setTreatmentCode("HH_LF2R1W");
    randomCase.setCaseType("HH");
    randomCase.setRegion("W1000");
    randomCase.setFieldCoordinatorId("fieldCord1");
    randomCase.setFieldOfficerId("MrFieldOfficer");
    randomCase.setOrganisationName("Area51");
    randomCase.setCeExpectedCapacity(ceExpectedCapacity);
    randomCase.setSkeleton(false);
    return caseRepository.saveAndFlush(randomCase);
  }
}

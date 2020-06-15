package uk.gov.ons.census.action.poller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.action.model.entity.ActionType.P_OR_HX;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
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

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private ActionRuleRepository actionRuleRepository;
  @Autowired private ActionPlanRepository actionPlanRepository;
  @Autowired private CaseToProcessRepository caseToProcessRepository;
  @Autowired private FulfilmentToProcessRepository fulfilmentToProcessRepository;

  private static final EasyRandom easyRandom = new EasyRandom();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Rule public WireMockRule mockCaseApi = new WireMockRule(wireMockConfig().port(8089));

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
    actionPlanRepository.deleteAll();
    actionRuleRepository.deleteAll();
    actionPlanRepository.deleteAllInBatch();
  }

  @Test
  public void testReminderLetterActionCreatesNewUac() throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCase(actionPlan, null);
    setUpActionRule(ActionType.P_RL_1RL1_1, actionPlan);

    // When the action plan triggers
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualMessage).isNotNull();
    PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

    assertThat(actualPrintFileDto.getActionType()).isEqualTo(ActionType.P_RL_1RL1_1.name());
    assertThat(actualPrintFileDto.getPackCode()).isEqualTo(ActionType.P_RL_1RL1_1.name());
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

  @Test
  public void testReminderQuestionnaireActionCreatesNewUac()
      throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCase(actionPlan, null);
    setUpActionRule(ActionType.P_QU_H1, actionPlan);

    // When the action plan triggers
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualMessage).isNotNull();
    PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);
    verify(getRequestedFor(urlPathEqualTo(MULTIPLE_QIDS_URL)));

    assertThat(actualPrintFileDto.getActionType()).isEqualTo(ActionType.P_QU_H1.name());
    assertThat(actualPrintFileDto.getPackCode()).isEqualTo(ActionType.P_QU_H1.name());
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

  @Test
  public void testWelshReminderQuestionnaireActionCreates2NewUacs()
      throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    UacQidDTO welshUacQidDto = uacQidDto;
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCase(actionPlan, null);
    setUpActionRule(ActionType.P_QU_H2, actionPlan);

    // When the action plan triggers
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualMessage).isNotNull();
    PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

    assertThat(actualPrintFileDto.getActionType()).isEqualTo(ActionType.P_QU_H2.name());
    assertThat(actualPrintFileDto.getPackCode()).isEqualTo(ActionType.P_QU_H2.name());
    assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
    assertThat(actualPrintFileDto.getUacWales()).isEqualTo(welshUacQidDto.getUac());
    assertThat(actualPrintFileDto.getQidWales()).isEqualTo(welshUacQidDto.getQid());
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

  @Test
  public void testWelshCeInitialContactQuestionnaireActionCreates2NewUacs()
      throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    UacQidDTO welshUacQidDto = uacQidDto;
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCeEstabWalesCase(actionPlan, 1);
    setUpActionRule(ActionType.CE_IC10, actionPlan);

    // When the action plan triggers
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualMessage).isNotNull();
    PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);

    assertThat(actualPrintFileDto.getActionType()).isEqualTo(ActionType.CE_IC10.name());
    assertThat(actualPrintFileDto.getPackCode()).isEqualTo(ActionType.CE_IC10.getPackCode());
    assertThat(actualPrintFileDto).isEqualToComparingOnlyGivenFields(uacQidDto, "uac", "qid");
    assertThat(actualPrintFileDto.getUacWales()).isEqualTo(welshUacQidDto.getUac());
    assertThat(actualPrintFileDto.getQidWales()).isEqualTo(welshUacQidDto.getQid());
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

  @Test
  public void testIndividualCaseReminderNotSent() throws InterruptedException {
    // Given we have an HI case with a valid Treatment Code.
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    setUpIndividualCase(actionPlan);
    setUpActionRule(ActionType.P_QU_H2, actionPlan);

    // When the action plan triggers
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertNull("Received Message for HI case, expected none", actualMessage);
  }

  @Test
  public void testFieldworkActionRule() throws IOException, InterruptedException {
    // Given
    BlockingQueue<String> fieldQueue = rabbitQueueHelper.listen(OUTBOUND_FIELD_QUEUE);
    BlockingQueue<String> caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);

    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCase(actionPlan, null);

    ActionRule actionRule = setUpActionRule(ActionType.FIELD, actionPlan);

    // When the action plan triggers
    String actualMessage = fieldQueue.poll(20, TimeUnit.SECONDS);
    String actualActionToCaseMessage = caseSelectedEventQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualMessage).isNotNull();
    FieldworkFollowup actualFieldworkFollowup =
        objectMapper.readValue(actualMessage, FieldworkFollowup.class);

    assertThat(actualFieldworkFollowup.getCaseRef())
        .isEqualTo(Long.toString(randomCase.getCaseRef()));

    assertThat(actualActionToCaseMessage).isNotNull();
    ResponseManagementEvent actualRmEvent =
        objectMapper.readValue(actualActionToCaseMessage, ResponseManagementEvent.class);
    assertThat(actualRmEvent.getEvent().getType()).isEqualTo(EventType.FIELD_CASE_SELECTED);
    assertThat(actualRmEvent.getPayload().getFieldCaseSelected().getCaseRef())
        .isEqualTo(randomCase.getCaseRef());
    assertThat(actualRmEvent.getPayload().getFieldCaseSelected().getActionRuleId())
        .isEqualTo(actionRule.getId().toString());

    assertThat(actualFieldworkFollowup.getCeActualResponses())
        .isEqualTo(randomCase.getCeActualResponses());
    assertThat(actualFieldworkFollowup.getHandDelivery()).isEqualTo(randomCase.isHandDelivery());
  }

  @Test
  public void testCeEstabActionRule() throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    ActionPlan actionPlan = setUpActionPlan();
    Case randomCase = setUpCase(actionPlan, 5);
    setUpActionRule(ActionType.CE_IC03, actionPlan);

    // Force the action rule to trigger

    // When the action plan triggers
    List<String> actualMessages = new LinkedList<>();
    for (int i = 0; i < 5; i++) {
      String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);
      assertThat(actualMessage).isNotNull();
      actualMessages.add(actualMessage);
    }

    // Then
    assertThat(actualMessages.size()).isEqualTo(5);

    for (String actualMessage : actualMessages) {
      PrintFileDto actualPrintFileDto = objectMapper.readValue(actualMessage, PrintFileDto.class);
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
  }

  @Test
  public void testFulfilment() throws IOException, InterruptedException {
    // Given
    UacQidDTO uacQidDto = stubCreateUacQid();
    BlockingQueue<String> printerQueue = rabbitQueueHelper.listen(OUTBOUND_PRINTER_QUEUE);
    BlockingQueue<String> caseSelectedEventQueue = rabbitQueueHelper.listen(ACTION_CASE_QUEUE);
    BlockingQueue<String> uacQidCreatedQueue = rabbitQueueHelper.listen(CASE_UAC_QID_CREATED_QUEUE);
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

    // When the fulfilment poller executes
    String actualMessage = printerQueue.poll(20, TimeUnit.SECONDS);
    String actualActionToCaseMessage = caseSelectedEventQueue.poll(20, TimeUnit.SECONDS);
    String actualUacQidCreateMessage = uacQidCreatedQueue.poll(20, TimeUnit.SECONDS);

    // Then
    assertThat(actualUacQidCreateMessage).isNotNull();
    ResponseManagementEvent actualRmUacQidCreateEvent =
        objectMapper.readValue(actualUacQidCreateMessage, ResponseManagementEvent.class);
    assertThat(actualRmUacQidCreateEvent.getEvent().getType()).isEqualTo(EventType.RM_UAC_CREATED);
    assertThat(actualRmUacQidCreateEvent.getPayload().getUacQidCreated().getCaseId())
        .isEqualTo(randomCase.getCaseId().toString());
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
    assertThat(actualPrintFileDto.getBatchQuantity()).isEqualTo(fulfilmentToProcess.getQuantity());
  }

  private UacQidDTO stubCreateUacQid() throws JsonProcessingException {
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    UacQidDTO[] uacQidDTOList = new UacQidDTO[1];
    uacQidDTOList[0] = uacQidDTO;
    String uacQidDtoJson = objectMapper.writeValueAsString(uacQidDTOList);
    stubFor(
        get(urlPathEqualTo(MULTIPLE_QIDS_URL))
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
    actionPlanRepository.saveAndFlush(actionPlan);
    return actionPlan;
  }

  private ActionRule setUpActionRule(ActionType actionType, ActionPlan actionPlan) {
    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setTriggerDateTime(OffsetDateTime.now());
    actionRule.setHasTriggered(false);
    actionRule.setActionType(actionType);
    actionRule.setActionPlan(actionPlan);

    Map<String, List<String>> classifiers = new HashMap<>();
    actionRule.setClassifiers(classifiers);

    actionRuleRepository.saveAndFlush(actionRule);
    return actionRule;
  }

  private Case setUpCase(ActionPlan actionPlan, Integer ceExpectedCapacity) {
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setActionPlanId(actionPlan.getId().toString());
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
    caseRepository.saveAndFlush(randomCase);
    return randomCase;
  }

  private Case setUpCeEstabWalesCase(ActionPlan actionPlan, Integer ceExpectedCapacity) {
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setActionPlanId(actionPlan.getId().toString());
    randomCase.setReceiptReceived(false);
    randomCase.setRefusalReceived(null);
    randomCase.setAddressInvalid(false);
    randomCase.setTreatmentCode("CE_QDIEW");
    randomCase.setCaseType("CE");
    randomCase.setRegion("W1000");
    randomCase.setFieldCoordinatorId("fieldCord1");
    randomCase.setFieldOfficerId("MrFieldOfficer");
    randomCase.setOrganisationName("Area51");
    randomCase.setCeExpectedCapacity(ceExpectedCapacity);
    randomCase.setSkeleton(false);
    caseRepository.saveAndFlush(randomCase);
    return randomCase;
  }

  private void setUpIndividualCase(ActionPlan actionPlan) {
    Case randomCase = easyRandom.nextObject(Case.class);
    randomCase.setActionPlanId(actionPlan.getId().toString());
    randomCase.setReceiptReceived(false);
    randomCase.setRefusalReceived(null);
    randomCase.setAddressInvalid(false);
    randomCase.setTreatmentCode("HH_LF2R1E");
    randomCase.setCaseType("HI");
    caseRepository.saveAndFlush(randomCase);
  }
}

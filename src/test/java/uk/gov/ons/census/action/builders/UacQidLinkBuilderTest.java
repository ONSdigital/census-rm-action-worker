package uk.gov.ons.census.action.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.action.builders.UacQidLinkBuilder.*;

import java.util.*;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uk.gov.ons.census.action.cache.UacQidCache;
import uk.gov.ons.census.action.model.UacQidTuple;
import uk.gov.ons.census.action.model.dto.EventType;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionPlan;
import uk.gov.ons.census.action.model.entity.ActionRule;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.UacQidLink;
import uk.gov.ons.census.action.model.repository.UacQidLinkRepository;

public class UacQidLinkBuilderTest {
  private static final String UAC_QID_CREATED_EXCHAGE = "test uac qid created exchange";

  private final UacQidLinkRepository uacQidLinkRepository = mock(UacQidLinkRepository.class);
  private final UacQidCache uacQidCache = mock(UacQidCache.class);
  private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

  private final UacQidLinkBuilder uacQidLinkBuilder =
      new UacQidLinkBuilder(
          uacQidLinkRepository, uacQidCache, rabbitTemplate, UAC_QID_CREATED_EXCHAGE);

  private static final String ENGLISH_QUESTIONNAIRE_TYPE = "01";
  private static final String WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE = "02";
  private static final String WALES_IN_WELSH_QUESTIONNAIRE_TYPE = "03";
  private static final String WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES = "22";
  private static final String WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES = "23";

  private static final EasyRandom easyRandom = new EasyRandom();

  @Test
  public void testEnglishAndWelshQidTupleReturned() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";
    String qidWal = "0320000002861455";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // when
    UacQidTuple uacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isFalse();

    UacQidLink actualWalesdUacQidLink = uacQidTuple.getUacQidLinkWales().get();
    assertThat(actualWalesdUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualWalesdUacQidLink.getQid()).isEqualTo(qidWal);
    assertThat(actualWalesdUacQidLink.getUac()).isEqualTo(uacWal);
    assertThat(actualWalesdUacQidLink.isActive()).isFalse();
  }

  @Test
  public void testEnglishAndWelshQidTupleReturnedForSpgCases() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseType("SPG");
    testCase.setRegion("W");
    testCase.setTreatmentCode("SPG_QDHUW");
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732666";
    String qidWal = "0320000002861777";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    UacQidDTO uacQidDTOEngland = new UacQidDTO();
    uacQidDTOEngland.setUac(uacEng);
    uacQidDTOEngland.setQid(qidEng);

    UacQidDTO uacQidDTOWales = new UacQidDTO();
    uacQidDTOWales.setUac(uacWal);
    uacQidDTOWales.setQid(qidWal);
    when(uacQidCache.getUacQidPair(anyInt()))
        .thenReturn(uacQidDTOEngland)
        .thenReturn(uacQidDTOWales);

    testCase.setTreatmentCode(
        SPG_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // when
    UacQidTuple uacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.SPG_IC14, UUID.randomUUID());

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isFalse();

    UacQidLink actualWalesdUacQidLink = uacQidTuple.getUacQidLinkWales().get();
    assertThat(actualWalesdUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualWalesdUacQidLink.getQid()).isEqualTo(qidWal);
    assertThat(actualWalesdUacQidLink.getUac()).isEqualTo(uacWal);
    assertThat(actualWalesdUacQidLink.isActive()).isFalse();
  }

  @Test
  public void testEnglishAndWelshQidTupleReturnedForCeCases() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setCaseType("CE");
    testCase.setRegion("W");
    testCase.setTreatmentCode("CE_QDIEW");
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "2220000010732199";
    String qidWal = "2320000002861455";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    UacQidDTO uacQidDTOEngland = new UacQidDTO();
    uacQidDTOEngland.setUac(uacEng);
    uacQidDTOEngland.setQid(qidEng);

    UacQidDTO uacQidDTOWales = new UacQidDTO();
    uacQidDTOWales.setUac(uacWal);
    uacQidDTOWales.setQid(qidWal);
    when(uacQidCache.getUacQidPair(anyInt()))
        .thenReturn(uacQidDTOEngland)
        .thenReturn(uacQidDTOWales);

    testCase.setTreatmentCode(
        CE_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // when
    UacQidTuple uacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.CE_IC10, UUID.randomUUID());

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isFalse();

    UacQidLink actualWalesdUacQidLink = uacQidTuple.getUacQidLinkWales().get();
    assertThat(actualWalesdUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualWalesdUacQidLink.getQid()).isEqualTo(qidWal);
    assertThat(actualWalesdUacQidLink.getUac()).isEqualTo(uacWal);
    assertThat(actualWalesdUacQidLink.isActive()).isFalse();
  }

  @Test
  public void testEnglishOnlyTupleReturned() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    testCase.setTreatmentCode("NotWelshTreatmentCode");

    // when
    UacQidTuple uacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICL1E, UUID.randomUUID());

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isFalse();

    assertThat(uacQidTuple.getUacQidLinkWales()).isNotPresent();
  }

  @Test(expected = RuntimeException.class)
  public void testWalesQuestionnaireWithTwoQidUacsWrongEnglish() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "9920000010732199";
    String qidWal = "0320000002861455";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    // Then
    // Exception thrown - expected
  }

  @Test(expected = RuntimeException.class)
  public void testWalesQuestionnaireWithTwoQidUacsWrongWelsh() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";
    String qidWal = "9920000002861455";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    // Then
    // Exception thrown - The second welsh QID must have questionnaire type "03"
  }

  @Test(expected = RuntimeException.class)
  public void testWalesQuestionnaireWithTooManyQidUacs() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";
    String qidWal = "0320000002861455";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    // add the 3rd and fatal Link
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    // Then
    // Exception thrown - expected
  }

  @Test(expected = RuntimeException.class)
  public void testWalesQuestionnaireWithMissingQidUac() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    // Then
    // Exception thrown - expected
  }

  @Test
  public void testIC02WorksForWelshQuestionnaireCECase() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacCE1Welsh = easyRandom.nextObject(String.class);
    String qidCE1Welsh = "3220000010732199";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId());
    uacQidLink.setUac(uacCE1Welsh);
    uacQidLink.setQid(qidCE1Welsh);
    uacQidLink.setActive(true);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId())).thenReturn(uacQidLinks);

    // We need to include welsh questionnaire treatment codes in the CE1 CE_IC02 letter run
    testCase.setTreatmentCode("CE_QDIEW");

    // When
    UacQidTuple uacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.CE1_IC02, UUID.randomUUID());

    // Then
    // The single CE1 QID pair is returned
    UacQidLink actualUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId());
    assertThat(actualUacQidLink.getQid()).isEqualTo(qidCE1Welsh);
    assertThat(actualUacQidLink.getUac()).isEqualTo(uacCE1Welsh);
    assertThat(actualUacQidLink.isActive()).isTrue();

    // There should not be a second dual language QID pair
    assertThat(uacQidTuple.getUacQidLinkWales()).isNotPresent();
  }

  @Test(expected = RuntimeException.class)
  public void testQidLinksEmpty() {
    // Given
    ActionPlan actionPlan = easyRandom.nextObject(ActionPlan.class);
    ActionRule actionRule = new ActionRule();
    actionRule.setActionPlan(actionPlan);
    actionRule.setActionType(ActionType.ICL1E);
    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setTreatmentCode("HH_QF2R1W");

    when(uacQidLinkRepository.findByCaseId(eq(testCase.getCaseId())))
        .thenReturn(Collections.EMPTY_LIST);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICL1E, UUID.randomUUID());

    // Then
    // Exception thrown - expected
  }

  @Test(expected = RuntimeException.class)
  public void testMultipleQidLinksAmbiguous() {
    // Given
    ActionPlan actionPlan = easyRandom.nextObject(ActionPlan.class);
    ActionRule actionRule = new ActionRule();
    actionRule.setActionPlan(actionPlan);
    actionRule.setActionType(ActionType.ICL1E);
    Case testCase = easyRandom.nextObject(Case.class);
    testCase.setTreatmentCode("HH_LF2R1E");
    String uacEng = easyRandom.nextObject(String.class);
    String uacWal = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";
    String qidWal = "0320000002861455";

    List<UacQidLink> uacQidLinks = new LinkedList<>();

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(eq(testCase.getCaseId()))).thenReturn(uacQidLinks);

    // When
    uacQidLinkBuilder.getUacQidLinks(testCase, ActionType.ICHHQW, UUID.randomUUID());

    // Then
    // Exception thrown - expected
  }

  @Test
  public void testNewUacIsRequestedForReminderLetter() {
    // Given
    Case linkedCase = easyRandom.nextObject(Case.class);
    linkedCase.setTreatmentCode("HH_LF2R1E");
    linkedCase.setRegion("E1000");
    linkedCase.setCaseType("HH");
    linkedCase.setAddressLevel("U");
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    UacQidTuple actualUacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(linkedCase, ActionType.P_RL_1RL1_1, UUID.randomUUID());

    // Then
    verify(uacQidCache).getUacQidPair(eq(Integer.parseInt(ENGLISH_QUESTIONNAIRE_TYPE)));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId()).isEqualTo(linkedCase.getCaseId());

    ArgumentCaptor<ResponseManagementEvent> rmEventArgCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(eq(UAC_QID_CREATED_EXCHAGE), eq(""), rmEventArgCaptor.capture());
    ResponseManagementEvent rmEvent = rmEventArgCaptor.getValue();
    assertThat(rmEvent.getEvent().getType()).isEqualTo(EventType.RM_UAC_CREATED);
    assertThat(rmEvent.getPayload().getUacQidCreated().getQid()).isEqualTo(uacQidDTO.getQid());
    assertThat(rmEvent.getPayload().getUacQidCreated().getUac()).isEqualTo(uacQidDTO.getUac());
    assertThat(rmEvent.getPayload().getUacQidCreated().getCaseId())
        .isEqualTo(linkedCase.getCaseId());
  }

  @Test
  public void testNewUacIsRequestedForReminderQuestionnaire() {
    // Given
    Case linkedCase = easyRandom.nextObject(Case.class);
    linkedCase.setTreatmentCode("HH_LF2R3BE");
    linkedCase.setRegion("E1000");
    linkedCase.setCaseType("HH");
    linkedCase.setAddressLevel("U");
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(uacQidCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    // When
    UacQidTuple actualUacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(linkedCase, ActionType.P_QU_H1, UUID.randomUUID());

    // Then
    verify(uacQidCache).getUacQidPair(eq(Integer.parseInt(ENGLISH_QUESTIONNAIRE_TYPE)));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId()).isEqualTo(linkedCase.getCaseId());

    ArgumentCaptor<ResponseManagementEvent> rmEventArgCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(eq(UAC_QID_CREATED_EXCHAGE), eq(""), rmEventArgCaptor.capture());
    ResponseManagementEvent rmEvent = rmEventArgCaptor.getValue();
    assertThat(rmEvent.getEvent().getType()).isEqualTo(EventType.RM_UAC_CREATED);
    assertThat(rmEvent.getPayload().getUacQidCreated().getQid()).isEqualTo(uacQidDTO.getQid());
    assertThat(rmEvent.getPayload().getUacQidCreated().getUac()).isEqualTo(uacQidDTO.getUac());
    assertThat(rmEvent.getPayload().getUacQidCreated().getCaseId())
        .isEqualTo(linkedCase.getCaseId());
  }

  @Test
  public void testNewUacIsRequestedForWelshReminderQuestionnaire() {
    // Given
    Case linkedCase = easyRandom.nextObject(Case.class);
    linkedCase.setTreatmentCode("HH_LF2R3BW");
    linkedCase.setRegion("W1000");
    linkedCase.setCaseType("HH");
    linkedCase.setAddressLevel("U");
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    UacQidDTO welshUacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(uacQidCache.getUacQidPair(eq(Integer.parseInt(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE))))
        .thenReturn(uacQidDTO);
    when(uacQidCache.getUacQidPair(eq(Integer.parseInt(WALES_IN_WELSH_QUESTIONNAIRE_TYPE))))
        .thenReturn(welshUacQidDTO);

    // When
    UacQidTuple actualUacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(linkedCase, ActionType.P_QU_H2, UUID.randomUUID());

    // Then
    verify(uacQidCache).getUacQidPair(eq(Integer.parseInt(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE)));
    verify(uacQidCache).getUacQidPair(eq(Integer.parseInt(WALES_IN_WELSH_QUESTIONNAIRE_TYPE)));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId()).isEqualTo(linkedCase.getCaseId());
    assertThat(actualUacQidTuple.getUacQidLinkWales().isPresent()).isTrue();
    assertThat(actualUacQidTuple.getUacQidLinkWales().get())
        .isEqualToComparingOnlyGivenFields(welshUacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLinkWales().get().getCaseId())
        .isEqualTo(linkedCase.getCaseId());

    verify(rabbitTemplate, times(2))
        .convertAndSend(eq(UAC_QID_CREATED_EXCHAGE), eq(""), any(ResponseManagementEvent.class));
  }

  @Test
  public void testNewUacIsRequestedForCeWelshInitialContactQuestionnaire() {
    // Given
    Case linkedCase = easyRandom.nextObject(Case.class);
    linkedCase.setTreatmentCode("CE_QDIEW");
    linkedCase.setRegion("W1000");
    linkedCase.setCaseType("CE");
    linkedCase.setAddressLevel("E");
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    UacQidDTO welshUacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(uacQidCache.getUacQidPair(
            eq(Integer.parseInt(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES))))
        .thenReturn(uacQidDTO);
    when(uacQidCache.getUacQidPair(
            eq(Integer.parseInt(WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES))))
        .thenReturn(welshUacQidDTO);

    // When
    UacQidTuple actualUacQidTuple =
        uacQidLinkBuilder.getUacQidLinks(linkedCase, ActionType.CE_IC10, UUID.randomUUID());

    // Then
    verify(uacQidCache)
        .getUacQidPair(eq(Integer.parseInt(WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES)));
    verify(uacQidCache)
        .getUacQidPair(eq(Integer.parseInt(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES)));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId()).isEqualTo(linkedCase.getCaseId());
    assertThat(actualUacQidTuple.getUacQidLinkWales().isPresent()).isTrue();
    assertThat(actualUacQidTuple.getUacQidLinkWales().get())
        .isEqualToComparingOnlyGivenFields(welshUacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLinkWales().get().getCaseId())
        .isEqualTo(linkedCase.getCaseId());

    verify(rabbitTemplate, times(2))
        .convertAndSend(eq(UAC_QID_CREATED_EXCHAGE), eq(""), any(ResponseManagementEvent.class));
  }

  @Test
  public void testValidQuestionnaireTypeEnglandHousehold() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("HH", "E1000", "U");

    // Then
    assertEquals("01", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesHousehold() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("HH", "W1000", "U");

    // Then
    assertEquals("02", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandHousehold() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("HH", "N1000", "U");

    // Then
    assertEquals("04", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandIndividual() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "E1000", "U");

    // Then
    assertEquals("21", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesIndividual() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "W1000", "U");

    // Then
    assertEquals("22", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandIndividual() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "N1000", "U");

    // Then
    assertEquals("24", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "E1000", "E");

    // Then
    assertEquals("31", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "W1000", "E");

    // Then
    assertEquals("32", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType =
        UacQidLinkBuilder.calculateQuestionnaireType("CE", "N1000", "E");

    // Then
    assertEquals("34", actualQuestionnaireType);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCountryTreatmentCode() {
    // Given

    // When
    UacQidLinkBuilder.calculateQuestionnaireType("CE", "Z1000", "U");

    // Then
    // Exception thrown - expected
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCaseType() {
    // Given

    // When
    UacQidLinkBuilder.calculateQuestionnaireType("ZZ", "E1000", "U");

    // Then
    // Exception thrown - expected
  }
}

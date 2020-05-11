package uk.gov.ons.census.action.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.action.builders.QidUacBuilder.*;

import java.util.*;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import uk.gov.ons.census.action.client.CaseClient;
import uk.gov.ons.census.action.model.UacQidTuple;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionPlan;
import uk.gov.ons.census.action.model.entity.ActionRule;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.UacQidLink;
import uk.gov.ons.census.action.model.repository.UacQidLinkRepository;

public class QidUacBuilderTest {
  private final UacQidLinkRepository uacQidLinkRepository = mock(UacQidLinkRepository.class);
  private final CaseClient caseClient = mock(CaseClient.class);

  private final QidUacBuilder qidUacBuilder = new QidUacBuilder(uacQidLinkRepository, caseClient);

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
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // when
    UacQidTuple uacQidTuple = qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId().toString());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isEqualTo(false);

    UacQidLink actualWalesdUacQidLink = uacQidTuple.getUacQidLinkWales().get();
    assertThat(actualWalesdUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId().toString());
    assertThat(actualWalesdUacQidLink.getQid()).isEqualTo(qidWal);
    assertThat(actualWalesdUacQidLink.getUac()).isEqualTo(uacWal);
    assertThat(actualWalesdUacQidLink.isActive()).isEqualTo(false);
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
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    UacQidDTO uacQidDTOEngland = new UacQidDTO();
    uacQidDTOEngland.setUac(uacEng);
    uacQidDTOEngland.setQid(qidEng);

    UacQidDTO uacQidDTOWales = new UacQidDTO();
    uacQidDTOWales.setUac(uacWal);
    uacQidDTOWales.setQid(qidWal);
    when(caseClient.getUacQid(any(UUID.class), anyString()))
        .thenReturn(uacQidDTOEngland)
        .thenReturn(uacQidDTOWales);

    testCase.setTreatmentCode(
        CE_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // when
    UacQidTuple uacQidTuple = qidUacBuilder.getUacQidLinks(testCase, ActionType.CE_IC10);

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId().toString());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isEqualTo(false);

    UacQidLink actualWalesdUacQidLink = uacQidTuple.getUacQidLinkWales().get();
    assertThat(actualWalesdUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId().toString());
    assertThat(actualWalesdUacQidLink.getQid()).isEqualTo(qidWal);
    assertThat(actualWalesdUacQidLink.getUac()).isEqualTo(uacWal);
    assertThat(actualWalesdUacQidLink.isActive()).isEqualTo(false);
  }

  @Test
  public void testEnglishOnlyTupleReturned() {
    // Given
    Case testCase = easyRandom.nextObject(Case.class);
    String uacEng = easyRandom.nextObject(String.class);
    String qidEng = "0220000010732199";

    List<UacQidLink> uacQidLinks = new ArrayList<>();
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    testCase.setTreatmentCode("NotWelshTreatmentCode");

    // when
    UacQidTuple uacQidTuple = qidUacBuilder.getUacQidLinks(testCase, ActionType.ICL1E);

    UacQidLink actualEnglandUacQidLink = uacQidTuple.getUacQidLink();
    assertThat(actualEnglandUacQidLink.getCaseId()).isEqualTo(testCase.getCaseId().toString());
    assertThat(actualEnglandUacQidLink.getQid()).isEqualTo(qidEng);
    assertThat(actualEnglandUacQidLink.getUac()).isEqualTo(uacEng);
    assertThat(actualEnglandUacQidLink.isActive()).isEqualTo(false);

    assertThat(uacQidTuple.getUacQidLinkWales().isPresent()).isEqualTo(false);
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

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

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

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

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
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    uacQidLink = new UacQidLink();
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacWal);
    uacQidLink.setQid(qidWal);
    uacQidLinks.add(uacQidLink);

    // add the 3rd and fatal Link
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

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
    uacQidLink.setCaseId(testCase.getCaseId().toString());
    uacQidLink.setUac(uacEng);
    uacQidLink.setQid(qidEng);
    uacQidLinks.add(uacQidLink);

    when(uacQidLinkRepository.findByCaseId(testCase.getCaseId().toString()))
        .thenReturn(uacQidLinks);

    testCase.setTreatmentCode(
        HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX
            + "BLAH"
            + WALES_TREATMENT_CODE_SUFFIX);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

    // Then
    // Exception thrown - expected
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

    when(uacQidLinkRepository.findByCaseId(eq(testCase.getCaseId().toString())))
        .thenReturn(Collections.EMPTY_LIST);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICL1E);

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

    when(uacQidLinkRepository.findByCaseId(eq(testCase.getCaseId().toString())))
        .thenReturn(uacQidLinks);

    // When
    qidUacBuilder.getUacQidLinks(testCase, ActionType.ICHHQW);

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
    when(caseClient.getUacQid(eq(linkedCase.getCaseId()), eq(ENGLISH_QUESTIONNAIRE_TYPE)))
        .thenReturn(uacQidDTO);

    // When
    UacQidTuple actualUacQidTuple =
        qidUacBuilder.getUacQidLinks(linkedCase, ActionType.P_RL_1RL1_1);

    // Then
    verify(caseClient).getUacQid(eq(linkedCase.getCaseId()), eq(ENGLISH_QUESTIONNAIRE_TYPE));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
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
    when(caseClient.getUacQid(eq(linkedCase.getCaseId()), eq(ENGLISH_QUESTIONNAIRE_TYPE)))
        .thenReturn(uacQidDTO);

    // When
    UacQidTuple actualUacQidTuple = qidUacBuilder.getUacQidLinks(linkedCase, ActionType.P_QU_H1);

    // Then
    verify(caseClient).getUacQid(eq(linkedCase.getCaseId()), eq(ENGLISH_QUESTIONNAIRE_TYPE));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
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
    when(caseClient.getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE)))
        .thenReturn(uacQidDTO);
    when(caseClient.getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_WELSH_QUESTIONNAIRE_TYPE)))
        .thenReturn(welshUacQidDTO);

    // When
    UacQidTuple actualUacQidTuple = qidUacBuilder.getUacQidLinks(linkedCase, ActionType.P_QU_H2);

    // Then
    verify(caseClient)
        .getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE));
    verify(caseClient).getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_WELSH_QUESTIONNAIRE_TYPE));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
    assertThat(actualUacQidTuple.getUacQidLinkWales().isPresent()).isTrue();
    assertThat(actualUacQidTuple.getUacQidLinkWales().get())
        .isEqualToComparingOnlyGivenFields(welshUacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLinkWales().get().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
  }

  @Test
  public void testNewUacIsRequestedForWelshInitialContactQuestionnaire() {
    // Given
    Case linkedCase = easyRandom.nextObject(Case.class);
    linkedCase.setTreatmentCode("CE_QDIEE");
    linkedCase.setRegion("W1000");
    linkedCase.setCaseType("CE");
    linkedCase.setAddressLevel("E");
    UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    UacQidDTO welshUacQidDTO = easyRandom.nextObject(UacQidDTO.class);
    when(caseClient.getUacQid(
            eq(linkedCase.getCaseId()), eq(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES)))
        .thenReturn(uacQidDTO);
    when(caseClient.getUacQid(
            eq(linkedCase.getCaseId()), eq(WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES)))
        .thenReturn(welshUacQidDTO);

    // When
    UacQidTuple actualUacQidTuple = qidUacBuilder.getUacQidLinks(linkedCase, ActionType.CE_IC10);

    // Then
    verify(caseClient)
        .getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES));
    verify(caseClient)
        .getUacQid(eq(linkedCase.getCaseId()), eq(WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES));
    assertThat(actualUacQidTuple.getUacQidLink())
        .isEqualToComparingOnlyGivenFields(uacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLink().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
    assertThat(actualUacQidTuple.getUacQidLinkWales().isPresent()).isTrue();
    assertThat(actualUacQidTuple.getUacQidLinkWales().get())
        .isEqualToComparingOnlyGivenFields(welshUacQidDTO, "uac", "qid");
    assertThat(actualUacQidTuple.getUacQidLinkWales().get().getCaseId())
        .isEqualTo(linkedCase.getCaseId().toString());
  }

  @Test
  public void testValidQuestionnaireTypeEnglandHousehold() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("HH", "E1000", "U");

    // Then
    assertEquals("01", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesHousehold() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("HH", "W1000", "U");

    // Then
    assertEquals("02", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandHousehold() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("HH", "N1000", "U");

    // Then
    assertEquals("04", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandIndividual() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "E1000", "U");

    // Then
    assertEquals("21", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesIndividual() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "W1000", "U");

    // Then
    assertEquals("22", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandIndividual() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "N1000", "U");

    // Then
    assertEquals("24", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeEnglandCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "E1000", "E");

    // Then
    assertEquals("31", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeWalesCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "W1000", "E");

    // Then
    assertEquals("32", actualQuestionnaireType);
  }

  @Test
  public void testValidQuestionnaireTypeNorthernIrelandCommunalEstablishment() {
    // Given

    // When
    String actualQuestionnaireType = QidUacBuilder.calculateQuestionnaireType("CE", "N1000", "E");

    // Then
    assertEquals("34", actualQuestionnaireType);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCountryTreatmentCode() {
    // Given

    // When
    QidUacBuilder.calculateQuestionnaireType("CE", "Z1000", "U");

    // Then
    // Exception thrown - expected
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidCaseType() {
    // Given

    // When
    QidUacBuilder.calculateQuestionnaireType("ZZ", "E1000", "U");

    // Then
    // Exception thrown - expected
  }
}

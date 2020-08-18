package uk.gov.ons.census.action.builders;

import static uk.gov.ons.census.action.model.dto.EventType.RM_UAC_CREATED;
import static uk.gov.ons.census.action.utility.ActionTypeHelper.isExpectedCapacityActionType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.cache.UacQidCache;
import uk.gov.ons.census.action.model.UacQidTuple;
import uk.gov.ons.census.action.model.dto.Event;
import uk.gov.ons.census.action.model.dto.Payload;
import uk.gov.ons.census.action.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.action.model.dto.UacQidCreated;
import uk.gov.ons.census.action.model.dto.UacQidDTO;
import uk.gov.ons.census.action.model.entity.ActionType;
import uk.gov.ons.census.action.model.entity.Case;
import uk.gov.ons.census.action.model.entity.UacQidLink;
import uk.gov.ons.census.action.model.repository.UacQidLinkRepository;

@Component
public class UacQidLinkBuilder {
  private static final Set<ActionType> initialContactNotExpectedCapacityActionTypes =
      Set.of(
          ActionType.ICHHQE,
          ActionType.ICHHQW,
          ActionType.ICHHQN,
          ActionType.ICL1E,
          ActionType.ICL2W,
          ActionType.ICL4N,
          ActionType.CE1_IC01,
          ActionType.CE1_IC02,
          ActionType.SPG_IC11,
          ActionType.SPG_IC12,
          ActionType.SPG_IC13,
          ActionType.SPG_IC14
          // This list is only for INITIAL CONTACT letters/questionnaires that are not part of
          // expected capacity for the case. You should only add to it if you are certain that you
          // are adding some new initial contact printed materials which is unlikely. For security
          // reasons, initial contact UACs should never be mailed out a second time, because some
          // respondents will have partially completed their EQs.
          );

  private static final String ADDRESS_LEVEL_ESTAB = "E";

  private static final String COUNTRY_CODE_ENGLAND = "E";
  private static final String COUNTRY_CODE_WALES = "W";
  private static final String COUNTRY_CODE_NORTHERN_IRELAND = "N";

  private static final String CASE_TYPE_HOUSEHOLD = "HH";
  private static final String CASE_TYPE_SPG = "SPG";
  private static final String CASE_TYPE_CE = "CE";

  private static final int NUM_OF_UAC_QID_PAIRS_NEEDED_BY_A_WALES_INITIAL_CONTACT_QUESTIONNAIRE = 2;
  private static final int NUM_OF_UAC_QID_PAIRS_NEEDED_FOR_SINGLE_LANGUAGE = 1;
  private static final String WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE = "02";
  private static final String WALES_IN_WELSH_QUESTIONNAIRE_TYPE = "03";
  private static final String WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES = "22";
  private static final String WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES = "23";
  public static final String HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX = "HH_Q";
  public static final String CE_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX = "CE_Q";
  public static final String SPG_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX = "SPG_Q";
  public static final String WALES_TREATMENT_CODE_SUFFIX = "W";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final UacQidCache uacQidCache;
  private final RabbitTemplate rabbitTemplate;
  private final String uacQidCreatedExchange;

  public UacQidLinkBuilder(
      UacQidLinkRepository uacQidLinkRepository,
      UacQidCache uacQidCache,
      RabbitTemplate rabbitTemplate,
      @Value("${queueconfig.uac-qid-created-exchange}") String uacQidCreatedExchange) {
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.uacQidCache = uacQidCache;
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidCreatedExchange = uacQidCreatedExchange;
  }

  public UacQidTuple getUacQidLinks(
      Case linkedCase, ActionType actionType, UUID actionRuleOrFulfilmentBatchId) {
    if (isInitialContactNotExpectedCapacityActionType(actionType)) {
      return fetchExistingUacQidPairsForAction(linkedCase, actionType);
    } else if (isExpectedCapacityActionType(actionType)) {
      // We override the address level for these action types because we want to create individual
      // uac qid pairs
      return createNewUacQidPairsForAction(
          linkedCase, actionType, "U", actionRuleOrFulfilmentBatchId);
    } else {
      return createNewUacQidPairsForAction(linkedCase, actionType, actionRuleOrFulfilmentBatchId);
    }
  }

  public UacQidLink createNewUacQidPair(
      Case linkedCase, String questionnaireType, UUID actionRuleOrFulfilmentBatchId) {
    UacQidDTO newUacQidPair = uacQidCache.getUacQidPair(Integer.parseInt(questionnaireType));
    UacQidCreated uacQidCreated = new UacQidCreated();
    uacQidCreated.setCaseId(linkedCase.getCaseId());
    uacQidCreated.setBatchId(actionRuleOrFulfilmentBatchId);
    uacQidCreated.setQid(newUacQidPair.getQid());
    uacQidCreated.setUac(newUacQidPair.getUac());

    Event event = new Event();
    event.setType(RM_UAC_CREATED);
    event.setDateTime(OffsetDateTime.now());
    event.setTransactionId(UUID.randomUUID().toString());
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(event);
    Payload payload = new Payload();
    payload.setUacQidCreated(uacQidCreated);
    responseManagementEvent.setPayload(payload);

    // This message to Case Processor will ensure the UAC-QID is persisted: eventual consistency
    rabbitTemplate.convertAndSend(uacQidCreatedExchange, "", responseManagementEvent);

    // Build a non-persisted object which can be used for immediate processing
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(newUacQidPair.getQid());
    uacQidLink.setUac(newUacQidPair.getUac());
    uacQidLink.setCaseId(linkedCase.getCaseId());
    return uacQidLink;
  }

  private UacQidTuple fetchExistingUacQidPairsForAction(Case linkedCase, ActionType actionType) {
    UUID caseId = linkedCase.getCaseId();

    List<UacQidLink> uacQidLinks = uacQidLinkRepository.findByCaseId(caseId);

    if (uacQidLinks == null || uacQidLinks.isEmpty()) {
      throw new RuntimeException(
          String.format("We can't process this case id '%s' with no UACs", caseId));

    } else if (actionType == ActionType.ICHHQW || actionType == ActionType.SPG_IC14) {
      if (isStateCorrectForSecondWelshUacQidPair(linkedCase, uacQidLinks)) {
        return getUacQidTupleWithSecondWelshPair(uacQidLinks, actionType);
      }

    } else if (isStateCorrectForSingleUacQidPair(linkedCase, uacQidLinks, actionType)) {
      return getUacQidTupleWithSinglePair(uacQidLinks);
    }

    throw new RuntimeException(
        String.format("Wrong number of UACs for treatment code '%s'", actionType));
  }

  private boolean isStateCorrectForSingleUacQidPair(
      Case linkedCase, List<UacQidLink> uacQidLinks, ActionType actionType) {
    return (!isQuestionnaireWelsh(linkedCase.getTreatmentCode())
            // CE_IC02 is single QID letter but includes welsh questionnaire treatment codes
            || actionType == ActionType.CE1_IC02)
        && uacQidLinks.size() == NUM_OF_UAC_QID_PAIRS_NEEDED_FOR_SINGLE_LANGUAGE;
  }

  private boolean isStateCorrectForSecondWelshUacQidPair(
      Case linkedCase, List<UacQidLink> uacQidLinks) {
    return isQuestionnaireWelsh(linkedCase.getTreatmentCode())
        && uacQidLinks.size()
            == NUM_OF_UAC_QID_PAIRS_NEEDED_BY_A_WALES_INITIAL_CONTACT_QUESTIONNAIRE;
  }

  private UacQidTuple getUacQidTupleWithSinglePair(List<UacQidLink> uacQidLinks) {
    UacQidTuple uacQidTuple = new UacQidTuple();
    uacQidTuple.setUacQidLink(uacQidLinks.get(0));
    return uacQidTuple;
  }

  private UacQidTuple getUacQidTupleWithSecondWelshPair(
      List<UacQidLink> uacQidLinks, ActionType actionType) {
    UacQidTuple uacQidTuple = new UacQidTuple();
    String primaryQuestionnaireType = WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE;
    String secondaryQuestionnaireType = WALES_IN_WELSH_QUESTIONNAIRE_TYPE;

    if (actionType.equals(ActionType.CE_IC10)) {
      primaryQuestionnaireType = WALES_IN_ENGLISH_QUESTIONNAIRE_TYPE_CE_CASES;
      secondaryQuestionnaireType = WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES;
    }

    uacQidTuple.setUacQidLink(
        getSpecificUacQidLinkByQuestionnaireType(
            uacQidLinks, primaryQuestionnaireType, secondaryQuestionnaireType));
    uacQidTuple.setUacQidLinkWales(
        Optional.of(
            getSpecificUacQidLinkByQuestionnaireType(
                uacQidLinks, secondaryQuestionnaireType, primaryQuestionnaireType)));
    return uacQidTuple;
  }

  private UacQidTuple createNewUacQidPairsForAction(
      Case linkedCase,
      ActionType actionType,
      String addressLevel,
      UUID actionRuleOrFulfilmentBatchId) {
    UacQidTuple uacQidTuple = new UacQidTuple();
    String questionnaireType =
        calculateQuestionnaireType(linkedCase.getCaseType(), linkedCase.getRegion(), addressLevel);

    uacQidTuple.setUacQidLink(
        createNewUacQidPair(linkedCase, questionnaireType, actionRuleOrFulfilmentBatchId));
    if (actionType.equals(ActionType.P_QU_H2)) {
      uacQidTuple.setUacQidLinkWales(
          Optional.of(
              createNewUacQidPair(
                  linkedCase, WALES_IN_WELSH_QUESTIONNAIRE_TYPE, actionRuleOrFulfilmentBatchId)));
    } else if (actionType.equals(ActionType.CE_IC10)) {
      uacQidTuple.setUacQidLinkWales(
          Optional.of(
              createNewUacQidPair(
                  linkedCase,
                  WALES_IN_WELSH_QUESTIONNAIRE_TYPE_CE_CASES,
                  actionRuleOrFulfilmentBatchId)));
    }
    return uacQidTuple;
  }

  private UacQidTuple createNewUacQidPairsForAction(
      Case linkedCase, ActionType actionType, UUID actionRuleOrFulfilmentBatchId) {
    return createNewUacQidPairsForAction(
        linkedCase, actionType, linkedCase.getAddressLevel(), actionRuleOrFulfilmentBatchId);
  }

  private boolean isQuestionnaireWelsh(String treatmentCode) {
    return ((treatmentCode.startsWith(HOUSEHOLD_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX)
            || treatmentCode.startsWith(CE_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX)
            || treatmentCode.startsWith(SPG_INITIAL_CONTACT_QUESTIONNAIRE_TREATMENT_CODE_PREFIX))
        && treatmentCode.endsWith(WALES_TREATMENT_CODE_SUFFIX));
  }

  private UacQidLink getSpecificUacQidLinkByQuestionnaireType(
      List<UacQidLink> uacQidLinks,
      String wantedQuestionnaireType,
      String otherAllowableQuestionnaireType) {
    for (UacQidLink uacQidLink : uacQidLinks) {
      if (uacQidLink.getQid().startsWith(wantedQuestionnaireType)) {
        return uacQidLink;
      } else if (!uacQidLink.getQid().startsWith(otherAllowableQuestionnaireType)) {
        throw new RuntimeException(
            String.format("Non allowable type  '%s' on case", uacQidLink.getQid()));
      }
    }

    throw new RuntimeException(
        String.format("Can't find UAC QID '%s' for case", otherAllowableQuestionnaireType));
  }

  private boolean isInitialContactNotExpectedCapacityActionType(ActionType actionType) {
    return initialContactNotExpectedCapacityActionTypes.contains(actionType);
  }

  public static String calculateQuestionnaireType(
      String caseType, String region, String addressLevel) {
    String country = region.substring(0, 1);
    if (!country.equals(COUNTRY_CODE_ENGLAND)
        && !country.equals(COUNTRY_CODE_WALES)
        && !country.equals(COUNTRY_CODE_NORTHERN_IRELAND)) {
      throw new IllegalArgumentException(String.format("Unknown Country: %s", caseType));
    }

    if (isCeCaseType(caseType) && addressLevel.equals("U")) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return "21";
        case COUNTRY_CODE_WALES:
          return "22";
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return "24";
      }
    } else if (isHouseholdCaseType(caseType) || isSpgCaseType(caseType)) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return "01";
        case COUNTRY_CODE_WALES:
          return "02";
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return "04";
      }
    } else if (isCE1RequestForEstabCeCase(caseType, addressLevel)) {
      switch (country) {
        case COUNTRY_CODE_ENGLAND:
          return "31";
        case COUNTRY_CODE_WALES:
          return "32";
        case COUNTRY_CODE_NORTHERN_IRELAND:
          return "34";
      }
    } else {
      throw new IllegalArgumentException(String.format("Unexpected Case Type: %s", caseType));
    }

    throw new RuntimeException(String.format("Unprocessable Case Type '%s'", caseType));
  }

  private static boolean isCE1RequestForEstabCeCase(String caseType, String addressLevel) {
    return isCeCaseType(caseType) && addressLevel.equals(ADDRESS_LEVEL_ESTAB);
  }

  private static boolean isSpgCaseType(String caseType) {
    return caseType.equals(CASE_TYPE_SPG);
  }

  private static boolean isHouseholdCaseType(String caseType) {
    return caseType.equals(CASE_TYPE_HOUSEHOLD);
  }

  private static boolean isCeCaseType(String caseType) {
    return caseType.equals(CASE_TYPE_CE);
  }
}

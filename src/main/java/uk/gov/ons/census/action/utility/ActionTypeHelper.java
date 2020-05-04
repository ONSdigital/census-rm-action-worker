package uk.gov.ons.census.action.utility;

import java.util.Set;
import uk.gov.ons.census.action.model.entity.ActionType;

public class ActionTypeHelper {
  private static final Set<ActionType> ceIndividualActionTypes =
      Set.of(
          ActionType.CE_IC03,
          ActionType.CE_IC04,
          ActionType.CE_IC05,
          ActionType.CE_IC06,
          ActionType.CE_IC08,
          ActionType.CE_IC09,
          ActionType.CE_IC10);

  public static boolean isCeIndividualActionType(ActionType actionType) {
    return ceIndividualActionTypes.contains(actionType);
  }
}

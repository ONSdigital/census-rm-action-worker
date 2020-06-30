package uk.gov.ons.census.action.utility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.ons.census.action.utility.ActionTypeHelper.isExpectedCapacityActionType;

import org.junit.Test;
import uk.gov.ons.census.action.model.entity.ActionType;

public class ActionTypeHelperTest {

  @Test
  public void testIsCeIndividualActionTypeIsTrue() {
    boolean testActionType = isExpectedCapacityActionType(ActionType.CE_IC03);

    assertTrue(testActionType);
  }

  @Test
  public void testIsCeIndividualActionTypeIsFalse() {
    boolean testActionType = isExpectedCapacityActionType(ActionType.ICL1E);

    assertFalse(testActionType);
  }
}

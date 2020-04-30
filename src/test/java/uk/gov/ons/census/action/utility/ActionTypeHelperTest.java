package uk.gov.ons.census.action.utility;

import org.junit.Test;
import uk.gov.ons.census.action.model.entity.ActionType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.ons.census.action.utility.ActionTypeHelper.isCeIndividualActionType;

public class ActionTypeHelperTest {

  @Test
  public void testIsCeIndividualActionTypeIsTrue(){
    boolean testActionType = isCeIndividualActionType(ActionType.CE_IC03);

    assertTrue(testActionType);
  }

  @Test
  public void testIsCeIndividualActionTypeIsFalse(){
    boolean testActionType = isCeIndividualActionType(ActionType.ICL1E);

    assertFalse(testActionType);
  }
}

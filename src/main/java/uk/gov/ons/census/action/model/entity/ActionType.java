package uk.gov.ons.census.action.model.entity;

public enum ActionType {
  // Initial contact letters
  ICL1E(ActionHandler.PRINTER, "P_IC_ICL1"), // Census initial contact letter for England
  ICL2W(ActionHandler.PRINTER, "P_IC_ICL2B"), // Census initial contact letter for Wales
  ICL4N(ActionHandler.PRINTER, "P_IC_ICL4"), // Census initial contact letter for NI

  // Initial contact questionnaires
  ICHHQE(ActionHandler.PRINTER, "P_IC_H1"), // Census household questionnaire for England
  ICHHQW(ActionHandler.PRINTER, "P_IC_H2"), // Census household questionnaire for Wales
  ICHHQN(ActionHandler.PRINTER, "P_IC_H4"), // Census household questionnaire for NI

  // CE1 for initial contact
  CE1_IC01(ActionHandler.PRINTER),
  CE1_IC02(ActionHandler.PRINTER),

  // Generic actionType for use in Fieldwork followup action rules, tranches
  FIELD(ActionHandler.FIELD),

  // Reminder letters
  P_RL_1RL1_1(ActionHandler.PRINTER), // 1st Reminder, Letter - for England addresses
  P_RL_1RL2B_1(
      ActionHandler
          .PRINTER), // 1st Reminder, Letter - for Wales addresses (bilingual Welsh and English)
  P_RL_1RL4(ActionHandler.PRINTER), // 1st Reminder, Letter - for Ireland addresses
  P_RL_1RL1_2(ActionHandler.PRINTER), // 2nd Reminder, Letter - for England addresses
  P_RL_1RL2B_2(
      ActionHandler
          .PRINTER), // 2nd Reminder, Letter - for Wales addresses (bilingual Welsh and English)
  P_RL_2RL1_3a(ActionHandler.PRINTER), // 3rd Reminder, Letter - for England addresses
  P_RL_2RL2B_3a(ActionHandler.PRINTER), // 3rd Reminder, Letter - for Wales addresses

  // Reminder questionnaires
  P_QU_H1(ActionHandler.PRINTER),
  P_QU_H2(ActionHandler.PRINTER),
  P_QU_H4(ActionHandler.PRINTER),

  // Ad hoc fulfilment requests
  P_OR_HX(ActionHandler.PRINTER), // Household questionnaires
  P_LP_HLX(ActionHandler.PRINTER), // Household questionnaires large print
  P_TB_TBX(ActionHandler.PRINTER), // Household translation booklets

  P_OR_IX(ActionHandler.PRINTER), // Individual Response questionnaire print

  //  response driven interventions
  P_RD_2RL1_1(ActionHandler.PRINTER), // Response driven reminder group 1 English
  P_RD_2RL2B_1(ActionHandler.PRINTER), // Response driven reminder group 1 Welsh
  P_RD_2RL1_2(ActionHandler.PRINTER), // Response driven reminder group 2 English
  P_RD_2RL2B_2(ActionHandler.PRINTER), // Response driven reminder group 2 Welsh
  P_RD_2RL1_3(ActionHandler.PRINTER), // Response driven reminder group 3 English
  P_RD_2RL2B_3(ActionHandler.PRINTER); // Response driven reminder group 3 Welsh

  private final ActionHandler handler;
  private final String packCode;

  ActionType(ActionHandler handler) {
    this.handler = handler;
    this.packCode = this.name();
  }

  ActionType(ActionHandler handler, String packCode) {
    this.handler = handler;
    this.packCode = packCode;
  }

  public ActionHandler getHandler() {
    return handler;
  }

  public String getPackCode() {
    return packCode;
  }
}

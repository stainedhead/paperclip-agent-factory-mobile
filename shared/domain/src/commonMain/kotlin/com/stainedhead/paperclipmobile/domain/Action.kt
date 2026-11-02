package com.stainedhead.paperclipmobile.domain

/** Whether an action changes data in a company on the server or only this device's configuration. */
enum class ActionScope {
    Company,
    Local,
}

/** Every action the app can take that changes something. Used by [Permissions] and [DeviceAuthPolicy]. */
enum class Action(
    val scope: ActionScope,
) {
    // Attention
    SnoozeItem(ActionScope.Company),
    DismissItem(ActionScope.Company),
    UndoDismiss(ActionScope.Company),
    SetDecideBy(ActionScope.Company),

    // Decisions
    DecideOption(ActionScope.Company),
    DecideDestructiveOption(ActionScope.Company),
    DismissDecision(ActionScope.Company),

    // Approvals
    ApproveApproval(ActionScope.Company),
    RejectApproval(ActionScope.Company),
    RequestApprovalRevision(ActionScope.Company),
    CommentOnApproval(ActionScope.Company),

    // Budgets
    KeepBudgetPaused(ActionScope.Company),
    RaiseBudgetAndResume(ActionScope.Company),
    SetAgentBudget(ActionScope.Company),

    // Work
    CommentOnIssue(ActionScope.Company),
    ChangeIssueStatus(ActionScope.Company),
    AssignIssue(ActionScope.Company),
    CreateIssue(ActionScope.Company),
    RespondToInteraction(ActionScope.Company),

    // Agents and runs
    PauseAgent(ActionScope.Company),
    ResumeAgent(ActionScope.Company),
    RunAgentNow(ActionScope.Company),
    CancelRun(ActionScope.Company),

    // Device-local: never a company mutation, so open to every role
    AddFactory(ActionScope.Local),
    EditFactory(ActionScope.Local),
    RemoveFactory(ActionScope.Local),
    SignOut(ActionScope.Local),
    RevokeBoardKey(ActionScope.Local),
    UpdateSettings(ActionScope.Local),
}

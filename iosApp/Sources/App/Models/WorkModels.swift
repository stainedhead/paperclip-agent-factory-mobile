import PaperclipKit

/// Screen 10's adapter.
typealias WorkListModel = HolderModel<WorkListStateHolder>

extension HolderModel where Holder == WorkListStateHolder {
    func refresh() { send(WorkListIntentRefresh.shared) }
    func select(_ filter: IssueFilterUi) { send(WorkListIntentFilterSelected(filter: filter)) }
    /// The text of the search box; blank clears the search.
    func search(_ text: String) { send(WorkListIntentSearchChanged(text: text)) }
    func loadMore() { send(WorkListIntentLoadMore.shared) }
    func create(title: String, priority: IssuePriorityUi) {
        send(WorkListIntentCreateSubmitted(title: title, priority: priority))
    }
    func createdAcknowledged() { send(WorkListIntentCreatedAcknowledged.shared) }
    func dismissMessage() { send(WorkListIntentMessageDismissed.shared) }
}

/// Screen 4's adapter (named for the screen: the shared issue model behind it is internal to Kotlin).
typealias IssueScreenModel = HolderModel<IssueStateHolder>

extension HolderModel where Holder == IssueStateHolder {
    func refresh() { send(IssueIntentRefresh.shared) }
    func select(_ tab: IssueTab) { send(IssueIntentTabSelected(tab: tab)) }
    func comment(_ body: String, reopen: Bool, interrupt: Bool) {
        send(IssueIntentCommentSubmitted(body: body, reopen: reopen, interrupt: interrupt))
    }
    func setStatus(_ status: IssueStatusUi) { send(IssueIntentStatusSelected(status: status)) }
    func assign(_ kind: AssigneeKindUi, id: String?) { send(IssueIntentAssigneeSelected(kind: kind, id: id)) }
    func addSubtask(_ title: String) { send(IssueIntentSubtaskSubmitted(title: title)) }
    func primary(_ interactionId: String) { send(IssueIntentInteractionPrimaryTapped(interactionId: interactionId)) }
    func reject(_ interactionId: String) { send(IssueIntentInteractionRejectTapped(interactionId: interactionId)) }
    func toggle(_ interactionId: String, group: String, option: String) {
        send(IssueIntentInteractionChoiceToggled(interactionId: interactionId, groupId: group, optionId: option))
    }
    func text(_ interactionId: String, target: String, _ text: String) {
        send(IssueIntentInteractionTextChanged(interactionId: interactionId, targetId: target, text: text))
    }
    func verdict(_ interactionId: String, item: String, _ verdict: VerdictUi) {
        send(IssueIntentInteractionVerdictChosen(interactionId: interactionId, itemId: item, verdict: verdict))
    }
    func dismissMessage() { send(IssueIntentMessageDismissed.shared) }
}

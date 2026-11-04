import PaperclipKit

/// Screen 1's adapter.
typealias InboxModel = HolderModel<InboxStateHolder>

extension HolderModel where Holder == InboxStateHolder {
    func refresh() { send(InboxIntentRefresh.shared) }
    func loadMore() { send(InboxIntentLoadMore.shared) }
    /// `nil` shows every kind.
    func select(_ kind: SourceKindUi?) { send(InboxIntentFilterSelected(kind: kind)) }
    func snooze(_ itemId: String, preset: SnoozePresetUi) {
        send(InboxIntentSnoozeRequested(itemId: itemId, preset: preset))
    }
    func dismiss(_ itemId: String) { send(InboxIntentDismissTapped(itemId: itemId)) }
    func undo() { send(InboxIntentUndoTapped.shared) }
    func approve(_ itemId: String) { send(InboxIntentApproveTapped(itemId: itemId)) }
    func requestReject(_ itemId: String) { send(InboxIntentRejectTapped(itemId: itemId)) }
    func confirmReject(note: String) { send(InboxIntentRejectConfirmed(note: note)) }
    func cancelReject() { send(InboxIntentRejectCancelled.shared) }
    func keepPaused(_ itemId: String) { send(InboxIntentKeepPausedTapped(itemId: itemId)) }
    func dismissMessage() { send(InboxIntentMessageDismissed.shared) }
}

import PaperclipKit

/// Screen 3's adapter.
typealias ApprovalModel = HolderModel<ApprovalStateHolder>

extension HolderModel where Holder == ApprovalStateHolder {
    func approve() { send(ApprovalIntentApproveTapped.shared) }
    func requestReject() { send(ApprovalIntentRejectTapped.shared) }
    func requestRevision() { send(ApprovalIntentRequestRevisionTapped.shared) }
    func confirmNote(_ note: String) { send(ApprovalIntentNoteConfirmed(note: note)) }
    func cancelNote() { send(ApprovalIntentNoteCancelled.shared) }
    func comment(_ body: String) { send(ApprovalIntentCommentSubmitted(body: body)) }
    func refresh() { send(ApprovalIntentRefresh.shared) }
    func dismissMessage() { send(ApprovalIntentMessageDismissed.shared) }
}

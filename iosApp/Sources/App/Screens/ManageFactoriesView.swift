import PaperclipKit
import SwiftUI

/// Screen 16: reorder and remove factories. Removal asks for confirmation (what will be deleted comes from state) and
/// then the device owner, inside the shared use case: no factory leaves the list until that approves.
struct ManageFactoriesView: View {
    let environment: AppEnvironment
    private var model: ManageModel { environment.manageModel() }

    var body: some View {
        let state = model.state
        List {
            Section {
                ForEach(state.rows, id: \.id) { row in
                    ManageRow(
                        row: row,
                        onRemove: { model.requestRemove(row.id) },
                        onOpen: { environment.router.push(.editFactory(id: row.id), in: .factories) })
                }
                .onMove { source, destination in
                    var ids = state.rows.map(\.id)
                    ids.move(fromOffsets: source, toOffset: destination)
                    model.reorder(ids)
                }
            } header: {
                Text(countText(state.rows.count))
                    .accessibilityIdentifier("manage-count")
            } footer: {
                VStack(alignment: .leading, spacing: Spacing.medium) {
                    Text(ShellLabel.manageHint.text)
                    if state.reorderRejected {
                        Text(ShellLabel.manageReorderRejected.text)
                            .foregroundStyle(Palette.destructive.color)
                            .accessibilityIdentifier("manage-reorder-rejected")
                    }
                    if let outcome = state.lastRemovedKey {
                        Text(L10n.text(outcome))
                            .accessibilityIdentifier("manage-key-outcome")
                    }
                }
            }
            Section {
                Button {
                    environment.router.push(.editFactory(id: nil), in: .factories)
                } label: {
                    Label(ShellLabel.factoriesAdd.text, systemImage: "plus")
                        .frame(minHeight: Spacing.minimumTarget, alignment: .leading)
                }
                .accessibilityIdentifier("add-factory-button")
            }
        }
        .scrollContentBackground(.hidden)
        .background(Palette.background.color)
        .environment(\.editMode, .constant(.active))
        .navigationTitle(ShellLabel.tabFactories.text)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(ShellLabel.commonDone.text) { environment.router.popToRoot(.factories) }
                    .accessibilityIdentifier("manage-done-button")
            }
        }
        .sheet(
            isPresented: Binding(
                get: { model.state.confirmation != nil },
                set: { if !$0, model.state.confirmation != nil { model.cancelRemove() } })
        ) {
            if let confirmation = model.state.confirmation {
                ConfirmDestructiveSheet(
                    title: ShellLabel.removeTitle.text(confirmation.name),
                    message: message(for: confirmation),
                    confirmTitle: ShellLabel.removeConfirm.text,
                    issue: model.state.deviceAuthIssue,
                    isWorking: model.state.isRemoving,
                    cancelTitle: ShellLabel.commonKeep.text,
                    onConfirm: model.confirmRemove,
                    onCancel: model.cancelRemove)
            }
        }
        .task { await model.observe() }
        .accessibilityIdentifier("manage-factories")
    }

    private func countText(_ count: Int) -> String {
        count == 1 ? ShellLabel.manageCountOne.text : ShellLabel.manageCountOther.text(count)
    }

    private func message(for confirmation: RemovalConfirmationUi) -> String {
        (confirmation.effects.map { L10n.text($0) } + [ShellLabel.removeFooter.text]).joined(separator: "\n")
    }
}

private struct ManageRow: View {
    let row: ManageRowUi
    let onRemove: () -> Void
    let onOpen: () -> Void

    var body: some View {
        let status = FactoryStatusPresentation(status: row.status, expiry: ExpiryUi(state: .noExpiry, daysLeft: nil))
        HStack(spacing: Spacing.extraLarge) {
            Button(action: onRemove) {
                Image(systemName: "minus.circle.fill")
                    .font(.title2)
                    .foregroundStyle(Palette.destructive.color)
                    .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(ShellLabel.manageRemoveAction.text(row.name))
            .accessibilityIdentifier("manage-remove-\(row.id)")
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: Spacing.hairline) {
                    Text(row.name).font(Typography.cardTitle).foregroundStyle(Palette.textPrimary.color)
                        .accessibilityIdentifier("manage-name-\(row.id)")
                    Text(row.address).font(Typography.secondary).foregroundStyle(Palette.textSecondary.color)
                        .accessibilityIdentifier("manage-address-\(row.id)")
                    Text(status.text).font(Typography.label).foregroundStyle(status.tone.foreground.color)
                        .accessibilityIdentifier("manage-status-\(row.id)")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(minHeight: Spacing.minimumTarget)
            }
            .buttonStyle(.plain)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("manage-row-\(row.id)")
    }
}

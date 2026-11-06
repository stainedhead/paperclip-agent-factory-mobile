import PaperclipKit
import SwiftUI

/// Screen 8, the root of the Factories tab: every factory with its status, key expiry and companies, the way to add
/// one, and the doors to Manage factories and Settings. It renders `FactoriesState`; selecting a factory or company
/// is an intent.
struct FactoriesView: View {
    let environment: AppEnvironment
    private var model: FactoriesModel { environment.factoriesModel() }

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.extraLarge) {
                if state.isOffline {
                    OfflineBanner(updatedAtEpochMillis: state.updatedAtEpochMillis?.int64Value)
                } else if state.updatedAtEpochMillis != nil {
                    UpdatedLabel(epochMillis: state.updatedAtEpochMillis?.int64Value)
                }
                if state.isEmpty {
                    EmptyState(
                        symbol: "rectangle.stack", title: ShellLabel.factoriesEmptyTitle.text,
                        message: ShellLabel.factoriesEmptyMessage.text)
                }
                ForEach(state.rows, id: \.id) { row in
                    FactoryCard(
                        row: row, model: model,
                        onPairAgain: { environment.router.push(.editFactory(id: row.id), in: .factories) },
                        onDetails: { environment.router.push(.editFactory(id: row.id), in: .factories) })
                }
                Button {
                    environment.router.push(.editFactory(id: nil), in: .factories)
                } label: {
                    Label(ShellLabel.factoriesAdd.text, systemImage: "plus")
                }
                .buttonStyle(.app(.secondary))
                .accessibilityIdentifier("add-factory-button")
            }
            .padding(Spacing.page)
        }
        .background(Palette.background.color)
        .refreshable { model.refresh() }
        .navigationTitle(ShellLabel.tabFactories.text)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                // An icon, not text: bar text is capped by the system and drawn on glass (audit findings).
                Button {
                    environment.router.push(.manageFactories, in: .factories)
                } label: {
                    Image(systemName: "pencil")
                }
                .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                .accessibilityLabel(ShellLabel.commonEdit.text)
                .accessibilityIdentifier("manage-factories-button")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    environment.router.push(.settings, in: .factories)
                } label: {
                    Image(systemName: "gearshape")
                }
                .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                .accessibilityLabel(ShellLabel.factoriesSettings.text)
                .accessibilityIdentifier("settings-button")
            }
        }
        .task {
            model.refresh()
            await model.observe()
        }
        .accessibilityIdentifier("factories-root")
    }
}

/// One factory of the Factories list, as in the mock: status dot and name, status words, address, key expiry,
/// company chips, "Pair again" when the phone has no working key.
struct FactoryCard: View {
    let row: FactoryRowUi
    let model: FactoriesModel
    let onPairAgain: () -> Void
    let onDetails: () -> Void

    var body: some View {
        let status = FactoryStatusPresentation(status: row.status, expiry: row.expiry)
        let expiry = ExpiryPresentation(row.expiry)
        VStack(alignment: .leading, spacing: Spacing.large) {
            AdaptiveStack(alignment: .firstTextBaseline, spacing: Spacing.medium) {
                Circle().fill(status.tone.foreground.color).frame(width: 10, height: 10).accessibilityHidden(true)
                Text(row.name)
                    .font(Typography.cardTitle)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("factory-name-\(row.id)")
                Spacer(minLength: Spacing.medium)
                Text(status.text)
                    .font(Typography.label)
                    .foregroundStyle(status.tone.foreground.color)
                    .fixedSize(horizontal: true, vertical: true)
                    .accessibilityIdentifier("factory-status-\(row.id)")
                Button(action: onDetails) {
                    Image(systemName: "ellipsis.circle")
                        .frame(minWidth: Spacing.minimumTarget, minHeight: Spacing.minimumTarget)
                }
                .accessibilityLabel(ShellLabel.editTitleEdit.text)
                .accessibilityIdentifier("factory-details-\(row.id)")
            }
            Text(BreakableAddress.text(row.address))
                .font(Typography.secondary)
                .foregroundStyle(Palette.textSecondary.color)
                .accessibilityLabel("\(ShellLabel.fieldAddressLabel.text): \(row.address)")
            if let text = expiry.text {
                Text(text)
                    .font(Typography.secondary)
                    .foregroundStyle(expiry.tone.foreground.color)
                    .accessibilityIdentifier("factory-expiry-\(row.id)")
            }
            if let error = row.error { ErrorBanner(error: error) }
            if row.isLoading {
                Text(ShellLabel.factoriesLoadingCompanies.text).font(Typography.secondary)
            }
            if !row.companies.isEmpty {
                FlowLayout {
                    ForEach(row.companies, id: \.id) { company in
                        CompanyChip(
                            company: company, isSelected: row.selectedCompanyId == company.id,
                            identifier: "company-chip-\(row.id)-\(company.id)"
                        ) {
                            model.selectCompany(instanceId: row.id, companyId: company.id)
                        }
                    }
                }
            }
            if status.offersPairing {
                Button(ShellLabel.factoriesPairAgain.text, action: onPairAgain)
                    .buttonStyle(.app(.secondary))
                    .accessibilityIdentifier("pair-again-\(row.id)")
            }
        }
        .cardSurface()
        .overlay(
            RoundedRectangle(cornerRadius: Radius.card)
                .strokeBorder(Palette.accent.color, lineWidth: row.isSelected ? 2 : 0)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("factory-card-\(row.id)")
    }
}

/// A company pill. Selection is shown by fill and by a check, and announced as selected.
struct CompanyChip: View {
    let company: CompanyUi
    let isSelected: Bool
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.small) {
                if isSelected { Image(systemName: "checkmark").font(Typography.micro).accessibilityHidden(true) }
                Text(company.name).font(Typography.body)
            }
            .padding(.horizontal, Spacing.extraLarge)
            .frame(minHeight: Spacing.minimumTarget)
            .foregroundStyle((isSelected ? Palette.accent : Palette.textPrimary).color)
            .background((isSelected ? Palette.accentSoft : Palette.muted).color, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(company.name)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier(identifier)
    }
}

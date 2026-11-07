import PaperclipKit

/// A factory with no company the tabs can show (F-1, ADR-150): the heading of each shared `CompanyGate` state.
extension CompanyGate: CatalogKey { static var catalogNamespace: String { "companyGate" } }

/// The sentence under the heading of each gate state: one entry per `CompanyGate`, so every state has its own.
enum CompanyGateMessage: String, CaseIterable, CatalogKey {
    static var catalogNamespace: String { "companyGateMessage" }

    case ready
    case checking
    case noCompany
    case chooseCompany
    case failed

    init(_ gate: CompanyGate) {
        switch gate {
        case .ready: self = .ready
        case .checking: self = .checking
        case .noCompany: self = .noCompany
        case .chooseCompany: self = .chooseCompany
        case .failed: self = .failed
        }
    }
}

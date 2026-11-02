import PaperclipKit

/// Screen 15's adapter: the generic holder model plus the one intent the address field sends.
typealias FirstRunModel = HolderModel<FirstRunStateHolder>

extension HolderModel where Holder == FirstRunStateHolder {
    func addressChanged(_ text: String) {
        send(FirstRunIntentAddressChanged(text: text))
    }
}

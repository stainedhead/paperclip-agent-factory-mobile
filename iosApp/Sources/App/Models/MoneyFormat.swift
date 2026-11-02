import Foundation
import PaperclipKit

/// Money as the person reads and types it. The amounts are minor units in a currency (cents for USD); the number of
/// decimals is the currency's own. A whole amount drops its zero cents ("$150"), any other keeps them ("$150.50").
enum MoneyFormat {
    static func text(_ money: MoneyUi, locale: Locale = .current) -> String {
        let digits = fractionDigits(money.currency, locale: locale)
        let amount = Decimal(money.minorUnits) / pow(10, digits)
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = money.currency
        let whole = money.minorUnits % Int64(pow(10, digits).intValue) == 0
        formatter.minimumFractionDigits = whole ? 0 : digits
        formatter.maximumFractionDigits = whole ? 0 : digits
        return formatter.string(from: amount as NSDecimalNumber) ?? "\(money.minorUnits) \(money.currency)"
    }

    /// What the person typed ("250", "$1,200", "250.50") in minor units, or `nil` when it is not a positive amount.
    static func minorUnits(from text: String, currency: String = "USD", locale: Locale = .current) -> Int64? {
        let cleaned = text.filter { $0.isNumber || String($0) == locale.decimalSeparator || String($0) == "-" }
        guard !cleaned.isEmpty else { return nil }
        let parser = NumberFormatter()
        parser.numberStyle = .decimal
        parser.locale = locale
        parser.generatesDecimalNumbers = true
        guard let number = parser.number(from: cleaned) as? NSDecimalNumber, number.compare(0) == .orderedDescending
        else { return nil }
        let scaled = number.multiplying(by: NSDecimalNumber(decimal: pow(10, fractionDigits(currency, locale: locale))))
        return Int64(truncating: scaled.rounding(accordingToBehavior: nil))
    }

    private static func fractionDigits(_ currency: String, locale: Locale) -> Int {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = currency
        return formatter.maximumFractionDigits
    }
}

extension Decimal {
    fileprivate var intValue: Int { NSDecimalNumber(decimal: self).intValue }
}

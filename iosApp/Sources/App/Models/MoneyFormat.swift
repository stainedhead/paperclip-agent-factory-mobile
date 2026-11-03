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

    static func fractionDigits(_ currency: String, locale: Locale) -> Int {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = locale
        formatter.currencyCode = currency
        return formatter.maximumFractionDigits
    }
}

/// What shared code needs to read a typed amount: the locale's decimal separator and the currency's fraction digits.
/// Reading, validating and refusing the text is shared code's (`MoneyInput`); nothing is parsed here.
struct MoneyInputFormat: Equatable {
    let decimalSeparator: String
    let fractionDigits: Int

    init(currency: String, locale: Locale = .current) {
        decimalSeparator = locale.decimalSeparator ?? "."
        fractionDigits = MoneyFormat.fractionDigits(currency, locale: locale)
    }
}

extension Decimal {
    fileprivate var intValue: Int { NSDecimalNumber(decimal: self).intValue }
}

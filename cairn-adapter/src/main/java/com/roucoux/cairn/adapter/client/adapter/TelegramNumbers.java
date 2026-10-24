package com.roucoux.cairn.adapter.client.adapter;

import com.roucoux.cairn.domain.model.Language;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class TelegramNumbers {

    private TelegramNumbers() {}

    static Locale locale(Language language) {
        return language == Language.FR ? Locale.FRANCE : Locale.UK;
    }

    static String euros(BigDecimal rounded, boolean signed, Language language) {
        String digits = grouped(rounded.abs(), "#,##0", language);
        String sign = signed ? sign(rounded) : rounded.signum() < 0 ? "-" : "";
        return language == Language.FR ? sign + digits + " €" : sign + "€" + digits;
    }

    static String headlineRatio(BigDecimal percent, Language language) {
        String number = signedNumber(percent, language);
        return language == Language.FR ? number + " %" : number + "%";
    }

    static String tableRatio(BigDecimal percent, Language language) {
        return signedNumber(percent, language) + "%";
    }

    private static String signedNumber(BigDecimal percent, Language language) {
        return sign(percent) + grouped(percent.abs(), "#,##0.00", language);
    }

    private static String sign(BigDecimal value) {
        return switch (value.signum()) {
            case 1 -> "+";
            case -1 -> "-";
            default -> "";
        };
    }

    private static String grouped(BigDecimal amount, String pattern, Language language) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale(language));
        if (language == Language.FR) {
            symbols.setGroupingSeparator(' ');
        }
        return new DecimalFormat(pattern, symbols).format(amount);
    }
}

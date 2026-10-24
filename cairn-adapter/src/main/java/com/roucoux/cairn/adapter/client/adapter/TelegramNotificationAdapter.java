package com.roucoux.cairn.adapter.client.adapter;

import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.CashOnlyAccount;
import com.roucoux.cairn.domain.model.Language;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.Recipient;
import com.roucoux.cairn.domain.model.Summary;
import com.roucoux.cairn.domain.model.SummaryPeriod;
import com.roucoux.cairn.domain.port.out.SendNotificationPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Only account names are escaped: any other free text added later needs escaped() or Telegram answers 400. */
@Component
public class TelegramNotificationAdapter implements SendNotificationPort {

    private final TelegramBotApi api;
    private final MessageSource messages;

    @Autowired
    TelegramNotificationAdapter(TelegramBotApi api, @Qualifier("telegramMessages") MessageSource messages) {
        this.api = api;
        this.messages = messages;
    }

    @Override
    public void send(Recipient recipient, Summary summary) {
        api.sendMessage(recipient.chatId(), format(recipient.language(), summary));
    }

    private String format(Language language, Summary summary) {
        Performance performance = summary.performance();
        BigDecimal change = roundedEuros(performance.change());
        StringBuilder message = new StringBuilder()
                .append("📊 <b>Cairn · ")
                .append(title(language, summary))
                .append("</b>\n\n💰 ")
                .append(text(language, "netWorth"))
                .append("  <b>")
                .append(TelegramNumbers.euros(roundedEuros(performance.total()), false, language))
                .append("</b>\n")
                .append(change.signum() < 0 ? "📉" : "📈")
                .append(" ")
                .append(changeLabel(language, summary))
                .append("  <b>")
                .append(TelegramNumbers.euros(change, true, language))
                .append("</b>");
        performance
                .changeRatio()
                .ifPresent(ratio -> message.append("  (")
                        .append(TelegramNumbers.headlineRatio(percent(ratio), language))
                        .append(")"));
        String table = table(language, summary);
        if (!table.isEmpty()) {
            message.append("\n\n<pre>").append(table).append("</pre>");
        }
        return message.toString();
    }

    private String text(Language language, String key, Object... arguments) {
        return messages.getMessage(key, arguments, TelegramNumbers.locale(language));
    }

    private String title(Language language, Summary summary) {
        Locale locale = TelegramNumbers.locale(language);
        LocalDate first = summary.first();
        LocalDate last = summary.last();
        return switch (summary.period()) {
            case DAY ->
                text(
                        language,
                        "title.day",
                        capitalize(
                                DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
                                        .format(first),
                                locale));
            case WEEK ->
                first.getMonth() == last.getMonth()
                        ? text(
                                language,
                                "title.week.sameMonth",
                                String.valueOf(first.getDayOfMonth()),
                                dayMonth(locale, last))
                        : text(language, "title.week", dayMonth(locale, first), dayMonth(locale, last));
            case MONTH ->
                text(
                        language,
                        "title.month",
                        capitalize(
                                DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(first), locale));
            case YEAR -> text(language, "title.year", String.valueOf(first.getYear()));
        };
    }

    private static String dayMonth(Locale locale, LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMMM", locale).format(date);
    }

    private String changeLabel(Language language, Summary summary) {
        String label =
                switch (summary.period()) {
                    case DAY -> text(language, "change.day");
                    case WEEK -> text(language, "change.week");
                    case MONTH -> text(language, "change.month");
                    case YEAR -> text(language, "change.year");
                };
        boolean clamped = summary.period() != SummaryPeriod.DAY
                && summary.performance().from().isAfter(summary.first().minusDays(1));
        return clamped
                ? text(
                        language,
                        "change.since",
                        label,
                        dayMonth(
                                TelegramNumbers.locale(language),
                                summary.performance().from()))
                : label;
    }

    private String table(Language language, Summary summary) {
        Set<AccountType> cashOnly =
                summary.cashOnlyAccounts().stream().map(CashOnlyAccount::type).collect(Collectors.toSet());
        List<List<String>> moving = summary.performance().byEnvelope().stream()
                .filter(envelope -> !cashOnly.contains(envelope.accountType()))
                .map(envelope -> {
                    BigDecimal change = roundedEuros(envelope.change());
                    return List.of(
                            dot(change),
                            text(language, "envelope." + envelope.accountType().name()),
                            TelegramNumbers.euros(roundedEuros(envelope.value()), false, language),
                            TelegramNumbers.euros(change, true, language),
                            envelope.changeRatio()
                                    .map(ratio -> TelegramNumbers.tableRatio(percent(ratio), language))
                                    .orElse(""));
                })
                .toList();
        List<List<String>> idle = summary.cashOnlyAccounts().stream()
                .map(account -> List.of(
                        "🏦",
                        account.name(),
                        TelegramNumbers.euros(roundedEuros(account.value()), false, language),
                        "",
                        ""))
                .toList();
        List<List<String>> all = new ArrayList<>(moving);
        all.addAll(idle);
        int labelWidth = width(all, 1);
        int valueWidth = width(all, 2);
        int changeWidth = width(moving, 3);
        int ratioWidth = width(moving, 4);
        List<String> lines = new ArrayList<>();
        moving.forEach(row -> lines.add(row(row, labelWidth, valueWidth, changeWidth, ratioWidth)));
        if (!moving.isEmpty() && !idle.isEmpty()) {
            lines.add("─".repeat(2 + 1 + labelWidth + 1 + valueWidth + 2 + changeWidth + 2 + ratioWidth));
        }
        idle.forEach(row -> lines.add(row(row, labelWidth, valueWidth, changeWidth, ratioWidth)));
        return String.join("\n", lines);
    }

    private static String row(List<String> row, int labelWidth, int valueWidth, int changeWidth, int ratioWidth) {
        return (row.get(0) + " " + escaped(padRight(row.get(1), labelWidth)) + " " + padLeft(row.get(2), valueWidth)
                        + "  " + padLeft(row.get(3), changeWidth) + "  " + padLeft(row.get(4), ratioWidth))
                .stripTrailing();
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String dot(BigDecimal roundedChange) {
        return switch (roundedChange.signum()) {
            case 1 -> "🟢";
            case -1 -> "🔴";
            default -> "⚪";
        };
    }

    private static int width(List<List<String>> rows, int column) {
        return rows.stream().mapToInt(row -> row.get(column).length()).max().orElse(0);
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return " ".repeat(width - text.length()) + text;
    }

    private static String capitalize(String text, Locale locale) {
        return text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private static BigDecimal roundedEuros(Money money) {
        return money.amount().setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}

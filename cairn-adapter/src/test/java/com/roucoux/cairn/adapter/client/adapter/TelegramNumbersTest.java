package com.roucoux.cairn.adapter.client.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Language;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TelegramNumbersTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void frenchEurosGroupWithASpaceAndSuffixTheSymbol() {
        assertThat(TelegramNumbers.euros(bd("12345"), false, Language.FR)).isEqualTo("12 345 €");
        assertThat(TelegramNumbers.euros(bd("0"), false, Language.FR)).isEqualTo("0 €");
    }

    @Test
    void frenchSignedEurosCarryThePlusOrMinus() {
        assertThat(TelegramNumbers.euros(bd("1234"), true, Language.FR)).isEqualTo("+1 234 €");
        assertThat(TelegramNumbers.euros(bd("-1234"), true, Language.FR)).isEqualTo("-1 234 €");
        assertThat(TelegramNumbers.euros(bd("0"), true, Language.FR)).isEqualTo("0 €");
    }

    @Test
    void englishEurosPrefixTheSymbolAndGroupWithCommas() {
        assertThat(TelegramNumbers.euros(bd("12345"), false, Language.EN)).isEqualTo("€12,345");
        assertThat(TelegramNumbers.euros(bd("0"), false, Language.EN)).isEqualTo("€0");
    }

    @Test
    void englishSignedEurosPutTheSignBeforeTheSymbol() {
        assertThat(TelegramNumbers.euros(bd("1234"), true, Language.EN)).isEqualTo("+€1,234");
        assertThat(TelegramNumbers.euros(bd("-1234"), true, Language.EN)).isEqualTo("-€1,234");
        assertThat(TelegramNumbers.euros(bd("0"), true, Language.EN)).isEqualTo("€0");
    }

    @Test
    void frenchHeadlineRatioUsesACommaAndASpaceBeforeThePercentSign() {
        assertThat(TelegramNumbers.headlineRatio(bd("1.23"), Language.FR)).isEqualTo("+1,23 %");
        assertThat(TelegramNumbers.headlineRatio(bd("-1.23"), Language.FR)).isEqualTo("-1,23 %");
        assertThat(TelegramNumbers.headlineRatio(bd("0.00"), Language.FR)).isEqualTo("0,00 %");
    }

    @Test
    void englishHeadlineRatioUsesADotAndNoSpace() {
        assertThat(TelegramNumbers.headlineRatio(bd("1.23"), Language.EN)).isEqualTo("+1.23%");
        assertThat(TelegramNumbers.headlineRatio(bd("-1.23"), Language.EN)).isEqualTo("-1.23%");
        assertThat(TelegramNumbers.headlineRatio(bd("0.00"), Language.EN)).isEqualTo("0.00%");
    }

    @Test
    void frenchTableRatioHasNoSpaceBeforeThePercentSign() {
        assertThat(TelegramNumbers.tableRatio(bd("1.23"), Language.FR)).isEqualTo("+1,23%");
        assertThat(TelegramNumbers.tableRatio(bd("-1.23"), Language.FR)).isEqualTo("-1,23%");
        assertThat(TelegramNumbers.tableRatio(bd("0.00"), Language.FR)).isEqualTo("0,00%");
    }

    @Test
    void englishTableRatioMatchesTheHeadlineStyle() {
        assertThat(TelegramNumbers.tableRatio(bd("1.23"), Language.EN)).isEqualTo("+1.23%");
        assertThat(TelegramNumbers.tableRatio(bd("-1.23"), Language.EN)).isEqualTo("-1.23%");
        assertThat(TelegramNumbers.tableRatio(bd("0.00"), Language.EN)).isEqualTo("0.00%");
    }
}

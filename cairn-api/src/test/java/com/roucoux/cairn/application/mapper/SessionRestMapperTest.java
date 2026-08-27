package com.roucoux.cairn.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionRestMapperTest {

    private final SessionRestMapper mapper = new SessionRestMapper();

    @Test
    void takesOneInitialFromEachOfTheFirstTwoWords() {
        assertThat(mapper.initialsOf("Joan Roucoux")).isEqualTo("JR");
    }

    @Test
    void ignoresTheWordsAfterTheSecond() {
        assertThat(mapper.initialsOf("Jean Pierre Marie Dupont")).isEqualTo("JP");
    }

    @Test
    void takesTwoLettersFromASingleWord() {
        assertThat(mapper.initialsOf("joan")).isEqualTo("JO");
    }

    @Test
    void takesTheOnlyLetterOfAOneLetterName() {
        assertThat(mapper.initialsOf("J")).isEqualTo("J");
    }

    @Test
    void survivesAnEmptyDisplayName() {
        assertThat(mapper.initialsOf("  ")).isEmpty();
    }
}

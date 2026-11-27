package org.example.stock.agent;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class QuestionLanguageTests {

	@Test
	void acceptsEnglishAndTurkishStockQuestions() {
		assertThatCode(() -> QuestionLanguage.reject("Aselsan hissesi şu an kaç TL?")).doesNotThrowAnyException();
		assertThatCode(() -> QuestionLanguage.reject("What is the ASELS price?")).doesNotThrowAnyException();
		assertThatCode(() -> QuestionLanguage.reject("ASELS bugün?")).doesNotThrowAnyException();
		assertThatCode(() -> QuestionLanguage.reject("ASELS son 5 gün?")).doesNotThrowAnyException();
		assertThatCode(() -> QuestionLanguage.reject("NOPE1 ve ASELS?")).doesNotThrowAnyException();
		assertThatCode(() -> QuestionLanguage.reject("Bugün hava nasıl?")).doesNotThrowAnyException();
	}

	@Test
	void rejectsATickerAloneAForeignWordAndAnotherScript() {
		assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> QuestionLanguage.reject("ASELS?"))
				.withMessageContaining(QuestionLanguage.REJECTION);
		assertThatExceptionOfType(ResponseStatusException.class)
				.isThrownBy(() -> QuestionLanguage.reject("bonjour ASELS fiyatı"))
				.withMessageContaining(QuestionLanguage.REJECTION);
		assertThatExceptionOfType(ResponseStatusException.class)
				.isThrownBy(() -> QuestionLanguage.reject("ما هو سعر ASELS؟"))
				.withMessageContaining(QuestionLanguage.REJECTION);
	}

	@Test
	void isNotConstructedByCallers() throws Exception {
		Constructor<QuestionLanguage> constructor = QuestionLanguage.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		assertThat(constructor.newInstance()).isNotNull();
	}

}

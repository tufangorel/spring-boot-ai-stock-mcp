package org.example.stock.agent;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerGroundingTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final List<ToolCallRecord> QUOTE_CALL = List.of(new ToolCallRecord("getQuote", null,
			JSON.readTree(Samples.QUOTES_JSON), null));

	@ParameterizedTest(name = "\"{0}\" is grounded")
	@ValueSource(strings = {
			"ASELS 330,75 TL.",
			"ASELS is at 330.75 TRY.",
			"ASELS 330,8 TL civarında.",
			"Değişim %0,53.",
			"Change: -1.75 TRY.",
			"Hacim 30.437.685 lot.",
			"Volume 30,437,685 shares.",
			"Gün içi 325,25 – 335,75 TL.",
			"Fiyat 06.11.2026 15:40 itibarıyla, 15 dakika gecikmeli." })
	void acceptsNumbersFromTheToolsInEitherStyle(String answer) {
		assertThat(AnswerGrounding.warnings("ASELS?", answer, QUOTE_CALL)).isEmpty();
	}

	@ParameterizedTest(name = "\"{0}\" mentions {1}")
	@CsvSource(delimiter = '|', value = {
			"Hedef fiyat 410,00 TL.|410,00",
			"ASELS 330,70 TL.|330,70",
			"Volume 1,234,567 shares.|1,234,567",
			"Up 12.5% this year.|12.5" })
	void warnsAboutNumbersNoToolReturned(String answer, String number) {
		assertThat(AnswerGrounding.warnings("ASELS?", answer, QUOTE_CALL))
				.containsExactly("The answer mentions " + number + ", which is not in any tool result.");
	}

	@Test
	void warnsOnceAboutTheSameNumber() {
		assertThat(AnswerGrounding.warnings("ASELS?", "410,00 TL ve yine 410,00 TL.", QUOTE_CALL)).hasSize(1);
	}

	@Test
	void acceptsNumbersFromTheQuestion() {
		assertThat(AnswerGrounding.warnings("ASELS 340,50 TL'yi geçti mi?", "Hayır, 330,75 TL; 340,50 TL'nin altında.",
				QUOTE_CALL)).isEmpty();
	}

	@Test
	void warnsAboutFailedToolsAndAMissingToolCall() {
		assertThat(AnswerGrounding.warnings(null, null, List.of())).containsExactly(
				"No tool was called, so the answer is not based on market data.");
		assertThat(AnswerGrounding.warnings("q", "Bilmiyorum.",
				List.of(new ToolCallRecord("getQuote", null, null, "Connection refused"))))
				.containsExactly("The getQuote tool failed: Connection refused");
	}

	@ParameterizedTest(name = "{0} reads as {1}")
	@CsvSource(delimiter = '|', value = {
			"330,75|330.75",
			"330.75|330.75",
			"1.234|1.234;1234",
			"1,234|1234;1.234",
			"1.234,56|1234.56",
			"1,234.56|1234.56",
			"30.437.685|30437685",
			"06.11.2026|",
			"1.2.3,4|",
			"12,34.5|",
			"1234|" })
	void readsBothStyles(String text, String values) {
		List<BigDecimal> expected = values == null ? List.of()
				: java.util.Arrays.stream(values.split(";")).map(BigDecimal::new).toList();

		assertThat(AnswerGrounding.readings(text)).containsExactlyInAnyOrderElementsOf(expected);
	}

}

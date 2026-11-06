package org.example.stock.mcp;

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SymbolsTests {

	@ParameterizedTest
	@ValueSource(strings = { "ASELS", "asels", " asels ", "ASELS.IS", "asels.is", "BIST:ASELS", "bist:asels.is" })
	void normalizesTheWaysATickerIsWritten(String symbol) {
		assertThat(Symbols.normalize(symbol)).isEqualTo("ASELS");
	}

	@ParameterizedTest
	@ValueSource(strings = { "isctr", "XU100" })
	void keepsTickersThatAreNotLatinWordsOrContainDigits(String symbol) {
		assertThat(Symbols.normalize(symbol)).isEqualTo(symbol.toUpperCase(Locale.ROOT));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "A", "Aselsan Elektronik", "ASELS;DROP", "ŞİŞE", "ABCDEFGHIJKLM" })
	void rejectsTextThatIsNotATicker(String symbol) {
		assertThatIllegalArgumentException().isThrownBy(() -> Symbols.normalize(symbol))
				.withMessageContaining("is not a Borsa Istanbul ticker")
				.withMessageContaining("findSymbol");
	}

}

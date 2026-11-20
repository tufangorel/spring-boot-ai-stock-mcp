package org.example.stock.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockCueTests {

	@Test
	void acceptsATurkishStockQuestion() {
		assertThat(StockCue.inRole("Aselsan hissesi şu an kaç TL?")).isTrue();
		assertThat(StockCue.inRole("FİYATI nedir?")).isTrue();
	}

	@Test
	void acceptsACapitalTickerWithoutAStockWord() {
		assertThat(StockCue.inRole("ASELS bugün?")).isTrue();
	}

	@Test
	void rejectsAQuestionWithNoStockCue() {
		assertThat(StockCue.inRole("Bugün hava nasıl?")).isFalse();
		assertThat(StockCue.inRole("What is the weather?")).isFalse();
	}

}

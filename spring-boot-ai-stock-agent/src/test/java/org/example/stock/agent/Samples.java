package org.example.stock.agent;

/** Tool results shaped like the stock MCP server's, taken from its recorded provider responses. */
final class Samples {

	static final String QUESTION = "Aselsan hissesi şu an kaç TL?";

	static final String SYMBOLS_JSON = """
			{"query":"Aselsan","matches":[{"symbol":"ASELS","name":"Aselsan Elektronik Sanayi ve Ticaret A.S. Class B","exchange":"BIST"}]}""";

	static final String QUOTES_JSON = """
			{"quotes":[{"symbol":"ASELS","name":"Aselsan Elektronik Sanayi ve Ticaret A.S. Class B","price":330.75,\
			"currency":"TRY","change":1.75,"changePercent":0.53,"open":329.5,"dayHigh":335.75,"dayLow":325.25,\
			"volume":30437685,"asOf":"2026-11-06T15:40:11+03:00","delayMinutes":15,"source":"TradingView",\
			"marketOpen":true}],"notFound":[]}""";

	static final String HISTORY_JSON = """
			{"symbol":"ASELS","currency":"TRY","range":"5d","interval":"1d","candles":[\
			{"time":"2026-11-02T09:30:00+03:00","open":346.75,"high":351.25,"low":339.25,"close":342.25,"volume":31956940},\
			{"time":"2026-11-05T09:30:00+03:00","open":355.5,"high":359.5,"low":327.5,"close":329.0,"volume":50835282},\
			{"time":"2026-11-06T09:30:00+03:00","open":329.5,"high":335.75,"low":325.25,"close":330.75,"volume":30329395}],\
			"omittedCandles":0,"asOf":"2026-11-06T15:25:56+03:00","delayMinutes":15,"source":"Yahoo Finance"}""";

	static final String ANSWER = "ASELSAN (ASELS) 15:40 itibarıyla 330,75 TL, günlük değişim %0,53. "
			+ "Veri TradingView'den ve yaklaşık 15 dakika gecikmeli.";

	private Samples() {
	}

}

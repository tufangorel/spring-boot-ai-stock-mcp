package org.example.stock.agent;

import java.util.List;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "spring.ai.openai.api-key=test-key", "spring.ai.mcp.client.enabled=false",
		"stocks.ask-permits-per-minute=1", "management.opentelemetry.tracing.export.schedule-delay=1ms",
		"spring.test.tracing.export=true" })
@AutoConfigureMockMvc
@Import({ StockAgentIntegrationTests.Fakes.class, AskOtelTests.Exporter.class })
class AskOtelTests {

	private static final String PARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ScriptedChatModel model;

	@Autowired
	private FakeTools tools;

	@Autowired
	private InMemorySpanExporter spans;

	@Autowired
	private SdkTracerProvider tracerProvider;

	@BeforeEach
	void reset() {
		tools.reset();
		spans.reset();
	}

	@Test
	void oneAskSharesItsTraceAcrossTheModelAndToolSpans() throws Exception {
		tools.returning("getQuote", Samples.QUOTES_JSON);
		model.script(model.callTool("getQuote", "{\"symbols\":[\"ASELS\"]}"), ScriptedChatModel.answer(Samples.ANSWER));

		MvcResult result = ask("ada", "{\"question\":\"ASELS fiyati\"}");
		List<SpanData> finished = finished();

		String traceId = result.getResponse().getHeader(AskTraceIds.TRACE_HEADER);
		String spanId = result.getResponse().getHeader(AskTraceIds.SPAN_HEADER);
		assertThat(finished).allMatch(span -> span.getTraceId().equals(traceId));
		SpanData ask = finished.stream().filter(span -> span.getSpanId().equals(spanId)).findFirst().orElseThrow();
		SpanData model = finished.stream().filter(span -> "agent model call".equals(span.getName())).findFirst().orElseThrow();
		SpanData tool = finished.stream().filter(span -> span.getName().startsWith("agent tool")).findFirst().orElseThrow();
		assertThat(ancestors(finished, model)).contains(spanId);
		assertThat(tool.getParentSpanId()).isEqualTo(model.getSpanId());
		assertThat(ask.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("X-User-Id"))).isEqualTo("ada");
	}

	@Test
	void ignoresCallerIdsAndContinuesTraceparent() throws Exception {
		model.script(ScriptedChatModel.answer(Samples.ANSWER));
		tools.returning("getQuote", Samples.QUOTES_JSON);

		MvcResult supplied = mockMvc.perform(post("/api/stocks/ask").queryParam(AskTraceIds.TRACE_HEADER, "client-trace")
						.header(AskUserLimiter.USER_HEADER, "bea")
						.header(AskTraceIds.TRACE_HEADER, "client-trace")
						.header(AskTraceIds.SPAN_HEADER, "client-span")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"What is the ASELS price?\",\"X-Trace-Id\":\"client-trace\",\"X-Span-Id\":\"client-span\"}"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(supplied.getResponse().getHeader(AskTraceIds.TRACE_HEADER)).isNotEqualTo("client-trace");
		assertThat(supplied.getResponse().getHeader(AskTraceIds.SPAN_HEADER)).isNotEqualTo("client-span");

		spans.reset();
		model.script(ScriptedChatModel.answer(Samples.ANSWER));
		MvcResult continued = mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, "cara")
						.header(McpTracePropagation.TRACEPARENT, PARENT)
						.contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"What is the ASELS price?\"}"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(continued.getResponse().getHeader(AskTraceIds.TRACE_HEADER))
				.isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
		assertThat(continued.getResponse().getHeader(AskTraceIds.SPAN_HEADER)).isNotEqualTo("00f067aa0ba902b7");
	}

	@Test
	void rejectedCallsStillReturnTheAskTrace() throws Exception {
		MvcResult missing = mockMvc.perform(post("/api/stocks/ask").contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"ASELS?\"}"))
				.andExpect(status().isBadRequest())
				.andReturn();
		assertThat(missing.getResponse().getHeader(AskTraceIds.TRACE_HEADER)).matches("[0-9a-f]{32}");

		ask("dora", "{\"question\":\"ASELS?\"}");
		MvcResult limited = ask("dora", "{\"question\":\"ASELS?\"}");
		assertThat(limited.getResponse().getStatus()).isEqualTo(429);
		assertThat(limited.getResponse().getHeader(AskTraceIds.TRACE_HEADER)).matches("[0-9a-f]{32}");
		assertThat(limited.getResponse().getHeader(AskTraceIds.SPAN_HEADER)).matches("[0-9a-f]{16}");
	}

	private MvcResult ask(String userId, String body) throws Exception {
		return mockMvc.perform(post("/api/stocks/ask").header(AskUserLimiter.USER_HEADER, userId)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn();
	}

	private static List<String> ancestors(List<SpanData> spans, SpanData span) {
		List<String> parents = new java.util.ArrayList<>();
		String parentId = span.getParentSpanId();
		while (parentId != null && !parentId.equals("0000000000000000") && parents.size() < spans.size()) {
			parents.add(parentId);
			String next = parentId;
			parentId = spans.stream().filter(candidate -> candidate.getSpanId().equals(next)).map(SpanData::getParentSpanId)
					.findFirst().orElse(null);
		}
		return parents;
	}

	private List<SpanData> finished() {
		tracerProvider.forceFlush().join(5, java.util.concurrent.TimeUnit.SECONDS);
		return spans.getFinishedSpanItems();
	}

	@TestConfiguration
	static class Exporter {

		@Bean
		InMemorySpanExporter inMemorySpanExporter() {
			return InMemorySpanExporter.create();
		}

	}

}

package org.example.stock.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Provider responses recorded from the live endpoints on 2026-11-06, used so that no test calls them. */
public final class Recorded {

	private Recorded() {
	}

	public static String read(String name) {
		try (InputStream in = Recorded.class.getResourceAsStream("/recorded/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("No recorded response " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}

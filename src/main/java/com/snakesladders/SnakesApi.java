package com.snakesladders;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class SnakesApi
{
	private static final HttpClient HTTP = HttpClient.newBuilder().build();

	public static final class ApiResult
	{
		public final int statusCode;
		public final String body;     // null when 304, or when no body
		public final String etag;     // may be null

		public ApiResult(int statusCode, String body, String etag)
		{
			this.statusCode = statusCode;
			this.body = body;
			this.etag = etag;
		}

		public boolean isNotModified()
		{
			return statusCode == 304;
		}

		public boolean isOk()
		{
			return statusCode >= 200 && statusCode < 300;
		}
	}

	private static String normalizeBaseUrl(String baseUrl)
	{
		if (baseUrl == null) return "";
		String s = baseUrl.trim();
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		return s;
	}

	private static HttpRequest.Builder maybeWebhook(HttpRequest.Builder b, String webhookUrl)
	{
		if (webhookUrl != null && !webhookUrl.trim().isEmpty())
		{
			b.header("x-discord-webhook-url", webhookUrl.trim());
		}
		return b;
	}

	private static String firstHeader(HttpResponse<?> res, String name)
	{
		return res.headers().firstValue(name).orElse(null);
	}

	/** Encode a value for query string usage (e.g. rsn, join code, etc). */
	private static String encQ(String s)
	{
		if (s == null) return "";
		return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
	}

	/**
	 * Encode a value used in a URL path segment (e.g. /games/{id}/...).
	 * We keep it conservative: encode anything that could break path parsing.
	 */
	private static String encPath(String s)
	{
		if (s == null) return "";
		// URLEncoder is for query strings. For path segments, do a safe minimal encoding.
		// This covers spaces and any reserved path breakers.
		String t = s.trim();
		return t
			.replace("%", "%25")
			.replace(" ", "%20")
			.replace("#", "%23")
			.replace("?", "%3F")
			.replace("&", "%26")
			.replace("/", "%2F")
			.replace("\\", "%5C")
			.replace(":", "%3A");
	}

	/**
	 * Lightweight poll endpoint for the RuneLite InfoBox overlay.
	 *
	 * Backend should support:
	 * - ETag response header (based on revision)
	 * - If-None-Match request header
	 * - 304 Not Modified when unchanged
	 *
	 * GET /games/:id/overlay?rsn=...
	 */
	public static ApiResult getOverlay(String baseUrl, String gameId, String rsn, String ifNoneMatchEtag)
		throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl)
			+ "/games/" + encPath(gameId)
			+ "/overlay?rsn=" + encQ(rsn);

		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.header("accept", "application/json");

		if (ifNoneMatchEtag != null && !ifNoneMatchEtag.trim().isEmpty())
		{
			b.header("if-none-match", ifNoneMatchEtag.trim());
		}

		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());

		// 304 is expected and should not throw
		if (res.statusCode() == 304)
		{
			return new ApiResult(304, null, firstHeader(res, "etag"));
		}

		if (res.statusCode() >= 400)
		{
			throw new IOException("Overlay HTTP " + res.statusCode() + ": " + res.body());
		}

		return new ApiResult(res.statusCode(), res.body(), firstHeader(res, "etag"));
	}

	public static String getGameState(String baseUrl, String gameId) throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games/" + encPath(gameId) + "/state";
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.header("accept", "application/json")
			.build();

		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("State HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

	/**
	 * NOTE: Plugin no longer needs to create games (website does it),
	 * but leaving it here is harmless.
	 */
	public static String createGame(String baseUrl, String webhookUrl, String clanName, String hostPassword, int boardSize, String boardUrl)
		throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games";

		String body =
			"{\"clanName\":\"" + escape(clanName) + "\"," +
			"\"hostPassword\":\"" + escape(hostPassword) + "\"," +
			"\"boardSize\":" + boardSize + "," +
			"\"boardUrl\":\"" + escape(boardUrl) + "\"}";

		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.header("content-type", "application/json")
			.header("accept", "application/json");

		maybeWebhook(b, webhookUrl);

		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("Create HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

	public static String register(String baseUrl, String gameId, String webhookUrl, String teamName, String teamPassword, String rsn)
		throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games/" + encPath(gameId) + "/register";

		String body = "{\"teamName\":\"" + escape(teamName) + "\"," +
			"\"teamPassword\":\"" + escape(teamPassword) + "\"," +
			"\"rsn\":\"" + escape(rsn) + "\"}";

		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.header("content-type", "application/json")
			.header("accept", "application/json");

		maybeWebhook(b, webhookUrl);

		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("Register HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

	public static String roll(String baseUrl, String gameId, String webhookUrl, String jwtToken) throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games/" + encPath(gameId) + "/roll";

		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.header("content-type", "application/json")
			.header("accept", "application/json")
			.header("authorization", "Bearer " + (jwtToken == null ? "" : jwtToken.trim()));

		maybeWebhook(b, webhookUrl);

		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("Roll HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

	public static String submitProof(String baseUrl, String gameId, String webhookUrl, String jwtToken, String urlToProof)
		throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games/" + encPath(gameId) + "/proof";

		String body = "{\"url\":\"" + escape(urlToProof) + "\"}";

		HttpRequest.Builder b = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.header("content-type", "application/json")
			.header("accept", "application/json")
			.header("authorization", "Bearer " + (jwtToken == null ? "" : jwtToken.trim()));

		maybeWebhook(b, webhookUrl);

		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("Proof HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

	private static String escape(String s)
	{
		if (s == null) return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}

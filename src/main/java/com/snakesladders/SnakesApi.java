package com.snakesladders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SnakesApi
{
	private static final HttpClient HTTP = HttpClient.newBuilder().build();

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

	public static String getGameState(String baseUrl, String gameId) throws IOException, InterruptedException
	{
		String url = normalizeBaseUrl(baseUrl) + "/games/" + gameId + "/state";
		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.header("accept", "application/json")
			.build();

		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() >= 400) throw new IOException("State HTTP " + res.statusCode() + ": " + res.body());
		return res.body();
	}

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
		String url = normalizeBaseUrl(baseUrl) + "/games/" + gameId + "/register";

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
		String url = normalizeBaseUrl(baseUrl) + "/games/" + gameId + "/roll";

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
		String url = normalizeBaseUrl(baseUrl) + "/games/" + gameId + "/proof";

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

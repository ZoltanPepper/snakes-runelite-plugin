package com.snakesladders;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("snakesladders")
public interface SnakesLaddersConfig extends Config
{
	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API Base URL",
		description = "Backend base URL, e.g. http://127.0.0.1:8787"
	)
	default String apiBaseUrl()
	{
		return "http://127.0.0.1:8787";
	}

	@ConfigItem(
		keyName = "discordWebhookUrl",
		name = "Discord Webhook URL",
		description = "Webhook used for posting rolls/proofs (sent to backend on requests).",
		secret = true
	)
	default String discordWebhookUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "gameId",
		name = "Game Code",
		description = "Paste the game code (game_xxx)"
	)
	default String gameId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "jwtToken",
		name = "JWT Token",
		description = "Stored after joining a team",
		secret = true
	)
	default String jwtToken()
	{
		return "";
	}
}

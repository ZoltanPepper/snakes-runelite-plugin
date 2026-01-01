package com.snakesladders;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@PluginDescriptor(name = "Snakes & Ladders")
public class SnakesLaddersPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private SnakesLaddersConfig config;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ConfigManager configManager;

	@Inject private InfoBoxManager infoBoxManager;

	private final Gson gson = new Gson();

	private SnakesLaddersPanel panel;
	private NavigationButton navButton;

	// Overlay polling + local tick for InfoBox
	private Timer overlayPollTimer;
	private Timer overlayTickTimer;

	private String currentClanName = "Sixth Degree";
	private String currentTeamName = "-";

	// InfoBox + overlay polling state
	private SnakesTileInfoBox tileInfoBox;
	private String overlayEtag;
	private OverlaySnapshot overlaySnapshot; // last parsed overlay

	@Override
	protected void startUp()
	{
		panel = new SnakesLaddersPanel();

		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		navButton = NavigationButton.builder()
			.tooltip("Snakes & Ladders")
			.icon(icon)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Wire new panel buttons
		panel.connectButton.addActionListener(e -> clientThread.invokeLater(this::connect));
		panel.disconnectButton.addActionListener(e -> clientThread.invokeLater(this::disconnect));
		panel.actionButton.addActionListener(e -> clientThread.invokeLater(this::action));

		updateHeader();

		boolean connected = config.gameId() != null && !config.gameId().trim().isEmpty();
		panel.setConnected(connected);
		panel.setStatus(connected ? "Connected" : "Not connected");

		if (connected)
		{
			ensureInfoBox();
			startOverlayPolling();
		}
	}

	@Override
	protected void shutDown()
	{
		stopOverlayPolling();
		removeInfoBox();

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged ev)
	{
		if (ev.getGameState() == GameState.LOGGED_IN)
		{
			updateHeader();
		}
	}

	private void updateHeader()
	{
		if (panel == null) return;

		String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "-";
		panel.setHeader(currentClanName, currentTeamName, rsn);
	}

	/* -------------------- New flow: Connect / Disconnect / Action -------------------- */

	private void connect()
	{
		if (panel == null) return;

		String gameId = JOptionPane.showInputDialog(panel, "Paste Game Code (game_xxx):", config.gameId());
		if (gameId == null || gameId.trim().isEmpty()) return;

		configManager.setConfiguration("snakesladders", "gameId", gameId.trim());

		// Note: JWT stays as-is; if they previously joined, they can still roll/proof
		panel.setConnected(true);
		panel.setStatus("Connected");
		panel.setTile(0);
		panel.setAwaitingProof(false);
		panel.setCanRoll(false);

		ensureInfoBox();
		startOverlayPolling();

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Snakes & Ladders: Connected to " + gameId.trim(), null);
	}

	private void disconnect()
	{
		if (panel == null) return;

		configManager.setConfiguration("snakesladders", "gameId", "");
		configManager.setConfiguration("snakesladders", "jwtToken", "");

		currentTeamName = "-";
		updateHeader();

		panel.setConnected(false);
		panel.setStatus("Not connected");
		panel.setTile(0);
		panel.setAwaitingProof(false);
		panel.setCanRoll(false);

		stopOverlayPolling();
		removeInfoBox();
	}

	private void action()
	{
		if (panel == null) return;

		// Contextual: if awaiting proof, submit proof; else roll (if allowed)
		if (overlaySnapshot != null && overlaySnapshot.awaitingProof)
		{
			submitProof();
		}
		else
		{
			rollDice();
		}
	}

	/* -------------------- InfoBox lifecycle + overlay polling -------------------- */

	private void ensureInfoBox()
	{
		if (tileInfoBox != null) return;

		// Placeholder icon for now. Later we’ll swap icon to tile image.
		tileInfoBox = new SnakesTileInfoBox(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
		infoBoxManager.addInfoBox(tileInfoBox);

		tileInfoBox.setTooltipLines("Snakes & Ladders", "Connecting…");
	}

	private void removeInfoBox()
	{
		if (tileInfoBox != null)
		{
			infoBoxManager.removeInfoBox(tileInfoBox);
			tileInfoBox = null;
		}
	}

	private void startOverlayPolling()
	{
		stopOverlayPolling();

		overlayEtag = null;
		overlaySnapshot = null;

		// Poll backend overlay endpoint every 5s (ETag/304)
		overlayPollTimer = new Timer("snakes-overlay-poll", true);
		overlayPollTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				clientThread.invokeLater(() ->
				{
					try
					{
						pollOverlayOnce();
					}
					catch (Exception ex)
					{
						log.debug("Overlay poll error", ex);
						if (tileInfoBox != null)
						{
							tileInfoBox.setText("");
							tileInfoBox.setStatus("Overlay offline");
							tileInfoBox.setTooltipLines("Snakes & Ladders", "Overlay endpoint unreachable.");
						}

						if (panel != null)
						{
							panel.setStatus("Overlay offline");
							panel.setCanRoll(false);
						}
					}
				});
			}
		}, 0, 5_000);

		// Local 1s tick to keep countdown text smooth
		overlayTickTimer = new Timer("snakes-overlay-tick", true);
		overlayTickTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				clientThread.invokeLater(() ->
				{
					if (tileInfoBox == null) return;
					if (overlaySnapshot == null) return;

					tileInfoBox.setText(computeCountdownText(overlaySnapshot));
				});
			}
		}, 1_000, 1_000);
	}

	private void stopOverlayPolling()
	{
		if (overlayPollTimer != null)
		{
			overlayPollTimer.cancel();
			overlayPollTimer = null;
		}
		if (overlayTickTimer != null)
		{
			overlayTickTimer.cancel();
			overlayTickTimer = null;
		}
	}

	private void pollOverlayOnce() throws Exception
	{
		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();

		if (gameId == null || gameId.trim().isEmpty())
		{
			return;
		}

		String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
		if (rsn == null) rsn = "";

		ensureInfoBox();

		SnakesApi.ApiResult res = SnakesApi.getOverlay(baseUrl, gameId.trim(), rsn.trim(), overlayEtag);

		if (res.etag != null && !res.etag.trim().isEmpty())
		{
			overlayEtag = res.etag.trim();
		}

		if (res.isNotModified())
		{
			return;
		}

		if (res.body == null || res.body.trim().isEmpty())
		{
			return;
		}

		JsonObject root = gson.fromJson(res.body, JsonObject.class);
		OverlaySnapshot snap = OverlaySnapshot.fromJson(root);
		overlaySnapshot = snap;

		// Update panel from overlay
		if (panel != null)
		{
			panel.setConnected(true);
			panel.setTile(snap.tileIndex);
			panel.setAwaitingProof(snap.awaitingProof);

			// canRoll comes from backend flags when you add it; for now:
			boolean hasJwt = config.jwtToken() != null && !config.jwtToken().trim().isEmpty();
			boolean canRoll = hasJwt && !snap.awaitingProof && "running".equalsIgnoreCase(snap.phase);
			panel.setCanRoll(canRoll);

			panel.setStatus(statusFromPhase(snap.phase, snap.awaitingProof));
		}

		// Update InfoBox
		if (tileInfoBox != null)
		{
			tileInfoBox.setText(computeCountdownText(snap));

			String status = snap.awaitingProof ? "Awaiting proof" : "Snakes & Ladders";
			tileInfoBox.setStatus(status);

			if (snap.tileTitle != null && !snap.tileTitle.isEmpty())
			{
				tileInfoBox.setTooltipLines(
					"Tile " + snap.tileIndex + (snap.tileKind != null && !snap.tileKind.isEmpty() ? " (" + snap.tileKind + ")" : ""),
					snap.tileTitle,
					snap.tileDescription == null ? "" : snap.tileDescription,
					snap.awaitingProof ? "Proof required" : ""
				);
			}
			else
			{
				tileInfoBox.setTooltipLines("Snakes & Ladders", "Waiting for tile…");
			}
		}
	}

	private static String statusFromPhase(String phase, boolean awaitingProof)
	{
		if (awaitingProof) return "Awaiting proof";
		if (phase == null) return "Connected";
		if ("prestart".equalsIgnoreCase(phase)) return "Waiting for start";
		if ("running".equalsIgnoreCase(phase)) return "Running";
		if ("ended".equalsIgnoreCase(phase)) return "Ended";
		return "Connected";
	}

	private String computeCountdownText(OverlaySnapshot snap)
	{
		Instant now = Instant.now();
		Instant target;

		if ("prestart".equalsIgnoreCase(snap.phase))
		{
			target = snap.startTime;
		}
		else if ("running".equalsIgnoreCase(snap.phase))
		{
			target = snap.endTime;
		}
		else
		{
			return "";
		}

		if (target == null) return "";

		long seconds = Duration.between(now, target).getSeconds();
		if (seconds < 0) seconds = 0;

		return formatSeconds(seconds);
	}

	private static String formatSeconds(long seconds)
	{
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;

		if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
		return String.format("%d:%02d", m, s);
	}

	/* -------------------- Roll + Proof (kept) -------------------- */

	private void rollDice()
	{
		if (panel == null) return;

		String token = config.jwtToken();
		if (token == null || token.trim().isEmpty())
		{
			panel.setStatus("No team token (join via web)");
			panel.setCanRoll(false);
			return;
		}

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();
		String webhook = config.discordWebhookUrl();

		if (gameId == null || gameId.trim().isEmpty())
		{
			panel.setStatus("Not connected");
			return;
		}

		try
		{
			String json = SnakesApi.roll(baseUrl, gameId, webhook, token);
			JsonObject root = gson.fromJson(json, JsonObject.class);

			int roll = root.has("roll") ? root.get("roll").getAsInt() : 0;
			int from = root.has("from") ? root.get("from").getAsInt() : 0;
			int to = root.has("to") ? root.get("to").getAsInt() : 0;

			boolean awaiting = root.has("awaitingProof") && root.get("awaitingProof").getAsBoolean();

			panel.setTile(to);
			panel.setAwaitingProof(awaiting);
			panel.setStatus(awaiting ? "Awaiting proof" : "Running");
			panel.setCanRoll(!awaiting);

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Snakes & Ladders: Rolled " + roll + " (" + from + " → " + to + ")", null);
		}
		catch (Exception ex)
		{
			log.warn("Roll error", ex);
			panel.setStatus("Roll error");
			panel.setCanRoll(false);
		}
	}

	private void submitProof()
	{
		if (panel == null) return;

		String token = config.jwtToken();
		if (token == null || token.trim().isEmpty())
		{
			panel.setStatus("No team token (join via web)");
			return;
		}

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();
		String webhook = config.discordWebhookUrl();

		if (gameId == null || gameId.trim().isEmpty())
		{
			panel.setStatus("Not connected");
			return;
		}

		String proofUrl = JOptionPane.showInputDialog(panel, "Paste proof URL (https://...):");
		if (proofUrl == null || proofUrl.trim().isEmpty()) return;

		try
		{
			String json = SnakesApi.submitProof(baseUrl, gameId, webhook, token, proofUrl.trim());
			JsonObject root = gson.fromJson(json, JsonObject.class);

			if (root.has("finished") && root.get("finished").getAsBoolean())
			{
				String winner = root.has("winner") ? root.get("winner").getAsString() : "Unknown";
				panel.setStatus("Ended - Winner: " + winner);
			}
			else
			{
				panel.setStatus("Proof submitted");
			}

			// Next overlay poll should clear awaiting proof
			panel.setAwaitingProof(false);
			panel.setCanRoll(false);
		}
		catch (Exception ex)
		{
			log.warn("Proof error", ex);
			panel.setStatus("Proof error");
		}
	}

	@Provides
	SnakesLaddersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SnakesLaddersConfig.class);
	}

	/* -------------------- Lightweight overlay snapshot parser -------------------- */

	private static final class OverlaySnapshot
	{
		final String phase;
		final Instant startTime;
		final Instant endTime;

		final int tileIndex;
		final String tileKind;
		final String tileTitle;
		final String tileDescription;

		final boolean awaitingProof;

		private OverlaySnapshot(
			String phase,
			Instant startTime,
			Instant endTime,
			int tileIndex,
			String tileKind,
			String tileTitle,
			String tileDescription,
			boolean awaitingProof
		)
		{
			this.phase = phase;
			this.startTime = startTime;
			this.endTime = endTime;
			this.tileIndex = tileIndex;
			this.tileKind = tileKind;
			this.tileTitle = tileTitle;
			this.tileDescription = tileDescription;
			this.awaitingProof = awaitingProof;
		}

		static OverlaySnapshot fromJson(JsonObject root)
		{
			String phase = root.has("phase") ? root.get("phase").getAsString() : "running";
			Instant start = parseInstant(root, "startTime");
			Instant end = parseInstant(root, "endTime");

			JsonObject tile = root.has("tile") && root.get("tile").isJsonObject() ? root.getAsJsonObject("tile") : null;

			int tileIndex = tile != null && tile.has("tileIndex") ? tile.get("tileIndex").getAsInt() : 0;
			String kind = tile != null && tile.has("kind") ? tile.get("kind").getAsString() : "";
			String title = tile != null && tile.has("title") && !tile.get("title").isJsonNull() ? tile.get("title").getAsString() : "";
			String desc = tile != null && tile.has("description") && !tile.get("description").isJsonNull() ? tile.get("description").getAsString() : "";

			JsonObject flags = root.has("flags") && root.get("flags").isJsonObject() ? root.getAsJsonObject("flags") : null;
			boolean awaiting = flags != null && flags.has("awaitingProof") && flags.get("awaitingProof").getAsBoolean();

			return new OverlaySnapshot(phase, start, end, tileIndex, kind, title, desc, awaiting);
		}

		private static Instant parseInstant(JsonObject root, String field)
		{
			try
			{
				if (!root.has(field) || root.get(field).isJsonNull()) return null;
				String s = root.get(field).getAsString();
				if (s == null || s.trim().isEmpty()) return null;
				return Instant.parse(s.trim());
			}
			catch (Exception ignored)
			{
				return null;
			}
		}
	}
}

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
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

	private Timer overlayPollTimer;
	private Timer overlayTickTimer;

	private String currentClanName = "Sixth Degree";
	private String currentTeamName = "-";

	private SnakesTileInfoBox tileInfoBox;
	private String overlayEtag;
	private OverlaySnapshot overlaySnapshot;

	@Override
	protected void startUp()
	{
		panel = new SnakesLaddersPanel();

		BufferedImage icon = buildNavIcon();
		navButton = NavigationButton.builder()
			.tooltip("Snakes & Ladders")
			.icon(icon)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Buttons
		panel.setupButton.addActionListener(e -> clientThread.invokeLater(this::openSetup));
		panel.viewBoardButton.addActionListener(e -> clientThread.invokeLater(this::openBoard));

		panel.connectButton.addActionListener(e -> clientThread.invokeLater(this::connect));
		panel.disconnectButton.addActionListener(e -> clientThread.invokeLater(this::disconnect));
		panel.actionButton.addActionListener(e -> clientThread.invokeLater(this::action));

		updateHeader();

		boolean hasGameId = hasGameId();
		panel.setHasGameId(hasGameId);

		// "Connected" for UI purposes means "has a gameId configured"
		panel.setConnected(hasGameId);
		panel.setStatus(hasGameId ? "Connected" : "Not connected");

		if (hasGameId)
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

	private boolean hasGameId()
	{
		String gameId = config.gameId();
		return gameId != null && !gameId.trim().isEmpty();
	}

	private String webBase()
	{
		String base = config.webBaseUrl();
		if (base == null) return "";
		base = base.trim();
		while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
		return base;
	}

	private void openSetup()
	{
		String base = webBase();
		if (base.isEmpty())
		{
			// If someone nuked config, don't crash the plugin—just no-op.
			return;
		}
		String url = base + "/index.html";
		LinkBrowser.browse(url);
	}

	private void openBoard()
	{
		String gameId = config.gameId();
		if (gameId == null || gameId.trim().isEmpty())
		{
			openSetup();
			return;
		}

		String base = webBase();
		if (base.isEmpty())
		{
			return;
		}

		String gid = gameId.trim();
		String url = base + "/view.html?gameId=" + urlEncode(gid);
		LinkBrowser.browse(url);
	}

	private static String urlEncode(String s)
	{
		try
		{
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
		}
		catch (Exception ignored)
		{
			return s;
		}
	}

	private static BufferedImage buildNavIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(240, 240, 240));
		g.fillRoundRect(2, 2, 12, 12, 3, 3);
		g.setColor(new Color(60, 60, 60));
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(2, 2, 12, 12, 3, 3);

		g.setColor(new Color(20, 20, 20));
		g.fillOval(4, 4, 2, 2);
		g.fillOval(10, 4, 2, 2);
		g.fillOval(7, 7, 2, 2);
		g.fillOval(4, 10, 2, 2);
		g.fillOval(10, 10, 2, 2);

		int[][] snake = {
			{4, 11}, {5, 10}, {6, 9}, {7, 8}, {8, 7}, {9, 6}, {10, 5}, {11, 4}
		};
		for (int i = 0; i < snake.length; i++)
		{
			g.setColor(i % 2 == 0 ? new Color(0, 120, 0) : new Color(0, 180, 0));
			g.fillRect(snake[i][0], snake[i][1], 1, 1);
		}
		g.setColor(new Color(0, 180, 0));
		g.fillRect(11, 4, 2, 1);
		g.fillRect(12, 5, 1, 1);
		g.dispose();
		return icon;
	}

	private void connect()
	{
		if (panel == null) return;

		String gameId = JOptionPane.showInputDialog(panel, "Paste Game ID (game_xxx):", config.gameId());
		if (gameId == null || gameId.trim().isEmpty()) return;

		String trimmed = gameId.trim();
		configManager.setConfiguration("snakesladders", "gameId", trimmed);

		panel.setHasGameId(true);
		panel.setConnected(true);
		panel.setStatus("Connected");
		panel.setTile(0);
		panel.setAwaitingProof(false);
		panel.setCanRoll(false);

		ensureInfoBox();
		startOverlayPolling();

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Snakes & Ladders: Connected to " + trimmed, null);
	}

	private void disconnect()
	{
		if (panel == null) return;

		configManager.setConfiguration("snakesladders", "gameId", "");
		configManager.setConfiguration("snakesladders", "jwtToken", "");

		currentTeamName = "-";
		updateHeader();

		panel.setHasGameId(false);
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

		if (overlaySnapshot != null && overlaySnapshot.awaitingProof)
		{
			submitProof();
		}
		else
		{
			rollDice();
		}
	}

	private void ensureInfoBox()
	{
		if (tileInfoBox != null) return;

		tileInfoBox = new SnakesTileInfoBox(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), null);
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
			if (panel != null)
			{
				panel.setHasGameId(false);
				panel.setConnected(false);
				panel.setStatus("Not connected");
			}
			return;
		}

		// Keep panel view button in-sync (gameId exists)
		if (panel != null)
		{
			panel.setHasGameId(true);
			panel.setConnected(true);
		}

		String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
		if (rsn == null) rsn = "";

		ensureInfoBox();

		SnakesApi.ApiResult res = SnakesApi.getOverlay(baseUrl, gameId.trim(), rsn.trim(), overlayEtag);

		if (res.etag != null && !res.etag.trim().isEmpty())
		{
			overlayEtag = res.etag.trim();
		}

		if (res.isNotModified()) return;
		if (res.body == null || res.body.trim().isEmpty()) return;

		JsonObject root = gson.fromJson(res.body, JsonObject.class);
		OverlaySnapshot snap = OverlaySnapshot.fromJson(root);
		overlaySnapshot = snap;

		if (panel != null)
		{
			panel.setTile(snap.tileIndex);
			panel.setAwaitingProof(snap.awaitingProof);

			boolean hasJwt = config.jwtToken() != null && !config.jwtToken().trim().isEmpty();
			boolean canRoll = hasJwt && !snap.awaitingProof && "running".equalsIgnoreCase(snap.phase);
			panel.setCanRoll(canRoll);

			panel.setStatus(statusFromPhase(snap.phase, snap.awaitingProof));
		}

		if (tileInfoBox != null)
		{
			tileInfoBox.setText(computeCountdownText(snap));

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

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

	// Tile image cache + last key
	private final TileImageCache imageCache = new TileImageCache();
	private String lastImageKey;

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
		imageCache.shutdown();

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

		panel.setConnected(true);
		panel.setStatus("Connected");
		panel.setTile(0);
		panel.setAwaitingProof(false);
		panel.setCanRoll(false);

		lastImageKey = null;
		overlayEtag = null;
		overlaySnapshot = null;

		ensureInfoBox();
		startOverlayPolling();

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Snakes & Ladders: Connected to " + gameId.trim(), null);
	}

	private void disconnect()
	{
		if (panel == null) return;

		co

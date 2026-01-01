package com.snakesladders;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
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

	@Inject private OverlayManager overlayManager;
	@Inject private SnakesOverlay snakesOverlay;

	private final Gson gson = new Gson();

	private SnakesLaddersPanel panel;
	private NavigationButton navButton;

	private Timer refreshTimer;

	private String currentTeamName = "-";

	// ✅ shared overlay state
	private final OverlayModel overlayModel = new OverlayModel();
	public OverlayModel getOverlayModel()
	{
		return overlayModel;
	}

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

		// ✅ add overlay
		overlayManager.add(snakesOverlay);

		updateHeader();

		// Lobby buttons
		panel.newGameButton.addActionListener(e -> clientThread.invokeLater(this::newGame));
		panel.joinGameButton.addActionListener(e -> clientThread.invokeLater(this::joinGame));

		// In-game buttons
		panel.teamButton.addActionListener(e -> clientThread.invokeLater(this::createOrJoinTeam));
		panel.leaveGameButton.addActionListener(e -> clientThread.invokeLater(this::leaveGame));

		panel.rollButton.addActionListener(e -> clientThread.invokeLater(this::rollDice));
		panel.proofButton.addActionListener(e -> clientThread.invokeLater(this::submitProof));
		panel.refreshButton.addActionListener(e -> clientThread.invokeLater(this::refreshState));

		// initial UI state based on config
		boolean inGame = config.gameId() != null && !config.gameId().trim().isEmpty();
		panel.setInGame(inGame);

		boolean hasJwt = config.jwtToken() != null && !config.jwtToken().trim().isEmpty();
		panel.setHasTeam(hasJwt);

		panel.setStatus(inGame ? "In game (refreshing…)" : "Not in game");
		panel.setTileInfo(inGame ? "Click Refresh or wait 30s." : "Click Join Game or New Game.");

		overlayModel.setClanName("Snakes & Ladders");
		overlayModel.setTeamName(currentTeamName);
		overlayModel.setStatusLine(inGame ? "In game" : "Not in game");

		if (inGame)
		{
			startAutoRefresh();
			clientThread.invokeLater(this::refreshState);
		}
	}

	@Override
	protected void shutDown()
	{
		stopAutoRefresh();

		// ✅ remove overlay
		if (overlayManager != null && snakesOverlay != null)
		{
			overlayManager.remove(snakesOverlay);
		}

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
		panel.setHeader("Sixth Degree", currentTeamName, rsn);

		overlayModel.setClanName("Sixth Degree — Snakes & Ladders");
		overlayModel.setTeamName(currentTeamName);
	}

	private void startAutoRefresh()
	{
		stopAutoRefresh();
		refreshTimer = new Timer("snakes-refresh", true);
		refreshTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				clientThread.invokeLater(() ->
				{
					if (panel != null && panel.isShowing())
					{
						refreshState();
					}
				});
			}
		}, 0, 30_000);
	}

	private void stopAutoRefresh()
	{
		if (refreshTimer != null)
		{
			refreshTimer.cancel();
			refreshTimer = null;
		}
	}

	private void joinGame()
	{
		if (panel == null) return;

		String gameId = JOptionPane.showInputDialog(panel, "Paste Game Code (game_xxx):", config.gameId());
		if (gameId == null || gameId.trim().isEmpty()) return;

		configManager.setConfiguration("snakesladders", "gameId", gameId.trim());
		configManager.setConfiguration("snakesladders", "jwtToken", ""); // clear
		currentTeamName = "-";
		panel.setTeam("-");
		panel.setHasTeam(false);

		panel.setInGame(true);
		panel.setStatus("Joined game - not in a team");
		panel.setTileInfo("Click Create/Join Team, or Refresh to view standings.");

		overlayModel.setStatusLine("Joined game");
		overlayModel.setTileLine("");
		overlayModel.setCountdownLine("");

		startAutoRefresh();
		refreshState();
	}

	private void leaveGame()
	{
		if (panel == null) return;

		configManager.setConfiguration("snakesladders", "gameId", "");
		configManager.setConfiguration("snakesladders", "jwtToken", "");

		currentTeamName = "-";
		panel.setTeam("-");
		panel.setHasTeam(false);
		panel.setAwaitingProof(false);
		panel.setTile(0);
		panel.clearStandings();

		panel.setInGame(false);
		panel.setStatus("Not in game");
		panel.setTileInfo("Click Join Game or New Game.");

		overlayModel.setTeamName("-");
		overlayModel.setStatusLine("Not in game");
		overlayModel.setTileLine("");
		overlayModel.setCountdownLine("");
		overlayModel.setTileImage(null);

		stopAutoRefresh();
	}

	private void newGame()
	{
		if (panel == null) return;

		String baseUrl = config.apiBaseUrl();
		String webhook = config.discordWebhookUrl();

		String clan = JOptionPane.showInputDialog(panel, "Clan name:", "Sixth Degree");
		if (clan == null || clan.trim().isEmpty()) return;

		String hostPass = JOptionPane.showInputDialog(panel, "Admin password (hostPassword):");
		if (hostPass == null || hostPass.trim().isEmpty()) return;

		String boardUrl = JOptionPane.showInputDialog(panel, "Board JSON URL:", "https://zoltanpepper.github.io/snakes-board-6degree/board.json");
		if (boardUrl == null || boardUrl.trim().isEmpty()) return;

		String boardSizeStr = JOptionPane.showInputDialog(panel, "Board size (finish tile index):", "100");
		if (boardSizeStr == null || boardSizeStr.trim().isEmpty()) return;

		int boardSize;
		try { boardSize = Integer.parseInt(boardSizeStr.trim()); }
		catch (Exception e) { panel.setStatus("Invalid board size"); return; }

		try
		{
			String json = SnakesApi.createGame(baseUrl, webhook, clan.trim(), hostPass.trim(), boardSize, boardUrl.trim());
			JsonObject root = gson.fromJson(json, JsonObject.class);

			String gameId = root.has("gameId") ? root.get("gameId").getAsString() : "";
			if (gameId.isEmpty())
			{
				panel.setStatus("Create failed (no gameId)");
				return;
			}

			// save + join immediately
			configManager.setConfiguration("snakesladders", "gameId", gameId);
			configManager.setConfiguration("snakesladders", "jwtToken", "");

			panel.setInGame(true);
			panel.setHasTeam(false);
			currentTeamName = "-";
			panel.setTeam("-");

			panel.setStatus("Game created");
			panel.setTileInfo("Game Code:\n" + gameId + "\n\nShare this code with your clan.\nThen click Create/Join Team.");

			overlayModel.setClanName(clan.trim() + " — Snakes & Ladders");
			overlayModel.setStatusLine("Game created");
			overlayModel.setTileLine("Share code: " + gameId);

			startAutoRefresh();
			refreshState();

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Snakes & Ladders: Game created: " + gameId, null);
		}
		catch (Exception ex)
		{
			log.warn("Create game error", ex);
			panel.setStatus("Create game error");
			overlayModel.setStatusLine("Create game error");
		}
	}

	private void createOrJoinTeam()
	{
		if (panel == null) return;

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();
		String webhook = config.discordWebhookUrl();

		if (gameId == null || gameId.trim().isEmpty())
		{
			panel.setStatus("No game joined");
			return;
		}

		String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
		if (rsn == null || rsn.trim().isEmpty())
		{
			panel.setStatus("RSN not found");
			return;
		}

		String team = JOptionPane.showInputDialog(panel, "Team name:", "");
		if (team == null || team.trim().isEmpty()) return;

		String pass = JOptionPane.showInputDialog(panel, "Team password (new or existing):");
		if (pass == null || pass.trim().isEmpty()) return;

		try
		{
			String json = SnakesApi.register(baseUrl, gameId, webhook, team.trim(), pass.trim(), rsn.trim());
			JsonObject root = gson.fromJson(json, JsonObject.class);

			String token = root.has("token") ? root.get("token").getAsString() : "";
			String teamName = root.has("teamName") ? root.get("teamName").getAsString() : team.trim();

			if (token.isEmpty())
			{
				panel.setStatus("Join team failed");
				return;
			}

			configManager.setConfiguration("snakesladders", "jwtToken", token);

			currentTeamName = teamName;
			panel.setTeam(teamName);
			panel.setHasTeam(true);
			panel.setStatus("In team: " + teamName);

			overlayModel.setTeamName(teamName);
			overlayModel.setStatusLine("In team");

			updateHeader();
			refreshState();
		}
		catch (Exception ex)
		{
			log.warn("Join team error", ex);
			panel.setStatus("Join team error");
			overlayModel.setStatusLine("Join team error");
		}
	}

	private void refreshState()
	{
		if (panel == null) return;

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();

		if (gameId == null || gameId.trim().isEmpty())
		{
			panel.setStatus("Not in game");
			return;
		}

		try
		{
			String json = SnakesApi.getGameState(baseUrl, gameId);
			JsonObject root = gson.fromJson(json, JsonObject.class);

			JsonObject game = root.has("game") && root.get("game").isJsonObject() ? root.getAsJsonObject("game") : null;
			JsonArray teams = root.has("teams") && root.get("teams").isJsonArray() ? root.getAsJsonArray("teams") : new JsonArray();

			panel.clearStandings();

			JsonObject myTeam = null;

			for (JsonElement el : teams)
			{
				if (!el.isJsonObject()) continue;
				JsonObject t = el.getAsJsonObject();

				String name = t.has("name") ? t.get("name").getAsString() : "-";
				int pos = t.has("position") ? t.get("position").getAsInt() : 0;

				String taskTitle = "-";
				if (t.has("activeTile") && t.get("activeTile").isJsonObject())
				{
					JsonObject at = t.getAsJsonObject("activeTile");
					if (at.has("title") && !at.get("title").isJsonNull())
						taskTitle = at.get("title").getAsString();
				}

				panel.addStandingRow(name, pos, taskTitle);

				if (!currentTeamName.equals("-") && name.equalsIgnoreCase(currentTeamName))
				{
					myTeam = t;
				}
			}

			boolean awaiting = false;
			int myPos = 0;
			String info = "Click Create/Join Team to participate.";

			if (myTeam != null)
			{
				myPos = myTeam.has("position") ? myTeam.get("position").getAsInt() : 0;
				awaiting = myTeam.has("awaitingProof") && myTeam.get("awaitingProof").getAsBoolean();

				info = "Tile " + myPos;
				String title = "-";
				String kind = "empty";

				if (myTeam.has("activeTile") && myTeam.get("activeTile").isJsonObject())
				{
					JsonObject at = myTeam.getAsJsonObject("activeTile");
					kind = at.has("kind") ? at.get("kind").getAsString() : "empty";
					if (at.has("title") && !at.get("title").isJsonNull())
						title = at.get("title").getAsString();
				}

				info = "Tile " + myPos + " (" + kind + ")\n" + title;

				overlayModel.setTileLine("Tile " + myPos);
				overlayModel.setStatusLine(awaiting ? "Awaiting proof" : "Ready");
			}
			else
			{
				overlayModel.setTileLine("");
				overlayModel.setStatusLine("Spectating / not in team");
			}

			panel.setTile(myPos);
			panel.setAwaitingProof(awaiting);

			String status = "Ready";
			if (game != null && game.has("status"))
			{
				String gs = game.get("status").getAsString();
				if ("finished".equalsIgnoreCase(gs)) status = "Finished";
			}
			if (awaiting) status = "Awaiting proof";

			panel.setStatus(status);
			panel.setTileInfo(info);
		}
		catch (Exception ex)
		{
			log.warn("Refresh error", ex);
			panel.setStatus("Error fetching state");
			overlayModel.setStatusLine("State fetch error");
		}
	}

	private void rollDice()
	{
		if (panel == null) return;

		String token = config.jwtToken();
		if (token == null || token.trim().isEmpty())
		{
			panel.setStatus("Join a team first");
			panel.setHasTeam(false);
			return;
		}

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();
		String webhook = config.discordWebhookUrl();

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
			panel.setStatus(awaiting ? "Awaiting proof" : "Ready");
			panel.setTileInfo("Rolled " + roll + "\n" + from + " → " + to);

			overlayModel.setTileLine("Tile " + to);
			overlayModel.setStatusLine(awaiting ? "Awaiting proof" : "Ready");

			refreshState();
		}
		catch (Exception ex)
		{
			log.warn("Roll error", ex);
			panel.setStatus("Roll error");
			overlayModel.setStatusLine("Roll error");
		}
	}

	private void submitProof()
	{
		if (panel == null) return;

		String token = config.jwtToken();
		if (token == null || token.trim().isEmpty())
		{
			panel.setStatus("Join a team first");
			panel.setHasTeam(false);
			return;
		}

		String baseUrl = config.apiBaseUrl();
		String gameId = config.gameId();
		String webhook = config.discordWebhookUrl();

		String proofUrl = JOptionPane.showInputDialog(panel, "Paste proof URL (https://...):");
		if (proofUrl == null || proofUrl.trim().isEmpty()) return;

		try
		{
			String json = SnakesApi.submitProof(baseUrl, gameId, webhook, token, proofUrl.trim());
			JsonObject root = gson.fromJson(json, JsonObject.class);

			if (root.has("finished") && root.get("finished").getAsBoolean())
			{
				String winner = root.has("winner") ? root.get("winner").getAsString() : "Unknown";
				panel.setStatus("Finished - Winner: " + winner);
				overlayModel.setStatusLine("Finished");
			}
			else
			{
				panel.setStatus("Proof submitted");
				overlayModel.setStatusLine("Proof submitted");
			}

			refreshState();
		}
		catch (Exception ex)
		{
			log.warn("Proof error", ex);
			panel.setStatus("Proof error");
			overlayModel.setStatusLine("Proof error");
		}
	}

	@Provides
	SnakesLaddersConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SnakesLaddersConfig.class);
	}
}

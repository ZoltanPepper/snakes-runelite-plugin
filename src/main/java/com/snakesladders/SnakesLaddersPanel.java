package com.snakesladders;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class SnakesLaddersPanel extends PluginPanel
{
	// Header labels
	private final JLabel clanLabel = new JLabel("-");
	private final JLabel teamLabel = new JLabel("-");
	private final JLabel rsnLabel = new JLabel("-");
	private final JLabel statusLabel = new JLabel("-");
	private final JLabel tileLabel = new JLabel("-");
	private final JLabel awaitingLabel = new JLabel("-");

	// Tile/task info
	private final JTextArea tileInfoArea = new JTextArea();

	// Lobby buttons
	public final JButton newGameButton = new JButton("New Game (Admin)");
	public final JButton joinGameButton = new JButton("Join Game");

	// In-game buttons
	public final JButton teamButton = new JButton("Create / Join Team");
	public final JButton leaveGameButton = new JButton("Leave Game");
	public final JButton rollButton = new JButton("Roll Dice");
	public final JButton proofButton = new JButton("Submit Proof");
	public final JButton refreshButton = new JButton("Refresh");

	// Standings table
	private final DefaultTableModel standingsModel = new DefaultTableModel(
		new Object[]{"Team", "Tile", "Task"}, 0
	)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};
	private final JTable standingsTable = new JTable(standingsModel);

	private boolean inGame = false;
	private boolean hasTeam = false;
	private boolean awaitingProof = false;

	public SnakesLaddersPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(buildHeader());
		top.add(Box.createVerticalStrut(8));
		top.add(buildTileInfo());

		add(top, BorderLayout.NORTH);
		add(buildStandings(), BorderLayout.CENTER);
		add(buildActions(), BorderLayout.SOUTH);

		// Defaults
		tileInfoArea.setLineWrap(true);
		tileInfoArea.setWrapStyleWord(true);
		tileInfoArea.setEditable(false);
		tileInfoArea.setRows(5);

		standingsTable.setFillsViewportHeight(true);
		standingsTable.setRowSelectionAllowed(false);
		standingsTable.setCellSelectionEnabled(false);

		setTileInfo("UI ready.");
		setStatus("Not in game");
		setTile(0);
		setAwaitingProof(false);
		setTeam("-");
		setInGame(false);
	}

	private JPanel buildHeader()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 3));
		p.setBorder(BorderFactory.createTitledBorder("Snakes & Ladders"));

		p.add(line("Clan:", clanLabel));
		p.add(line("Team:", teamLabel));
		p.add(line("RSN:", rsnLabel));
		p.add(line("Status:", statusLabel));
		p.add(line("Tile:", tileLabel));
		p.add(line("Awaiting proof:", awaitingLabel));

		return p;
	}

	private JPanel buildTileInfo()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createTitledBorder("Current tile / task"));
		p.add(new JScrollPane(tileInfoArea), BorderLayout.CENTER);
		return p;
	}

	private JPanel buildStandings()
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBorder(BorderFactory.createTitledBorder("Standings"));

		JScrollPane sp = new JScrollPane(standingsTable);
		sp.setBorder(BorderFactory.createEmptyBorder());
		p.add(sp, BorderLayout.CENTER);

		return p;
	}

	private JPanel buildActions()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
		p.setBorder(new EmptyBorder(10, 0, 0, 0));

		p.add(newGameButton);
		p.add(joinGameButton);

		p.add(teamButton);
		p.add(leaveGameButton);

		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.add(rollButton);
		row.add(proofButton);
		p.add(row);

		p.add(refreshButton);

		refreshButtons();
		return p;
	}

	private JPanel line(String left, JLabel right)
	{
		JPanel row = new JPanel(new BorderLayout());
		JLabel l = new JLabel(left);
		l.setForeground(Color.GRAY);
		row.add(l, BorderLayout.WEST);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private void refreshButtons()
	{
		// Lobby visible when not in game
		newGameButton.setVisible(!inGame);
		joinGameButton.setVisible(!inGame);

		// In-game visible when in game
		teamButton.setVisible(inGame);
		leaveGameButton.setVisible(inGame);
		rollButton.setVisible(inGame);
		proofButton.setVisible(inGame);
		refreshButton.setVisible(inGame);

		teamButton.setEnabled(inGame);
		leaveGameButton.setEnabled(inGame);

		rollButton.setEnabled(inGame && hasTeam && !awaitingProof);
		proofButton.setEnabled(inGame && hasTeam && awaitingProof);
		refreshButton.setEnabled(inGame);
	}

	public void setHeader(String clan, String team, String rsn)
	{
		clanLabel.setText(blankToDash(clan));
		teamLabel.setText(blankToDash(team));
		rsnLabel.setText(blankToDash(rsn));
	}

	public void setTeam(String team)
	{
		teamLabel.setText(blankToDash(team));
	}

	public void setStatus(String status)
	{
		statusLabel.setText(blankToDash(status));
	}

	public void setTile(int tile)
	{
		tileLabel.setText(String.valueOf(tile));
	}

	public void setAwaitingProof(boolean awaiting)
	{
		this.awaitingProof = awaiting;
		awaitingLabel.setText(awaiting ? "YES" : "NO");
		refreshButtons();
	}

	public void setInGame(boolean inGame)
	{
		this.inGame = inGame;
		refreshButtons();
	}

	public void setHasTeam(boolean hasTeam)
	{
		this.hasTeam = hasTeam;
		refreshButtons();
	}

	public void setTileInfo(String text)
	{
		tileInfoArea.setText(text == null ? "" : text);
	}

	public void clearStandings()
	{
		standingsModel.setRowCount(0);
	}

	public void addStandingRow(String team, int tile, String task)
	{
		standingsModel.addRow(new Object[]{
			team == null ? "-" : team,
			tile,
			task == null ? "-" : task
		});
	}

	private static String blankToDash(String s)
	{
		return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
	}
}

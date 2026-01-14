package com.snakesladders;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SnakesLaddersPanel extends PluginPanel
{
	private final JLabel clanLabel = new JLabel("-");
	private final JLabel teamLabel = new JLabel("-");
	private final JLabel rsnLabel  = new JLabel("-");
	private final JLabel statusLabel = new JLabel("-");
	private final JLabel tileLabel = new JLabel("-");
	private final JLabel proofLabel = new JLabel("-");
	private final JLabel canRollLabel = new JLabel("-");

	public final JButton setupButton = new JButton("Set Up (Open Website)");
	public final JButton viewBoardButton = new JButton("View Board");

	public final JButton connectButton = new JButton("Connect");
	public final JButton disconnectButton = new JButton("Disconnect");
	public final JButton actionButton = new JButton("Roll");

	private boolean connected = false;
	private boolean awaitingProof = false;
	private boolean canRoll = false;
	private boolean hasGameId = false;

	public SnakesLaddersPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(buildHeader());

		add(top, BorderLayout.NORTH);
		add(buildActions(), BorderLayout.SOUTH);

		setStatus("Not connected");
		setTile(0);
		setAwaitingProof(false);
		setCanRoll(false);
		setHasGameId(false);
		setConnected(false);
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
		p.add(line("Awaiting proof:", proofLabel));
		p.add(line("Can roll:", canRollLabel));

		return p;
	}

	private JPanel buildActions()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
		p.setBorder(new EmptyBorder(10, 0, 0, 0));

		p.add(setupButton);
		p.add(viewBoardButton);

		p.add(connectButton);
		p.add(disconnectButton);
		p.add(actionButton);

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
		connectButton.setVisible(!connected);
		disconnectButton.setVisible(connected);

		// action only makes sense when connected (i.e., gameId set)
		actionButton.setEnabled(connected);

		// setup is always enabled
		setupButton.setEnabled(true);

		// viewBoard always enabled (opens index if no gameId)
		viewBoardButton.setEnabled(true);
	}

	private void refreshActionLabel()
	{
		// Awaiting proof takes priority; otherwise roll
		if (awaitingProof)
		{
			actionButton.setText("Submit Proof");
		}
		else
		{
			actionButton.setText("Roll");
		}
	}

	public void setHeader(String clan, String team, String rsn)
	{
		clanLabel.setText(blankToDash(clan));
		teamLabel.setText(blankToDash(team));
		rsnLabel.setText(blankToDash(rsn));
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
		proofLabel.setText(awaiting ? "YES" : "NO");
		refreshActionLabel();
	}

	public void setCanRoll(boolean canRoll)
	{
		this.canRoll = canRoll;
		canRollLabel.setText(canRoll ? "YES" : "NO");
		// label handled by awaitingProof, not canRoll
	}

	public void setConnected(boolean connected)
	{
		this.connected = connected;
		refreshButtons();
	}

	public void setHasGameId(boolean hasGameId)
	{
		this.hasGameId = hasGameId;
		refreshButtons();
	}

	private static String blankToDash(String s)
	{
		return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
	}
}

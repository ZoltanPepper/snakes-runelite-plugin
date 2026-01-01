package com.snakesladders;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

	// Main buttons (new flow)
	public final JButton connectButton = new JButton("Connect (Set Game ID)");
	public final JButton disconnectButton = new JButton("Disconnect");
	public final JButton actionButton = new JButton("…"); // Roll or Submit Proof

	// State flags controlled by plugin
	private boolean connected = false;
	private boolean awaitingProof = false;
	private boolean canRoll = false;

	public SnakesLaddersPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(buildHeader());
		top.add(Box.createVerticalStrut(8));
		top.add(buildHint());

		add(top, BorderLayout.NORTH);
		add(buildActions(), BorderLayout.SOUTH);

		setStatus("Not connected");
		setTile(0);
		setAwaitingProof(false);
		setConnected(false);
		setCanRoll(false);
		setTeam("-");
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

	private JComponent buildHint()
	{
		JTextArea hint = new JTextArea(
			"Use the website to create/join teams.\n" +
			"RuneLite only needs the Game ID.\n\n" +
			"Tile details + countdown are shown in the buff/timer InfoBox."
		);
		hint.setEditable(false);
		hint.setLineWrap(true);
		hint.setWrapStyleWord(true);
		hint.setOpaque(false);
		hint.setBorder(BorderFactory.createTitledBorder("How this works"));
		return hint;
	}

	private JPanel buildActions()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
		p.setBorder(new EmptyBorder(10, 0, 0, 0));

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

		// Action button visible when connected
		actionButton.setVisible(connected);

		// Contextual action
		if (!connected)
		{
			actionButton.setText("…");
			actionButton.setEnabled(false);
			return;
		}

		if (awaitingProof)
		{
			actionButton.setText("Submit Proof");
			actionButton.setEnabled(true);
		}
		else if (canRoll)
		{
			actionButton.setText("Roll");
			actionButton.setEnabled(true);
		}
		else
		{
			actionButton.setText("Waiting…");
			actionButton.setEnabled(false);
		}
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

	public void setConnected(boolean connected)
	{
		this.connected = connected;
		refreshButtons();
	}

	public void setCanRoll(boolean canRoll)
	{
		this.canRoll = canRoll;
		refreshButtons();
	}

	private static String blankToDash(String s)
	{
		return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
	}
}

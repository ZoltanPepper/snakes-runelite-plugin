package com.snakesladders;

import java.awt.image.BufferedImage;

/**
 * Simple mutable model for UI/InfoBox display.
 * (We’ll likely delete this later once the InfoBox is fully driven by /overlay.)
 */
public final class OverlayModel
{
	private String clanName = "";
	private String teamName = "";
	private String statusLine = "";
	private String countdownLine = "";
	private String tileLine = "";
	private String tileTitle = "";
	private String tileDescription = "";
	private BufferedImage tileImage = null;

	/* -------------------- getters -------------------- */

	public synchronized String getClanName()
	{
		return clanName;
	}

	public synchronized String getTeamName()
	{
		return teamName;
	}

	public synchronized String getStatusLine()
	{
		return statusLine;
	}

	public synchronized String getCountdownLine()
	{
		return countdownLine;
	}

	public synchronized String getTileLine()
	{
		return tileLine;
	}

	public synchronized String getTileTitle()
	{
		return tileTitle;
	}

	public synchronized String getTileDescription()
	{
		return tileDescription;
	}

	public synchronized BufferedImage getTileImage()
	{
		return tileImage;
	}

	/* -------------------- setters -------------------- */

	public synchronized void setClanName(String clanName)
	{
		this.clanName = clanName == null ? "" : clanName;
	}

	public synchronized void setTeamName(String teamName)
	{
		this.teamName = teamName == null ? "" : teamName;
	}

	public synchronized void setStatusLine(String statusLine)
	{
		this.statusLine = statusLine == null ? "" : statusLine;
	}

	public synchronized void setCountdownLine(String countdownLine)
	{
		this.countdownLine = countdownLine == null ? "" : countdownLine;
	}

	public synchronized void setTileLine(String tileLine)
	{
		this.tileLine = tileLine == null ? "" : tileLine;
	}

	public synchronized void setTileTitle(String tileTitle)
	{
		this.tileTitle = tileTitle == null ? "" : tileTitle;
	}

	public synchronized void setTileDescription(String tileDescription)
	{
		this.tileDescription = tileDescription == null ? "" : tileDescription;
	}

	public synchronized void setTileImage(BufferedImage tileImage)
	{
		this.tileImage = tileImage;
	}

	/* -------------------- helpers -------------------- */

	public synchronized void clearTile()
	{
		tileLine = "";
		tileTitle = "";
		tileDescription = "";
		tileImage = null;
	}
}

package com.snakesladders;

import java.awt.image.BufferedImage;

/**
 * Shared state model for the RuneLite overlay (canvas-style toolbox).
 * This is intentionally simple and mutable.
 */
public class OverlayModel
{
    private String clanName = "Snakes & Ladders";
    private String teamName = "-";

    private String statusLine = "Not in game";
    private String countdownLine = "";   // e.g. "Starts in 3d 7h"
    private String tileLine = "";        // e.g. "Tile 6"
    private String tileTitle = "";
    private String tileDescription = "";

    private BufferedImage tileImage;

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
        this.clanName = clanName;
    }

    public synchronized void setTeamName(String teamName)
    {
        this.teamName = teamName;
    }

    public synchronized void setStatusLine(String statusLine)
    {
        this.statusLine = statusLine;
    }

    public synchronized void setCountdownLine(String countdownLine)
    {
        this.countdownLine = countdownLine;
    }

    public synchronized void setTileLine(String tileLine)
    {
        this.tileLine = tileLine;
    }

    public synchronized void setTileTitle(String tileTitle)
    {
        this.tileTitle = tileTitle;
    }

    public synchronized void setTileDescription(String tileDescription)
    {
        this.tileDescription = tileDescription;
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

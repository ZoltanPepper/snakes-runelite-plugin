package com.snakesladders;

import net.runelite.client.ui.overlay.infobox.InfoBox;

import java.awt.image.BufferedImage;

/**
 * RuneLite-native InfoBox (buff/timer style).
 * Icon = tile image (later pulled from wiki/board).
 * Text = countdown (mm:ss or h:mm:ss).
 * Tooltip = status + tile title/description.
 */
public class SnakesTileInfoBox extends InfoBox
{
	private volatile String text = "";
	private volatile String status = "Snakes & Ladders";
	private volatile String tooltip = "Snakes & Ladders";

	public SnakesTileInfoBox(BufferedImage image)
	{
		super(image, null);
	}

	@Override
	public String getText()
	{
		return text;
	}

	@Override
	public String getTooltip()
	{
		return tooltip;
	}

	public void setText(String text)
	{
		this.text = text == null ? "" : text;
	}

	public void setStatus(String status)
	{
		this.status = (status == null || status.trim().isEmpty()) ? "Snakes & Ladders" : status.trim();
		// If caller hasn't set a multi-line tooltip, keep a sane default
		this.tooltip = this.status;
	}

	public void setTooltipLines(String... lines)
	{
		if (lines == null || lines.length == 0)
		{
			this.tooltip = status;
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(status);

		for (String line : lines)
		{
			if (line == null) continue;
			String s = line.trim();
			if (s.isEmpty()) continue;

			sb.append("\n").append(s);
		}

		this.tooltip = sb.toString();
	}

	public void setIcon(BufferedImage image)
	{
		if (image == null) return;
		setImage(image);
	}
}

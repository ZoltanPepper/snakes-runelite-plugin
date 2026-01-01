package com.snakesladders;

import net.runelite.client.ui.overlay.infobox.InfoBox;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * RuneLite-native InfoBox (buff/timer style).
 * Icon = tile image (later we’ll swap from placeholder to real image).
 * Text = countdown (mm:ss or h:mm:ss).
 * Tooltip = tile title + description + status.
 */
public class SnakesTileInfoBox extends InfoBox
{
	private volatile String text = "";
	private volatile String tooltip = "Snakes & Ladders";

	public SnakesTileInfoBox(BufferedImage image)
	{
		// Pass null plugin because we aren't using plugin hub auto-grouping here
		// (If you want strict grouping later, pass the plugin instance)
		super(image, null);
	}

	@Override
	public String getText()
	{
		return text;
	}

	@Override
	public Color getTextColor()
	{
		// RuneLite expects a color; white is consistent with timer/buff display.
		return Color.WHITE;
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

	public void setTooltipLines(String... lines)
	{
		if (lines == null || lines.length == 0)
		{
			this.tooltip = "Snakes & Ladders";
			return;
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;

		for (String line : lines)
		{
			if (line == null) continue;
			String s = line.trim();
			if (s.isEmpty()) continue;

			if (!first) sb.append("\n");
			sb.append(s);
			first = false;
		}

		this.tooltip = sb.length() == 0 ? "Snakes & Ladders" : sb.toString();
	}
}

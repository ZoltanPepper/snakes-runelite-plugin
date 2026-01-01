package com.snakesladders;

import net.runelite.client.ui.overlay.infobox.InfoBox;

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
	private volatile String status = "Snakes & Ladders";
	private volatile String tooltip = "Snakes & Ladders";

	public SnakesTileInfoBox(BufferedImage image)
	{
		// Pass null plugin because we aren't using plugin hub auto-grouping here
		// (This is acceptable; if you want strict grouping we can pass plugin instance later)
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
		this.status = status == null ? "" : status;
		// keep tooltip first line as status unless overridden
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

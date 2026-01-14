package com.snakesladders;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class SnakesTileInfoBox extends InfoBox
{
    private volatile String text = "";
    private volatile String tooltip = "Snakes & Ladders";
    private volatile String status = "Snakes & Ladders";

    public SnakesTileInfoBox(BufferedImage image, Plugin plugin)
    {
        super(image, plugin); // ✅ MUST NOT be null
    }

    @Override
    public String getText()
    {
        return text;
    }

    @Override
    public Color getTextColor()
    {
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

    public void setStatus(String status)
    {
        this.status = status == null ? "" : status;
    }

    public void setIcon(BufferedImage image)
    {
        if (image == null) return;
        setImage(image);
    }

    public void setTooltipLines(String... lines)
    {
        StringBuilder sb = new StringBuilder();

        if (status != null && !status.trim().isEmpty())
        {
            sb.append(status.trim());
        }

        if (lines != null)
        {
            for (String line : lines)
            {
                if (line == null) continue;
                String s = line.trim();
                if (s.isEmpty()) continue;

                if (sb.length() > 0) sb.append("\n");
                sb.append(s);
            }
        }

        this.tooltip = sb.length() == 0 ? "Snakes & Ladders" : sb.toString();
    }
}

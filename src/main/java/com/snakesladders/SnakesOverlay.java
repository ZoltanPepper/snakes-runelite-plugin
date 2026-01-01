package com.snakesladders;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * In-game "toolbox" overlay for Snakes & Ladders.
 * This behaves like a canvas overlay used by other RuneLite plugins.
 */
public class SnakesOverlay extends Overlay
{
    private static final int IMAGE_SIZE = 36;

    private final Client client;
    private final SnakesLaddersPlugin plugin;

    private final PanelComponent panel = new PanelComponent();

    @Inject
    public SnakesOverlay(Client client, SnakesLaddersPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);

        panel.setPreferredSize(new Dimension(180, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        OverlayModel model = plugin.getOverlayModel();
        if (model == null)
        {
            return null;
        }

        panel.getChildren().clear();

        // ---- Title ----
        panel.getChildren().add(
            TitleComponent.builder()
                .text(safe(model.getClanName()))
                .build()
        );

        // ---- Team ----
        panel.getChildren().add(
            LineComponent.builder()
                .left("Team")
                .right(safe(model.getTeamName()))
                .build()
        );

        // ---- Status ----
        String status = safe(model.getStatusLine());
        if (!status.isEmpty())
        {
            panel.getChildren().add(
                LineComponent.builder()
                    .left("Status")
                    .right(status)
                    .build()
            );
        }

        // ---- Countdown ----
        String countdown = safe(model.getCountdownLine());
        if (!countdown.isEmpty())
        {
            panel.getChildren().add(
                LineComponent.builder()
                    .left("Time")
                    .right(countdown)
                    .build()
            );
        }

        // ---- Tile ----
        String tileLine = safe(model.getTileLine());
        if (!tileLine.isEmpty())
        {
            panel.getChildren().add(
                LineComponent.builder()
                    .left("Tile")
                    .right(tileLine)
                    .build()
            );
        }

        Dimension dim = panel.render(graphics);

        // ---- Optional tile image preview ----
        BufferedImage img = model.getTileImage();
        if (img != null)
        {
            int x = 4;
            int y = dim.height + 6;

            graphics.drawImage(
                img,
                x,
                y,
                IMAGE_SIZE,
                IMAGE_SIZE,
                null
            );

            return new Dimension(
                Math.max(dim.width, IMAGE_SIZE + 8),
                y + IMAGE_SIZE
            );
        }

        return dim;
    }

    private static String safe(String s)
    {
        return s == null ? "" : s;
    }
}

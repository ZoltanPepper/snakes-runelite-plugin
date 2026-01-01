package com.snakesladders;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SnakesLaddersPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SnakesLaddersPlugin.class);
		RuneLite.main(args);
	}
}

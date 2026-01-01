package com.snakesladders;

import java.util.Map;

public class GameStateDto
{
	public int tile;
	public boolean awaitingProof;

	// teamName -> tile number
	public Map<String, Integer> standings;
}

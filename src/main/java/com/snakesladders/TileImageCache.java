package com.snakesladders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TileImageCache
{
	private static final HttpClient HTTP = HttpClient.newBuilder().build();

	private final ExecutorService pool = Executors.newFixedThreadPool(2);

	private final Map<String, BufferedImage> lru = new LinkedHashMap<>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest)
		{
			return size() > 64;
		}
	};

	public synchronized BufferedImage getIfPresent(String key)
	{
		return lru.get(key);
	}

	private synchronized void put(String key, BufferedImage img)
	{
		if (key == null || img == null) return;
		lru.put(key, img);
	}

	public void fetchAsync(String cacheKey, String imageUrl, ImageCallback cb)
	{
		if (cacheKey == null || cacheKey.trim().isEmpty()) return;
		if (imageUrl == null || imageUrl.trim().isEmpty()) return;

		BufferedImage cached = getIfPresent(cacheKey);
		if (cached != null)
		{
			cb.onImage(cached);
			return;
		}

		pool.submit(() ->
		{
			try
			{
				HttpRequest req = HttpRequest.newBuilder()
					.uri(URI.create(imageUrl.trim()))
					.GET()
					.header("accept", "image/*")
					.build();

				HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
				if (res.statusCode() >= 400) return;

				BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
				if (img == null) return;

				put(cacheKey, img);
				cb.onImage(img);
			}
			catch (Exception ignored)
			{
				// ignore
			}
		});
	}

	public void shutdown()
	{
		pool.shutdownNow();
	}

	public interface ImageCallback
	{
		void onImage(BufferedImage image);
	}
}

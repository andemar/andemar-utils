import { searchTorrents } from "@/lib/nyaa";
import { NextRequest } from "next/server";

/**
 * Nyaa.si Torrent Search API Route
 *
 * GET /api/nyaa?q=AnimeTitle&ep=12
 *
 * Returns top 3 torrent results as JSON.
 * Strategy: Nyaa HTML scraping (with magnet links) → AnimeTosho fallback
 */
export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams;
  const query = searchParams.get("q");
  const episode = searchParams.get("ep");

  if (!query || !episode) {
    return Response.json(
      { error: "Missing required parameters: q (anime title) and ep (episode number)" },
      { status: 400 }
    );
  }

  const episodeNumber = parseInt(episode, 10);
  if (isNaN(episodeNumber)) {
    return Response.json(
      { error: "Invalid episode number" },
      { status: 400 }
    );
  }

  try {
    const results = await searchTorrents(query, episodeNumber);

    return Response.json({
      results,
      query: `${query} ${String(episodeNumber).padStart(2, "0")}`,
      count: results.length,
    });
  } catch (error) {
    console.error("Torrent search error:", error);

    return Response.json(
      {
        error: "Failed to search for torrents",
        results: [],
        query: `${query} ${String(episodeNumber).padStart(2, "0")}`,
        count: 0,
        fallbackUrl: `https://nyaa.si/?f=0&c=1_2&q=${encodeURIComponent(query + " " + String(episodeNumber).padStart(2, "0"))}&s=seeders&o=desc`,
      },
      { status: 500 }
    );
  }
}

import * as cheerio from "cheerio";
import type { TorrentResult } from "@/types";

/**
 * Search Nyaa.si via RSS feed (primary strategy).
 * More stable than HTML scraping, provides structured data.
 */
export async function searchNyaaRSS(query: string): Promise<TorrentResult[]> {
  const url = `https://nyaa.si/?page=rss&f=0&c=1_2&q=${encodeURIComponent(query)}`;

  const response = await fetch(url, {
    headers: { "User-Agent": "AniTracker/1.0" },
  });

  if (!response.ok) {
    throw new Error(`Nyaa RSS error: ${response.status}`);
  }

  const xml = await response.text();
  const $ = cheerio.load(xml, { xml: true });
  const results: TorrentResult[] = [];

  $("item").each((_, item) => {
    const name = $(item).find("title").text().trim();
    const link = $(item).find("link").text().trim();
    const seeders = parseInt($(item).find("nyaa\\:seeders, seeders").text().trim() || "0", 10);
    const size = $(item).find("nyaa\\:size, size").text().trim() || "Unknown";
    const pubDate = $(item).find("pubDate").text().trim();

    // Extract magnet link from the guid or description
    const guid = $(item).find("guid").text().trim();
    // Nyaa RSS items have a link to the torrent page, we need to get the magnet from HTML
    // For RSS, we'll use the torrent page link and construct a magnet later
    // Actually, nyaa RSS provides direct download link, not magnet. We need to scrape for magnet.

    if (name && link) {
      results.push({
        name,
        magnetLink: link, // This is the torrent page URL - we'll get magnet from HTML scraping
        seeders,
        size,
        uploadDate: pubDate ? new Date(pubDate).toLocaleDateString() : "Unknown",
        source: "nyaa",
      });
    }
  });

  // Sort by seeders descending
  results.sort((a, b) => b.seeders - a.seeders);
  return results.slice(0, 10); // Return top 10 for further processing
}

/**
 * Scrape Nyaa.si HTML page (fallback / enrichment strategy).
 * Directly extracts magnet links and detailed torrent info.
 */
export async function scrapeNyaaHTML(query: string): Promise<TorrentResult[]> {
  const url = `https://nyaa.si/?f=0&c=1_2&q=${encodeURIComponent(query)}&s=seeders&o=desc`;

  const response = await fetch(url, {
    headers: { "User-Agent": "AniTracker/1.0" },
  });

  if (!response.ok) {
    throw new Error(`Nyaa scrape error: ${response.status}`);
  }

  const html = await response.text();
  const $ = cheerio.load(html);
  const results: TorrentResult[] = [];

  $("tbody tr").each((_, row) => {
    const nameEl = $(row).find("td:nth-child(2) a").last();
    const name = nameEl.text().trim();
    const magnetEl = $(row).find('a[href^="magnet"]');
    const magnetLink = magnetEl.attr("href") || "";
    const seeders = parseInt($(row).find("td:nth-child(6)").text().trim() || "0", 10);
    const size = $(row).find("td:nth-child(4)").text().trim();
    const uploadDate = $(row).find("td:nth-child(5)").text().trim();

    if (name && magnetLink) {
      results.push({
        name,
        magnetLink,
        seeders,
        size,
        uploadDate,
        source: "nyaa",
      });
    }
  });

  return results.slice(0, 5); // Already sorted by seeders (URL param)
}

/**
 * Search AnimeTosho as fallback when Nyaa returns no results.
 */
export async function searchAnimeTosho(query: string): Promise<TorrentResult[]> {
  const url = `https://animetosho.org/search?q=${encodeURIComponent(query)}`;

  try {
    const response = await fetch(url, {
      headers: { "User-Agent": "AniTracker/1.0" },
    });

    if (!response.ok) {
      return [];
    }

    const html = await response.text();
    const $ = cheerio.load(html);
    const results: TorrentResult[] = [];

    $(".home_list_entry").each((_, entry) => {
      const nameEl = $(entry).find(".link a");
      const name = nameEl.text().trim();
      const magnetEl = $(entry).find('a[href^="magnet"]');
      const magnetLink = magnetEl.attr("href") || "";
      const sizeEl = $(entry).find(".size");
      const size = sizeEl.text().trim() || "Unknown";

      if (name && magnetLink) {
        results.push({
          name,
          magnetLink,
          seeders: 0, // AnimeTosho doesn't always show seeders in search results
          size,
          uploadDate: "Unknown",
          source: "animetosho",
        });
      }
    });

    return results.slice(0, 5);
  } catch {
    return [];
  }
}

/**
 * Combined search: tries Nyaa HTML first (for magnet links),
 * falls back to AnimeTosho if no results.
 */
export async function searchTorrents(
  animeTitle: string,
  episodeNumber: number
): Promise<TorrentResult[]> {
  const query = `${animeTitle} ${String(episodeNumber).padStart(2, "0")}`;

  // Try Nyaa HTML scraping first (direct magnet links)
  try {
    const nyaaResults = await scrapeNyaaHTML(query);
    if (nyaaResults.length > 0) {
      return nyaaResults.slice(0, 3);
    }
  } catch {
    // Nyaa might be down, continue to fallback
  }

  // Fallback: try AnimeTosho
  try {
    const toshoResults = await searchAnimeTosho(query);
    if (toshoResults.length > 0) {
      return toshoResults.slice(0, 3);
    }
  } catch {
    // AnimeTosho also failed
  }

  return [];
}

import type {
  Anime,
  AniListPageResponse,
  AniListMediaResponse,
  MediaSeason,
} from "@/types";

const ANILIST_ENDPOINT = "https://graphql.anilist.co";

/**
 * Season overview query — fetches all anime for a given season/year.
 */
const SEASON_QUERY = `
  query ($season: MediaSeason, $year: Int, $page: Int) {
    Page(page: $page, perPage: 50) {
      pageInfo {
        total
        currentPage
        lastPage
        hasNextPage
      }
      media(season: $season, seasonYear: $year, type: ANIME, sort: POPULARITY_DESC) {
        id
        title { romaji english }
        coverImage { large }
        episodes
        nextAiringEpisode { episode airingAt }
        airingSchedule { nodes { episode airingAt } }
        status
        genres
        description(asHtml: false)
        averageScore
        studios { nodes { name } }
      }
    }
  }
`;

/**
 * Anime detail query — fetches full info for a single anime by ID.
 */
const ANIME_DETAIL_QUERY = `
  query ($id: Int) {
    Media(id: $id, type: ANIME) {
      id
      title { romaji english }
      coverImage { large }
      episodes
      nextAiringEpisode { episode airingAt }
      airingSchedule {
        nodes { episode airingAt }
      }
      status
      genres
      description(asHtml: false)
      averageScore
      studios { nodes { name } }
    }
  }
`;

/**
 * Fetch anime for a given season. Cached for 1 hour via Next.js revalidation.
 */
export async function fetchSeasonAnime(
  season: MediaSeason,
  year: number,
  page: number = 1
): Promise<{ anime: Anime[]; hasNextPage: boolean; totalPages: number }> {
  const response = await fetch(ANILIST_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      query: SEASON_QUERY,
      variables: { season, year, page },
    }),
    next: { revalidate: 3600 }, // Cache for 1 hour
  });

  if (!response.ok) {
    throw new Error(`AniList API error: ${response.status} ${response.statusText}`);
  }

  const json = (await response.json()) as AniListPageResponse;

  return {
    anime: json.data.Page.media,
    hasNextPage: json.data.Page.pageInfo.hasNextPage,
    totalPages: json.data.Page.pageInfo.lastPage,
  };
}

/**
 * Fetch full details for a single anime by ID. Cached for 1 hour.
 */
export async function fetchAnimeDetails(id: number): Promise<Anime> {
  const response = await fetch(ANILIST_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      query: ANIME_DETAIL_QUERY,
      variables: { id },
    }),
    next: { revalidate: 3600 },
  });

  if (!response.ok) {
    throw new Error(`AniList API error: ${response.status} ${response.statusText}`);
  }

  const json = (await response.json()) as AniListMediaResponse;
  return json.data.Media;
}

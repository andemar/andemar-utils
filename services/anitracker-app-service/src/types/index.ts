// AniTracker TypeScript Interfaces
// All shared type definitions for the application

export interface Anime {
  id: number;
  title: { romaji: string; english: string | null };
  coverImage: { large: string };
  episodes: number | null;
  nextAiringEpisode: { episode: number; airingAt: number } | null;
  airingSchedule: { nodes: AiringNode[] };
  status: string;
  genres: string[];
  description: string | null;
  averageScore: number | null;
  studios: { nodes: { name: string }[] };
}

export interface AiringNode {
  episode: number;
  airingAt: number; // Unix timestamp
}

export interface Episode {
  number: number;
  airDate: Date | null;
  status: "aired" | "not_yet" | "downloaded";
}

export interface TorrentResult {
  name: string;
  magnetLink: string;
  seeders: number;
  size: string;
  uploadDate: string;
  source: "nyaa" | "animetosho";
}

export interface UserPreferences {
  favorites: number[]; // Array of AniList anime IDs
  downloaded: Record<number, number[]>; // { animeId: [ep1, ep2, ...] }
}

// AniList API response types
export interface AniListPageResponse {
  data: {
    Page: {
      pageInfo: {
        total: number;
        currentPage: number;
        lastPage: number;
        hasNextPage: boolean;
      };
      media: Anime[];
    };
  };
}

export interface AniListMediaResponse {
  data: {
    Media: Anime;
  };
}

export type MediaSeason = "WINTER" | "SPRING" | "SUMMER" | "FALL";

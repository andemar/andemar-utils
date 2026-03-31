import { fetchSeasonAnime } from "@/lib/anilist";
import {
  getCurrentSeason,
  getCurrentYear,
  getSeasonDisplayName,
  getSeasonEmoji,
} from "@/lib/season";
import { AnimeGrid } from "@/components/AnimeGrid";

export default async function HomePage() {
  const season = getCurrentSeason();
  const year = getCurrentYear();
  const seasonName = getSeasonDisplayName(season);
  const emoji = getSeasonEmoji(season);

  const { anime } = await fetchSeasonAnime(season, year);

  return (
    <div className="container mx-auto px-4 py-8">
      {/* Header Section */}
      <header className="mb-10 animate-fade-in-up">
        <div className="flex items-center gap-3 mb-2">
          <span className="text-4xl">{emoji}</span>
          <h1 className="text-4xl md:text-5xl font-bold text-gradient">
            {seasonName} {year}
          </h1>
        </div>
        <p className="text-muted-foreground text-lg ml-1">
          {anime.length} anime airing this season • Sorted by popularity
        </p>
      </header>

      {/* Anime Grid with Client-side Filtering */}
      <AnimeGrid animeList={anime} />
    </div>
  );
}

"use client";

import { useState } from "react";
import type { Anime } from "@/types";
import { usePreferences } from "@/store/usePreferences";
import { AnimeCard } from "@/components/AnimeCard";
import { SeasonFilterButtons } from "@/components/SeasonFilter";

interface AnimeGridProps {
  animeList: Anime[];
}

export function AnimeGrid({ animeList }: AnimeGridProps) {
  const [filter, setFilter] = useState<"all" | "favorites">("all");
  const { favorites } = usePreferences();

  const filteredAnime =
    filter === "favorites"
      ? animeList.filter((a) => favorites.includes(a.id))
      : animeList;

  const favoritesCount = animeList.filter((a) => favorites.includes(a.id)).length;

  return (
    <div>
      {/* Filter Buttons */}
      <SeasonFilterButtons
        totalCount={animeList.length}
        favoritesCount={favoritesCount}
        activeFilter={filter}
        onFilterChange={setFilter}
      />

      {/* Grid */}
      {filteredAnime.length === 0 ? (
        <div className="text-center py-20">
          <div className="text-6xl mb-4">
            {filter === "favorites" ? "💔" : "🔍"}
          </div>
          <h3 className="text-xl font-semibold mb-2">
            {filter === "favorites"
              ? "No favorites yet"
              : "No anime found"}
          </h3>
          <p className="text-muted-foreground">
            {filter === "favorites"
              ? "Click the ❤️ on any anime card to add it to your favorites!"
              : "There are no anime for this season."}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 md:gap-6">
          {filteredAnime.map((anime, index) => (
            <div
              key={anime.id}
              style={{ animationDelay: `${Math.min(index * 50, 500)}ms` }}
            >
              <AnimeCard anime={anime} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

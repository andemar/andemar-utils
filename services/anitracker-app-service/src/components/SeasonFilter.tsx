"use client";

import { useState } from "react";
import { usePreferences } from "@/store/usePreferences";

interface SeasonFilterProps {
  totalCount: number;
  favoritesCount: number;
}

export function SeasonFilter({ totalCount, favoritesCount }: SeasonFilterProps) {
  const [filter, setFilter] = useState<"all" | "favorites">("all");

  return {
    filter,
    FilterUI: (
      <div className="flex items-center gap-2 mb-6">
        <button
          id="filter-all"
          onClick={() => setFilter("all")}
          className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200 ${
            filter === "all"
              ? "bg-primary text-primary-foreground shadow-lg shadow-primary/25"
              : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
          }`}
        >
          All ({totalCount})
        </button>
        <button
          id="filter-favorites"
          onClick={() => setFilter("favorites")}
          className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200 ${
            filter === "favorites"
              ? "bg-primary text-primary-foreground shadow-lg shadow-primary/25"
              : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
          }`}
        >
          ❤️ Favorites ({favoritesCount})
        </button>
      </div>
    ),
  };
}

// Simpler version that uses a callback
export function SeasonFilterButtons({
  totalCount,
  favoritesCount,
  activeFilter,
  onFilterChange,
}: {
  totalCount: number;
  favoritesCount: number;
  activeFilter: "all" | "favorites";
  onFilterChange: (filter: "all" | "favorites") => void;
}) {
  return (
    <div className="flex items-center gap-2 mb-6">
      <button
        id="filter-all-btn"
        onClick={() => onFilterChange("all")}
        className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200 cursor-pointer ${
          activeFilter === "all"
            ? "bg-primary text-primary-foreground shadow-lg shadow-primary/25"
            : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
        }`}
      >
        All ({totalCount})
      </button>
      <button
        id="filter-favorites-btn"
        onClick={() => onFilterChange("favorites")}
        className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200 cursor-pointer ${
          activeFilter === "favorites"
            ? "bg-primary text-primary-foreground shadow-lg shadow-primary/25"
            : "bg-secondary text-secondary-foreground hover:bg-secondary/80"
        }`}
      >
        ❤️ Favorites ({favoritesCount})
      </button>
    </div>
  );
}

"use client";

import type { Anime } from "@/types";
import { usePreferences } from "@/store/usePreferences";
import Image from "next/image";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { CountdownBadge } from "@/components/CountdownBadge";
import { truncateText } from "@/lib/utils";

interface AnimeCardProps {
  anime: Anime;
}

export function AnimeCard({ anime }: AnimeCardProps) {
  const { toggleFavorite, isFavorite } = usePreferences();
  const title = anime.title.english || anime.title.romaji;
  const isFav = isFavorite(anime.id);

  return (
    <div className="group relative animate-fade-in-up">
      <Link
        href={`/anime/${anime.id}`}
        id={`anime-card-${anime.id}`}
        className="block"
      >
        <div className="relative glass rounded-xl overflow-hidden anime-card-hover">
          {/* Cover Image */}
          <div className="aspect-[3/4] relative overflow-hidden">
            <Image
              src={anime.coverImage.large}
              alt={title}
              fill
              className="object-cover transition-transform duration-500 group-hover:scale-110"
              sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
            />
            {/* Gradient overlay */}
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />

            {/* Countdown badge */}
            {anime.nextAiringEpisode && (
              <div className="absolute top-2 left-2">
                <CountdownBadge airingAt={anime.nextAiringEpisode.airingAt} />
              </div>
            )}

            {/* Score badge */}
            {anime.averageScore && (
              <div className="absolute top-2 right-2">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-bold bg-anime-gold/90 text-black">
                  ★ {anime.averageScore}%
                </span>
              </div>
            )}

            {/* Bottom info overlay */}
            <div className="absolute bottom-0 left-0 right-0 p-3">
              <h3 className="text-sm font-bold text-white leading-tight mb-1 line-clamp-2">
                {title}
              </h3>
              {title !== anime.title.romaji && (
                <p className="text-xs text-white/60 line-clamp-1">
                  {anime.title.romaji}
                </p>
              )}
            </div>
          </div>

          {/* Card footer */}
          <div className="p-3 pt-2 space-y-2">
            {/* Genres */}
            <div className="flex flex-wrap gap-1">
              {anime.genres.slice(0, 3).map((genre) => (
                <span
                  key={genre}
                  className="px-1.5 py-0.5 text-[10px] rounded-md bg-accent/15 text-muted-foreground border border-accent/20"
                >
                  {genre}
                </span>
              ))}
            </div>

            {/* Episode info */}
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                {anime.nextAiringEpisode
                  ? `Ep ${anime.nextAiringEpisode.episode - 1}/${anime.episodes || "?"}`
                  : anime.episodes
                  ? `${anime.episodes} eps`
                  : "Ongoing"}
              </span>
              <Badge
                variant="outline"
                className={`text-[10px] h-4 ${
                  anime.status === "RELEASING"
                    ? "border-anime-green/50 text-anime-green"
                    : "border-border"
                }`}
              >
                {anime.status === "RELEASING" ? "Airing" : anime.status === "FINISHED" ? "Done" : anime.status}
              </Badge>
            </div>
          </div>
        </div>
      </Link>

      {/* Favorite button (positioned absolute, on top of the card link) */}
      <button
        id={`fav-btn-${anime.id}`}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          toggleFavorite(anime.id);
        }}
        className={`absolute top-2 right-2 z-10 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-200 cursor-pointer ${
          isFav
            ? "bg-primary/90 text-primary-foreground scale-110 shadow-lg shadow-primary/40"
            : "bg-black/40 text-white/70 hover:bg-primary/60 hover:text-white hover:scale-110 opacity-0 group-hover:opacity-100"
        }`}
        aria-label={isFav ? "Remove from favorites" : "Add to favorites"}
      >
        <svg className="w-4 h-4" fill={isFav ? "currentColor" : "none"} stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
          />
        </svg>
      </button>
    </div>
  );
}

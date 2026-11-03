import type { MediaSeason } from "@/types";

/**
 * Get the current anime season based on the month.
 * Month 1–3  → WINTER
 * Month 4–6  → SPRING
 * Month 7–9  → SUMMER
 * Month 10–12 → FALL
 */
export function getCurrentSeason(): MediaSeason {
  const month = new Date().getMonth() + 1; // getMonth() is 0-indexed
  if (month >= 1 && month <= 3) return "WINTER";
  if (month >= 4 && month <= 6) return "SPRING";
  if (month >= 7 && month <= 9) return "SUMMER";
  return "FALL";
}

/**
 * Get the current year.
 */
export function getCurrentYear(): number {
  return new Date().getFullYear();
}

/**
 * Get a display-friendly season name.
 */
export function getSeasonDisplayName(season: MediaSeason): string {
  const names: Record<MediaSeason, string> = {
    WINTER: "Winter",
    SPRING: "Spring",
    SUMMER: "Summer",
    FALL: "Fall",
  };
  return names[season];
}

/**
 * Get the emoji for each season.
 */
export function getSeasonEmoji(season: MediaSeason): string {
  const emojis: Record<MediaSeason, string> = {
    WINTER: "❄️",
    SPRING: "🌸",
    SUMMER: "☀️",
    FALL: "🍂",
  };
  return emojis[season];
}

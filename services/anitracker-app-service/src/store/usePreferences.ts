"use client";

import { create } from "zustand";
import Cookies from "js-cookie";
import type { UserPreferences } from "@/types";

const COOKIE_KEY = "anitracker_prefs";

/**
 * Load preferences from cookie on initialization.
 */
function loadFromCookie(): UserPreferences {
  try {
    const raw = Cookies.get(COOKIE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as UserPreferences;
      return {
        favorites: parsed.favorites || [],
        downloaded: parsed.downloaded || {},
      };
    }
  } catch {
    // Cookie parsing failed, return defaults
  }
  return { favorites: [], downloaded: {} };
}

/**
 * Save preferences to cookie.
 * Stores only IDs and episode numbers to stay under 4KB limit.
 */
function saveToCookie(prefs: UserPreferences): void {
  try {
    const json = JSON.stringify(prefs);
    Cookies.set(COOKIE_KEY, json, { expires: 365, sameSite: "lax" });
  } catch {
    // Cookie save failed — storage limit likely exceeded
    console.warn("Failed to save preferences to cookie");
  }
}

interface PreferencesStore extends UserPreferences {
  toggleFavorite: (animeId: number) => void;
  toggleDownloaded: (animeId: number, episodeNumber: number) => void;
  isFavorite: (animeId: number) => boolean;
  isDownloaded: (animeId: number, episodeNumber: number) => boolean;
}

export const usePreferences = create<PreferencesStore>((set, get) => {
  const initial = loadFromCookie();

  return {
    ...initial,

    toggleFavorite: (animeId: number) => {
      set((state) => {
        const isFav = state.favorites.includes(animeId);
        const newFavorites = isFav
          ? state.favorites.filter((id) => id !== animeId)
          : [...state.favorites, animeId];

        const newState = { ...state, favorites: newFavorites };
        saveToCookie({ favorites: newState.favorites, downloaded: newState.downloaded });
        return newState;
      });
    },

    toggleDownloaded: (animeId: number, episodeNumber: number) => {
      set((state) => {
        const episodes = state.downloaded[animeId] || [];
        const isDownloaded = episodes.includes(episodeNumber);
        const newEpisodes = isDownloaded
          ? episodes.filter((ep) => ep !== episodeNumber)
          : [...episodes, episodeNumber];

        const newDownloaded = { ...state.downloaded };
        if (newEpisodes.length === 0) {
          delete newDownloaded[animeId];
        } else {
          newDownloaded[animeId] = newEpisodes;
        }

        const newState = { ...state, downloaded: newDownloaded };
        saveToCookie({ favorites: newState.favorites, downloaded: newState.downloaded });
        return newState;
      });
    },

    isFavorite: (animeId: number) => {
      return get().favorites.includes(animeId);
    },

    isDownloaded: (animeId: number, episodeNumber: number) => {
      const episodes = get().downloaded[animeId];
      return episodes ? episodes.includes(episodeNumber) : false;
    },
  };
});

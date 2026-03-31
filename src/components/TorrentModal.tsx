"use client";

import { useState, useEffect } from "react";
import type { TorrentResult } from "@/types";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

interface TorrentModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  animeTitle: string;
  episodeNumber: number;
}

export function TorrentModal({
  open,
  onOpenChange,
  animeTitle,
  episodeNumber,
}: TorrentModalProps) {
  const [results, setResults] = useState<TorrentResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  const searchQuery = `${animeTitle} ${String(episodeNumber).padStart(2, "0")}`;
  const nyaaSearchUrl = `https://nyaa.si/?f=0&c=1_2&q=${encodeURIComponent(searchQuery)}&s=seeders&o=desc`;

  // Fetch results when modal opens
  useEffect(() => {
    if (open) {
      fetchTorrents();
    } else {
      // Reset state on close
      setResults([]);
      setError(null);
      setCopiedIndex(null);
    }
  }, [open, animeTitle, episodeNumber]);

  const fetchTorrents = async () => {
    setLoading(true);
    setError(null);
    setResults([]);

    try {
      const res = await fetch(
        `/api/nyaa?q=${encodeURIComponent(animeTitle)}&ep=${episodeNumber}`
      );
      const data = await res.json();

      if (data.results && data.results.length > 0) {
        setResults(data.results);
      } else {
        setError("No torrents found for this episode.");
      }
    } catch {
      setError("Failed to search for torrents. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const copyMagnet = async (magnetLink: string, index: number) => {
    try {
      await navigator.clipboard.writeText(magnetLink);
      setCopiedIndex(index);
      setTimeout(() => setCopiedIndex(null), 2000);
    } catch {
      // Fallback: open magnet link directly
      window.open(magnetLink, "_blank");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            🧲 Torrents — Episode {episodeNumber}
          </DialogTitle>
          <DialogDescription>
            Searching for: {searchQuery}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 mt-2">
          {/* Loading State */}
          {loading && (
            <div className="space-y-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="p-3 rounded-lg bg-secondary/50 space-y-2">
                  <Skeleton className="h-4 w-3/4" />
                  <div className="flex gap-2">
                    <Skeleton className="h-4 w-16" />
                    <Skeleton className="h-4 w-16" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Results */}
          {!loading && results.length > 0 && (
            <div className="space-y-2">
              {results.map((result, index) => (
                <div
                  key={index}
                  className="p-3 rounded-lg bg-secondary/30 border border-border/50 hover:bg-secondary/50 transition-colors"
                >
                  <p className="text-sm font-medium mb-2 leading-tight line-clamp-2">
                    {result.name}
                  </p>
                  <div className="flex items-center gap-2 flex-wrap mb-2">
                    <Badge variant="outline" className="text-anime-green border-anime-green/30">
                      ↑ {result.seeders} seeders
                    </Badge>
                    <Badge variant="secondary" className="text-xs">
                      {result.size}
                    </Badge>
                    {result.source === "animetosho" && (
                      <Badge variant="outline" className="text-xs text-neon-cyan border-neon-cyan/30">
                        AnimeTosho
                      </Badge>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="default"
                      onClick={() => copyMagnet(result.magnetLink, index)}
                      className="text-xs"
                    >
                      {copiedIndex === index ? "✅ Copied!" : "📋 Copy Magnet"}
                    </Button>
                    {result.magnetLink.startsWith("magnet:") && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => window.open(result.magnetLink, "_self")}
                        className="text-xs"
                      >
                        🧲 Open
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Error / No Results */}
          {!loading && error && (
            <div className="text-center py-6">
              <div className="text-4xl mb-3">😔</div>
              <p className="text-sm text-muted-foreground mb-4">{error}</p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => window.open(nyaaSearchUrl, "_blank")}
              >
                🔍 Search Nyaa Manually →
              </Button>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

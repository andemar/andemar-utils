"use client";

import { useState } from "react";
import type { Episode } from "@/types";
import { usePreferences } from "@/store/usePreferences";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { TorrentModal } from "@/components/TorrentModal";

interface EpisodeListProps {
  episodes: Episode[];
  animeId: number;
  animeTitle: string;
}

export function EpisodeList({ episodes, animeId, animeTitle }: EpisodeListProps) {
  const { toggleDownloaded, isDownloaded } = usePreferences();
  const [torrentModal, setTorrentModal] = useState<{
    open: boolean;
    episodeNumber: number;
  }>({ open: false, episodeNumber: 0 });

  if (episodes.length === 0) {
    return (
      <div className="text-center py-12 glass rounded-xl">
        <div className="text-5xl mb-3">📭</div>
        <p className="text-muted-foreground">No episodes have aired yet.</p>
      </div>
    );
  }

  // Sort episodes by number (ascending)
  const sortedEpisodes = [...episodes].sort((a, b) => a.number - b.number);

  return (
    <>
      <div className="space-y-2">
        {sortedEpisodes.map((episode) => {
          const downloaded = isDownloaded(animeId, episode.number);
          const isAired = episode.status === "aired";

          return (
            <div
              key={episode.number}
              className={`flex items-center gap-3 p-3 rounded-lg transition-all duration-200 ${
                isAired
                  ? "glass glass-hover"
                  : "bg-muted/30 opacity-60"
              }`}
            >
              {/* Episode Number */}
              <div className="flex-shrink-0 w-12 h-12 rounded-lg bg-secondary flex items-center justify-center font-bold text-lg">
                {episode.number}
              </div>

              {/* Episode Info */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">
                    Episode {episode.number}
                  </span>

                  {/* Status Badge */}
                  {downloaded ? (
                    <Badge variant="default" className="bg-neon-blue/20 text-neon-blue border-neon-blue/30 text-xs">
                      ✓ Downloaded
                    </Badge>
                  ) : isAired ? (
                    <Badge variant="outline" className="border-anime-green/30 text-anime-green text-xs">
                      Aired
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="text-xs">
                      Not Yet
                    </Badge>
                  )}
                </div>

                {/* Air Date */}
                {episode.airDate && (
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {episode.airDate.toLocaleDateString("en-US", {
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                  </p>
                )}
              </div>

              {/* Actions */}
              <div className="flex items-center gap-2 flex-shrink-0">
                {/* Download toggle */}
                {isAired && (
                  <Button
                    id={`download-toggle-${animeId}-${episode.number}`}
                    variant={downloaded ? "secondary" : "ghost"}
                    size="icon-sm"
                    onClick={() => toggleDownloaded(animeId, episode.number)}
                    title={downloaded ? "Mark as not downloaded" : "Mark as downloaded"}
                  >
                    {downloaded ? "✅" : "⬇️"}
                  </Button>
                )}

                {/* Get Torrent button */}
                <Button
                  id={`get-torrent-${animeId}-${episode.number}`}
                  variant="outline"
                  size="sm"
                  disabled={!isAired}
                  onClick={() =>
                    setTorrentModal({ open: true, episodeNumber: episode.number })
                  }
                  className={isAired ? "border-primary/30 hover:border-primary hover:bg-primary/10 text-primary" : ""}
                >
                  🧲 Get Torrent
                </Button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Torrent Search Modal */}
      <TorrentModal
        open={torrentModal.open}
        onOpenChange={(open) => setTorrentModal({ ...torrentModal, open })}
        animeTitle={animeTitle}
        episodeNumber={torrentModal.episodeNumber}
      />
    </>
  );
}

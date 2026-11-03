"use client";

import { Button } from "@/components/ui/button";

export default function AnimeDetailError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="container mx-auto px-4 py-20 text-center">
      <div className="max-w-md mx-auto glass rounded-2xl p-8">
        <div className="text-6xl mb-4">🔍</div>
        <h2 className="text-2xl font-bold mb-2">Anime Not Found</h2>
        <p className="text-muted-foreground mb-6">
          {error.message || "Could not load this anime's details. It may not exist or AniList is temporarily unavailable."}
        </p>
        <div className="flex gap-3 justify-center">
          <Button onClick={reset} variant="default">
            Try Again
          </Button>
          <Button variant="outline" onClick={() => window.history.back()}>
            Go Back
          </Button>
        </div>
      </div>
    </div>
  );
}

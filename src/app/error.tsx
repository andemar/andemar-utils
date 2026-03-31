"use client";

import { Button } from "@/components/ui/button";

export default function HomeError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="container mx-auto px-4 py-20 text-center">
      <div className="max-w-md mx-auto glass rounded-2xl p-8">
        <div className="text-6xl mb-4">😵</div>
        <h2 className="text-2xl font-bold mb-2">Something went wrong</h2>
        <p className="text-muted-foreground mb-6">
          {error.message || "Failed to load anime data. AniList might be temporarily unavailable."}
        </p>
        <Button onClick={reset} variant="default" size="lg">
          Try Again
        </Button>
      </div>
    </div>
  );
}

"use client";

import { useState, useEffect } from "react";
import { formatCountdown } from "@/lib/utils";

interface CountdownBadgeProps {
  airingAt: number; // Unix timestamp
}

export function CountdownBadge({ airingAt }: CountdownBadgeProps) {
  const [countdown, setCountdown] = useState<string>(() => formatCountdown(airingAt));

  useEffect(() => {
    // Update immediately
    setCountdown(formatCountdown(airingAt));

    // Update every 60 seconds
    const interval = setInterval(() => {
      setCountdown(formatCountdown(airingAt));
    }, 60_000);

    return () => clearInterval(interval);
  }, [airingAt]);

  if (countdown === "Aired") return null;

  return (
    <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-neon-cyan/20 text-neon-cyan border border-neon-cyan/30 backdrop-blur-sm">
      <span className="relative flex h-2 w-2">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-neon-cyan opacity-75" />
        <span className="relative inline-flex rounded-full h-2 w-2 bg-neon-cyan" />
      </span>
      Ep {countdown.includes("d") ? "" : "in "}{countdown}
    </div>
  );
}

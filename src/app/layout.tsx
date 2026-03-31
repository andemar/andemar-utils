import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  variable: "--font-sans",
  subsets: ["latin"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "AniTracker — Anime Season Tracker",
  description:
    "Track the current anime season, browse aired episodes, and find torrent links. Your personal anime companion.",
  keywords: ["anime", "tracker", "season", "episodes", "torrent", "nyaa"],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col bg-background text-foreground">
        {/* Ambient background glow effects */}
        <div className="fixed inset-0 -z-10 overflow-hidden pointer-events-none">
          <div className="absolute -top-1/2 -left-1/4 w-[600px] h-[600px] rounded-full bg-neon-pink/10 animate-glow-pulse blur-3xl" />
          <div className="absolute -bottom-1/4 -right-1/4 w-[500px] h-[500px] rounded-full bg-neon-purple/10 animate-glow-pulse blur-3xl" style={{ animationDelay: "1.5s" }} />
          <div className="absolute top-1/3 right-1/4 w-[400px] h-[400px] rounded-full bg-neon-blue/5 animate-glow-pulse blur-3xl" style={{ animationDelay: "3s" }} />
        </div>

        {/* Main content */}
        <main className="flex-1">{children}</main>

        {/* Footer */}
        <footer className="border-t border-border/50 py-6 mt-auto">
          <div className="container mx-auto px-4 text-center text-sm text-muted-foreground">
            <p>
              AniTracker — Data from{" "}
              <a
                href="https://anilist.co"
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary hover:underline"
              >
                AniList
              </a>{" "}
              • Torrents from{" "}
              <a
                href="https://nyaa.si"
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary hover:underline"
              >
                Nyaa.si
              </a>
            </p>
          </div>
        </footer>
      </body>
    </html>
  );
}

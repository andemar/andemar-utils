import { fetchAnimeDetails } from "@/lib/anilist";
import { EpisodeList } from "@/components/EpisodeList";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import Image from "next/image";
import Link from "next/link";
import type { Metadata } from "next";

type Props = {
  params: Promise<{ id: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const anime = await fetchAnimeDetails(parseInt(id, 10));
  const title = anime.title.english || anime.title.romaji;
  return {
    title: `${title} — AniTracker`,
    description: anime.description?.slice(0, 160) || `Track episodes for ${title}`,
  };
}

export default async function AnimeDetailPage({ params }: Props) {
  const { id } = await params;
  const anime = await fetchAnimeDetails(parseInt(id, 10));
  const title = anime.title.english || anime.title.romaji;
  const romajiTitle = anime.title.romaji;
  const studioName = anime.studios.nodes[0]?.name || "Unknown Studio";

  // Build episode list from airing schedule
  const now = Math.floor(Date.now() / 1000);
  const episodes = anime.airingSchedule.nodes.map((node) => ({
    number: node.episode,
    airDate: new Date(node.airingAt * 1000),
    status: node.airingAt < now ? ("aired" as const) : ("not_yet" as const),
  }));

  return (
    <div className="container mx-auto px-4 py-8">
      {/* Back button */}
      <Link
        href="/"
        className="inline-flex items-center gap-2 text-muted-foreground hover:text-foreground transition-colors mb-6 group"
      >
        <svg
          className="w-4 h-4 transition-transform group-hover:-translate-x-1"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
        Back to Season
      </Link>

      {/* Anime Info Section */}
      <div className="animate-fade-in-up">
        <div className="flex flex-col md:flex-row gap-8 mb-8">
          {/* Cover Image */}
          <div className="relative w-full md:w-64 flex-shrink-0">
            <div className="aspect-[3/4] relative rounded-2xl overflow-hidden glow-border">
              <Image
                src={anime.coverImage.large}
                alt={title}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 256px"
                priority
              />
            </div>
          </div>

          {/* Details */}
          <div className="flex-1 min-w-0">
            <h1 className="text-3xl md:text-4xl font-bold mb-1 text-gradient">
              {title}
            </h1>
            {title !== romajiTitle && (
              <p className="text-lg text-muted-foreground mb-4">{romajiTitle}</p>
            )}

            {/* Meta Info */}
            <div className="flex flex-wrap items-center gap-3 mb-4">
              {anime.averageScore && (
                <Badge variant="default" className="bg-anime-gold text-black font-semibold">
                  ★ {anime.averageScore}%
                </Badge>
              )}
              <Badge variant="secondary">{studioName}</Badge>
              <Badge variant="outline">
                {anime.episodes ? `${anime.episodes} Episodes` : "Ongoing"}
              </Badge>
              <Badge
                variant="outline"
                className={
                  anime.status === "RELEASING"
                    ? "border-anime-green text-anime-green"
                    : ""
                }
              >
                {anime.status === "RELEASING"
                  ? "🟢 Airing"
                  : anime.status === "FINISHED"
                  ? "✅ Finished"
                  : anime.status}
              </Badge>
            </div>

            {/* Genres */}
            <div className="flex flex-wrap gap-2 mb-6">
              {anime.genres.map((genre) => (
                <span
                  key={genre}
                  className="px-3 py-1 text-xs rounded-full bg-accent/20 text-accent-foreground border border-accent/30"
                >
                  {genre}
                </span>
              ))}
            </div>

            {/* Description */}
            {anime.description && (
              <div className="glass rounded-xl p-4">
                <h3 className="text-sm font-semibold text-muted-foreground mb-2 uppercase tracking-wider">
                  Synopsis
                </h3>
                <p className="text-sm leading-relaxed text-foreground/90">
                  {anime.description.replace(/<[^>]*>/g, "")}
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      <Separator className="my-8 bg-border/50" />

      {/* Episode List */}
      <section>
        <h2 className="text-2xl font-bold mb-6 flex items-center gap-2">
          <span>📺</span>
          Episodes
          <span className="text-base font-normal text-muted-foreground">
            ({episodes.length} aired)
          </span>
        </h2>
        <EpisodeList
          episodes={episodes}
          animeId={anime.id}
          animeTitle={romajiTitle}
        />
      </section>
    </div>
  );
}

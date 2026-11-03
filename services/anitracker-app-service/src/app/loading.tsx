import { Skeleton } from "@/components/ui/skeleton";

export default function HomeLoading() {
  return (
    <div className="container mx-auto px-4 py-8">
      {/* Header Skeleton */}
      <header className="mb-10">
        <Skeleton className="h-12 w-80 mb-3" />
        <Skeleton className="h-6 w-64" />
      </header>

      {/* Filter Skeleton */}
      <div className="flex gap-2 mb-8">
        <Skeleton className="h-8 w-16 rounded-full" />
        <Skeleton className="h-8 w-28 rounded-full" />
      </div>

      {/* Grid Skeleton */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 md:gap-6">
        {Array.from({ length: 20 }).map((_, i) => (
          <div key={i} className="space-y-3">
            <Skeleton className="aspect-[3/4] w-full rounded-xl" />
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/2" />
          </div>
        ))}
      </div>
    </div>
  );
}

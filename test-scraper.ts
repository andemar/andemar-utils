import { searchTorrents } from "./src/lib/nyaa";

async function run() {
  console.log("Testing searchTorrents...");
  const results = await searchTorrents("DAN DA DAN", 1);
  console.log("Results:", results);
}

run().catch(console.error);

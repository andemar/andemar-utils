This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

### Local Development (Node.js)

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

---

### Running with Docker

The service includes a development `Dockerfile` based on `node:20-slim` running as an unprivileged user (`node`, UID 1000).

#### 1. Build the Image

* **From the service directory (`services/anitracker-app-service`):**
  ```bash
  docker build -t anitracker-app-service:dev .
  ```

* **From the repository root (`andemar-utils`):**
  ```bash
  docker build -t anitracker-app-service:dev services/anitracker-app-service
  ```

#### 2. Run the Container

**Option A: Standalone Container**
```bash
docker run --rm -it -p 3000:3000 --name anitracker-app anitracker-app-service:dev
```

**Option B: Live Development with Host Mount (Hot Reloading)**

Bind-mount your local source directory to `/app` so file changes trigger hot reloading immediately. The anonymous volumes `-v /app/node_modules` and `-v /app/.next` prevent the host directory from overwriting the container's installed packages and build cache.

* **Windows PowerShell (from `services\anitracker-app-service`):**
  ```powershell
  docker run --rm -it `
    -p 3000:3000 `
    -v "${PWD}:/app" `
    -v /app/node_modules `
    -v /app/.next `
    anitracker-app-service:dev
  ```

* **Windows PowerShell (from repository root `andemar-utils`):**
  ```powershell
  docker run --rm -it `
    -p 3000:3000 `
    -v "${PWD}\services\anitracker-app-service:/app" `
    -v /app/node_modules `
    -v /app/.next `
    anitracker-app-service:dev
  ```

* **Linux / macOS (Bash / Zsh - from `services/anitracker-app-service`):**
  ```bash
  docker run --rm -it \
    -p 3000:3000 \
    -v "$PWD:/app" \
    -v /app/node_modules \
    -v /app/.next \
    anitracker-app-service:dev
  ```

Once running, navigate to [http://localhost:3000](http://localhost:3000).

---

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.

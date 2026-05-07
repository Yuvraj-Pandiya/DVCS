/**
 * LandingPage — public marketing page at route /
 *
 * Sections:
 * - Hero: headline, subheadline, CTA buttons (Get Started → /register, Explore → /explore)
 * - Feature highlights grid (Git Transport, PR Reviews, CI/CD, Real-time Notifications)
 * - Terminal snippet showing a git clone command
 * - Footer with copyright
 *
 * Theme: TailwindCSS dark (bg-gray-950, indigo accents)
 */

import { Link } from 'react-router-dom'

// ─── Feature data ─────────────────────────────────────────────────────────────

interface Feature {
  title: string
  description: string
  icon: React.ReactNode
}

const features: Feature[] = [
  {
    title: 'Git Transport',
    description:
      'Full HTTP Smart Git protocol — clone and push with any Git client, no special tooling required.',
    icon: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-6 h-6"
        aria-hidden="true"
      >
        <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
        <path d="M9 18c-4.51 2-5-2-7-2" />
      </svg>
    ),
  },
  {
    title: 'PR Reviews',
    description:
      'Code review workflows with inline comments, approvals, and merge strategies — merge commit, squash, or rebase.',
    icon: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-6 h-6"
        aria-hidden="true"
      >
        <circle cx="18" cy="18" r="3" />
        <circle cx="6" cy="6" r="3" />
        <path d="M13 6h3a2 2 0 0 1 2 2v7" />
        <path d="M6 9v12" />
      </svg>
    ),
  },
  {
    title: 'CI/CD Pipelines',
    description:
      'Automatic build and test pipelines triggered on every push — track status per commit and get notified on failure.',
    icon: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-6 h-6"
        aria-hidden="true"
      >
        <path d="M12 2v4" />
        <path d="m16.2 7.8 2.9-2.9" />
        <path d="M18 12h4" />
        <path d="m16.2 16.2 2.9 2.9" />
        <path d="M12 18v4" />
        <path d="m4.9 19.1 2.9-2.9" />
        <path d="M2 12h4" />
        <path d="m4.9 4.9 2.9 2.9" />
      </svg>
    ),
  },
  {
    title: 'Real-time Notifications',
    description:
      'Instant WebSocket notifications for reviews, comments, and pipeline results — delivered the moment they happen.',
    icon: (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="w-6 h-6"
        aria-hidden="true"
      >
        <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
        <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
      </svg>
    ),
  },
]

// ─── FeatureCard ──────────────────────────────────────────────────────────────

function FeatureCard({ feature }: { feature: Feature }) {
  return (
    <article className="bg-gray-900 border border-gray-800 rounded-xl p-6 flex flex-col gap-4 hover:border-indigo-800 transition-colors">
      <div className="w-10 h-10 rounded-lg bg-indigo-600/20 text-indigo-400 flex items-center justify-center flex-shrink-0">
        {feature.icon}
      </div>
      <div>
        <h3 className="text-base font-semibold text-gray-100 mb-1">{feature.title}</h3>
        <p className="text-sm text-gray-400 leading-relaxed">{feature.description}</p>
      </div>
    </article>
  )
}

// ─── TerminalSnippet ──────────────────────────────────────────────────────────

function TerminalSnippet() {
  return (
    <div
      className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden max-w-2xl mx-auto"
      role="img"
      aria-label="Terminal showing git clone command"
    >
      {/* Title bar */}
      <div className="flex items-center gap-1.5 px-4 py-3 border-b border-gray-800 bg-gray-900/80">
        <span className="w-3 h-3 rounded-full bg-red-500/70" aria-hidden="true" />
        <span className="w-3 h-3 rounded-full bg-yellow-500/70" aria-hidden="true" />
        <span className="w-3 h-3 rounded-full bg-green-500/70" aria-hidden="true" />
        <span className="ml-3 text-xs text-gray-500 font-mono">terminal</span>
      </div>
      {/* Content */}
      <div className="px-5 py-4 font-mono text-sm space-y-1.5">
        <p>
          <span className="text-green-400">$</span>{' '}
          <span className="text-gray-200">
            git clone https://dvcs.example.com/alice/my-project.git
          </span>
        </p>
        <p className="text-gray-500">Cloning into &apos;my-project&apos;...</p>
        <p className="text-gray-500">remote: Counting objects: 142, done.</p>
        <p className="text-gray-500">remote: Compressing objects: 100% (98/98), done.</p>
        <p className="text-gray-500">
          Receiving objects: 100% (142/142), 1.23 MiB | 4.56 MiB/s, done.
        </p>
        <p>
          <span className="text-green-400">$</span>{' '}
          <span className="text-gray-400">cd my-project &amp;&amp; git log --oneline -3</span>
        </p>
        <p>
          <span className="text-yellow-400">a1b2c3d</span>{' '}
          <span className="text-gray-300">feat: add CI pipeline config</span>
        </p>
        <p>
          <span className="text-yellow-400">e4f5a6b</span>{' '}
          <span className="text-gray-300">fix: resolve merge conflict in auth module</span>
        </p>
        <p>
          <span className="text-yellow-400">7c8d9e0</span>{' '}
          <span className="text-gray-300">chore: initial commit</span>
        </p>
      </div>
    </div>
  )
}

// ─── LandingPage ──────────────────────────────────────────────────────────────

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 flex flex-col">
      {/* ── Nav ─────────────────────────────────────────────────────────── */}
      <header className="border-b border-gray-800 bg-gray-900/80 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-3 flex items-center justify-between">
          {/* Logo */}
          <Link
            to="/"
            className="flex items-center gap-2 text-white font-bold text-lg hover:text-indigo-400 transition-colors"
            aria-label="DVCS home"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-6 h-6 text-indigo-400"
              aria-hidden="true"
            >
              <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
              <path d="M9 18c-4.51 2-5-2-7-2" />
            </svg>
            <span>DVCS</span>
          </Link>

          {/* Nav links */}
          <nav className="flex items-center gap-2 sm:gap-4" aria-label="Main navigation">
            <Link
              to="/explore"
              className="text-sm text-gray-400 hover:text-gray-100 transition-colors px-2 py-1"
            >
              Explore
            </Link>
            <Link
              to="/login"
              className="text-sm text-gray-400 hover:text-gray-100 transition-colors px-2 py-1"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="text-sm font-medium bg-indigo-600 hover:bg-indigo-500 text-white rounded-md px-3 py-1.5 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
            >
              Get Started
            </Link>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        {/* ── Hero ──────────────────────────────────────────────────────── */}
        <section
          className="relative overflow-hidden py-24 sm:py-32 px-4 sm:px-6"
          aria-labelledby="hero-heading"
        >
          {/* Background glow */}
          <div
            className="absolute inset-0 -z-10 pointer-events-none"
            aria-hidden="true"
          >
            <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] bg-indigo-600/10 rounded-full blur-3xl" />
          </div>

          <div className="max-w-4xl mx-auto text-center">
            {/* Badge */}
            <div className="inline-flex items-center gap-2 rounded-full border border-indigo-800/60 bg-indigo-950/40 px-3 py-1 text-xs text-indigo-300 mb-6">
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" aria-hidden="true" />
              Open-source developer platform
            </div>

            {/* Headline */}
            <h1
              id="hero-heading"
              className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white mb-6 leading-tight"
            >
              The Developer Platform{' '}
              <span className="text-indigo-400">for Modern Teams</span>
            </h1>

            {/* Subheadline */}
            <p className="text-lg sm:text-xl text-gray-400 max-w-2xl mx-auto mb-10 leading-relaxed">
              Host your code, review pull requests, run CI/CD pipelines, and collaborate
              in real time — all in one self-hosted platform built for engineering teams.
            </p>

            {/* CTA buttons */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Link
                to="/register"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-semibold px-6 py-3 text-base transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2 focus:ring-offset-gray-950"
              >
                Get Started
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                  className="w-4 h-4"
                  aria-hidden="true"
                >
                  <path
                    fillRule="evenodd"
                    d="M3 10a.75.75 0 0 1 .75-.75h10.638L10.23 5.29a.75.75 0 1 1 1.04-1.08l5.5 5.25a.75.75 0 0 1 0 1.08l-5.5 5.25a.75.75 0 1 1-1.04-1.08l4.158-3.96H3.75A.75.75 0 0 1 3 10Z"
                    clipRule="evenodd"
                  />
                </svg>
              </Link>
              <Link
                to="/explore"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-lg border border-gray-700 hover:border-gray-500 text-gray-300 hover:text-white font-semibold px-6 py-3 text-base transition-colors focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2 focus:ring-offset-gray-950"
              >
                Explore
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                  className="w-4 h-4"
                  aria-hidden="true"
                >
                  <path
                    fillRule="evenodd"
                    d="M9 3.5a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11ZM2 9a7 7 0 1 1 12.452 4.391l3.328 3.329a.75.75 0 1 1-1.06 1.06l-3.329-3.328A7 7 0 0 1 2 9Z"
                    clipRule="evenodd"
                  />
                </svg>
              </Link>
            </div>
          </div>
        </section>

        {/* ── Feature highlights ─────────────────────────────────────────── */}
        <section
          className="py-20 px-4 sm:px-6 border-t border-gray-800/60"
          aria-labelledby="features-heading"
        >
          <div className="max-w-6xl mx-auto">
            <div className="text-center mb-12">
              <h2
                id="features-heading"
                className="text-2xl sm:text-3xl font-bold text-white mb-3"
              >
                Everything your team needs
              </h2>
              <p className="text-gray-400 max-w-xl mx-auto">
                A complete platform from source control to deployment, with no external
                dependencies.
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
              {features.map((feature) => (
                <FeatureCard key={feature.title} feature={feature} />
              ))}
            </div>
          </div>
        </section>

        {/* ── Terminal snippet ───────────────────────────────────────────── */}
        <section
          className="py-20 px-4 sm:px-6 border-t border-gray-800/60"
          aria-labelledby="terminal-heading"
        >
          <div className="max-w-6xl mx-auto text-center">
            <h2
              id="terminal-heading"
              className="text-2xl sm:text-3xl font-bold text-white mb-3"
            >
              Works with any Git client
            </h2>
            <p className="text-gray-400 max-w-xl mx-auto mb-10">
              Standard HTTP Smart Git protocol means you can use the tools you already
              know — no plugins, no wrappers.
            </p>
            <TerminalSnippet />
          </div>
        </section>
      </main>

      {/* ── Footer ──────────────────────────────────────────────────────── */}
      <footer className="border-t border-gray-800 bg-gray-900/60 py-8 px-4 sm:px-6">
        <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-sm text-gray-500">
          <div className="flex items-center gap-2">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="w-4 h-4 text-indigo-500"
              aria-hidden="true"
            >
              <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
              <path d="M9 18c-4.51 2-5-2-7-2" />
            </svg>
            <span className="font-medium text-gray-400">DVCS</span>
          </div>

          <p>&copy; {new Date().getFullYear()} DVCS Platform. All rights reserved.</p>

          <nav className="flex items-center gap-4" aria-label="Footer navigation">
            <Link to="/explore" className="hover:text-gray-300 transition-colors">
              Explore
            </Link>
            <Link to="/register" className="hover:text-gray-300 transition-colors">
              Sign up
            </Link>
            <Link to="/login" className="hover:text-gray-300 transition-colors">
              Sign in
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  )
}

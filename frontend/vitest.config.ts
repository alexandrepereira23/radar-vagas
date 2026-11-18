import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    pool: 'vmThreads',
    testTimeout: 30000,
    hookTimeout: 30000
  }
});

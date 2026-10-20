import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';

// In-process lifecycle avoids orphaned npm/cmd children during Windows teardown.
export default async function setup() {
  const server = await createServer({
    root: fileURLToPath(new URL('../', import.meta.url)),
    server: { host: '127.0.0.1', port: 5183, strictPort: true },
  });
  await server.listen();
  return async () => { await server.close(); };
}

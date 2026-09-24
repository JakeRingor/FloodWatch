// Run with Node 24: node supabase/functions/analyze-wheel/proxy.test.mjs
// Mocked contract tests: no credentials, photo uploads, or live API calls.
import assert from 'node:assert/strict';
let handler;
const env = { SUPABASE_URL: 'https://example.invalid', SUPABASE_ANON_KEY: 'test', ROBOFLOW_API_KEY: 'fake-test-key' };
globalThis.Deno = { env: { get: name => env[name] }, serve: callback => { handler = callback; } };
await import('./index.ts');
const request = (body, auth = true) => new Request('https://example.invalid', {
  method: 'POST', headers: auth ? { Authorization: 'Bearer fake-session' } : {}, body,
});
let inferenceCalls = 0;
globalThis.fetch = async (url, options) => {
  if (String(url).endsWith('/auth/v1/user')) return Response.json({ id: 'test-user' });
  inferenceCalls++;
  const payload = JSON.parse(options.body);
  assert.equal(payload.model_id, 'flood-watch-wheelie/5');
  assert.equal(payload.image.type, 'base64');
  assert.equal(payload.confidence, .4);
  return Response.json({ image: {width: 100,height: 100}, predictions: [] });
};
assert.equal((await handler(request('{}', false))).status, 401);
assert.equal((await handler(request('{bad'))).status, 400);
assert.equal((await handler(request(JSON.stringify({image:'not base64!'})))).status, 400);
assert.equal((await handler(request('x'.repeat(4_000_001)))).status, 413);
assert.equal(inferenceCalls, 0);
const result = await handler(request(JSON.stringify({image:'YWJj'})));
assert.equal(result.status, 200);
assert.deepEqual((await result.json()).predictions, []);
delete env.ROBOFLOW_API_KEY;
assert.equal((await handler(request('{}'))).status, 503);
globalThis.fetch = async () => new Response('', {status:401});
assert.equal((await handler(request('{}'))).status, 401);
console.log('7 proxy contract checks passed (mocked; no live inference).');

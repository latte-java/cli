import { artifactIdToPrefix, listVersions } from './repository.js';
import { sortVersionsDescending } from './version.js';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname !== '/repository/search') {
      return new Response('Not Found', { status: 404 });
    }

    if (request.method !== 'GET') {
      return new Response('Method Not Allowed', { status: 405 });
    }

    const id = url.searchParams.get('id');
    if (!id) {
      return Response.json({
        fieldErrors: {
          id: [{ code: '[missing]id', message: 'The [id] parameter is required' }],
        },
      }, { status: 400 });
    }

    const prefix = artifactIdToPrefix(id);
    if (!prefix) {
      return Response.json({
        fieldErrors: {
          id: [{ code: '[invalid]id', message: 'The [id] parameter is not a valid artifact ID' }],
        },
      }, { status: 400 });
    }

    const latest = url.searchParams.get('latest');
    const versions = await listVersions(env.REPOSITORY, prefix);

    if (versions.length === 0) {
      return new Response(null, { status: 404 });
    }

    const sorted = sortVersionsDescending(versions);

    if (latest === 'true') {
      return Response.json({ id, versions: [sorted[0]] });
    }

    return Response.json({ id, versions: sorted });
  },
};

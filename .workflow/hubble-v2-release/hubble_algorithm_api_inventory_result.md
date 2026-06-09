### Hubble Algorithm API Inventory - 2026-06-09

- Status: `SUCCESS`
- Hubble URL: `http://127.0.0.1:8088`
- Server URL: `http://127.0.0.1:8080`
- Smoke suffix: `1781002198`
- Implemented/successful algorithm endpoints: `15`
- Non-success FE algorithm slug endpoints: `2`
- Hubble stop: `stopped HugeGraphHubble`

| URL | HTTP | Business | Type | Graph V/E | Message |
|-----|------|----------|------|-----------|---------|
| `shortestPath` | `200` | `200` | `PATH` | `2/1` | `` |
| `shortpath` | `200` | `200` | `PATH` | `2/1` | `` |
| `allshortpath` | `200` | `200` | `PATH` | `2/1` | `` |
| `paths` | `200` | `200` | `PATH` | `6/0` | `` |
| `rings` | `200` | `200` | `PATH` | `6/9` | `` |
| `crosspoints` | `200` | `200` | `PATH` | `6/0` | `` |
| `fsimilarity` | `200` | `200` | `GENERAL` | `` | `` |
| `neighborrank` | `200` | `200` | `GENERAL` | `` | `` |
| `kneighbor` | `200` | `200` | `PATH` | `6/5` | `` |
| `kout` | `200` | `200` | `PATH` | `0/0` | `` |
| `customizedpaths` | `200` | `200` | `PATH` | `5/4` | `` |
| `rays` | `200` | `200` | `PATH` | `6/9` | `` |
| `sameneighbors` | `200` | `200` | `GENERAL` | `` | `` |
| `weightedshortpath` | `200` | `200` | `PATH` | `2/1` | `` |
| `singleshortpath` | `200` | `200` | `PATH` | `6/5` | `` |
| `jaccardsimilarity` | `200` | `400` | `None` | `` | `Failed to deserialize: {"jaccard_similarity":0.3333333333333333,"measure":{"edge_iterations":8,"vertice_iterations":2,"cost(ns)":312800}}` |
| `personalrank` | `200` | `400` | `None` | `` | `The edge label for personal rank must link different vertex labels` |

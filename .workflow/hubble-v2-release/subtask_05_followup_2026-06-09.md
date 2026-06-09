## Follow-up Server Comparison - 2026-06-09

Command:

```bash
python3 .workflow/hubble-v2-release/run_live_hubble_smoke.py
```

Result: `SUCCESS`.

Compared flows:

- Hubble schema create vs direct Server schema read.
- Hubble Gremlin count vs direct Server Gremlin count.
- Hubble shortestPath API vs direct Server traverser shortestPath.
- Hubble import orchestration vs direct Server post-import data visibility.

Observed comparison:

- Hubble-created property key `smoke_name_1780981142` was visible from direct Server schema API with matching `data_type`.
- Hubble-created vertex label `SmokePerson_1780981142` was visible from direct Server schema API with matching `id_strategy`.
- Hubble-created edge label `SmokeRelation_1780981142` was visible from direct Server schema API with matching source/target labels.
- Hubble and direct Server Gremlin counts matched: vertices `41` vs `41`, edges `51` vs `51`.
- Hubble shortestPath graph_view vertices/edges matched direct Server traverser vertices/edges: `6`/`5` vs `6`/`5`.

Classification:

- No Server/API gap was found in verified schema, Gremlin count, import data visibility, or shortestPath traverser flows.
- The previous label-count failure was configuration/data-shape related: Server rejects label count queries when label index is disabled.
- Hubble import remains a product-boundary orchestration flow: Hubble stores local job metadata and invokes HugeGraph Loader, while Server exposes the resulting graph data and schema.

Conclusion: Subtask 05 direct Server comparison verification is now complete.

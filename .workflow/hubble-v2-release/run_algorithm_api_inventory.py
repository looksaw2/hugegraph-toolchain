#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership.
#

import gzip
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "hugegraph-hubble" / "hubble-dist" / "apache-hugegraph-hubble-1.7.0"
OUT_DIR = ROOT / ".workflow" / "hubble-v2-release"
HUBBLE = "http://127.0.0.1:8088"
SERVER = "http://127.0.0.1:8080"


class ProbeError(RuntimeError):
    pass


def request(method, url, body=None, timeout=30):
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        status = e.code
    except urllib.error.URLError as e:
        raise ProbeError(f"{method} {url} failed: {e}") from e
    if raw.startswith(b"\x1f\x8b"):
        raw = gzip.decompress(raw)
    text = raw.decode("utf-8", errors="replace")
    parsed = None
    if text:
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            parsed = text
    return status, parsed, text


def wait_health(timeout=90):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            status, parsed, text = request("GET", f"{HUBBLE}/actuator/health", timeout=5)
            if status == 200:
                return parsed
            last = text[:300]
        except ProbeError as e:
            last = str(e)
        time.sleep(2)
    raise ProbeError(f"Timed out waiting for Hubble health: {last}")


def is_hubble_up():
    try:
        status, _, _ = request("GET", f"{HUBBLE}/actuator/health", timeout=5)
        return status == 200
    except ProbeError:
        return False


def run_script(name):
    proc = subprocess.run(
        [f"./{name}"],
        cwd=DIST / "bin",
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=90,
    )
    return proc.returncode, proc.stdout.strip()


def server_gremlin(gremlin):
    body = {
        "gremlin": gremlin,
        "language": "gremlin-groovy",
        "aliases": {
            "graph": "DEFAULT-hugegraph",
            "g": "__g_DEFAULT-hugegraph",
        },
    }
    status, parsed, text = request("POST", f"{SERVER}/gremlin", body)
    if status != 200 or not isinstance(parsed, dict):
        raise ProbeError(f"Server gremlin failed HTTP {status}: {text[:1000]}")
    data = parsed.get("result", {}).get("data", [])
    return data[0] if data else None


def hubble_api(method, path, body=None, timeout=30):
    status, parsed, text = request(method, f"{HUBBLE}{path}", body, timeout)
    if status != 200 or not isinstance(parsed, dict) or parsed.get("status") != 200:
        raise ProbeError(f"{method} {path} failed HTTP {status}: {text[:1000]}")
    return parsed.get("data")


def create_or_reuse_connection():
    body = {
        "name": "AlgorithmApiInventory",
        "graph": "hugegraph",
        "host": "127.0.0.1",
        "port": 8080,
        "username": "",
        "password": "",
    }
    try:
        return hubble_api("POST", "/api/v1.2/graph-connections", body), "created"
    except ProbeError as e:
        exists = (
            "Already exists connection with same graph" in str(e) or
            "Already exists connection with same name" in str(e)
        )
        if not exists:
            raise
    page = hubble_api(
        "GET",
        "/api/v1.2/graph-connections?page_no=1&page_size=100",
    )
    for conn in page.get("records", []):
        if conn.get("name") == body["name"] or (
            conn.get("graph") == body["graph"]
            and conn.get("host") == body["host"]
            and conn.get("port") == body["port"]
        ):
            return hubble_api("GET", f"/api/v1.2/graph-connections/{conn['id']}"), "reused"
    raise ProbeError("Connection exists by uniqueness check but was not found")


def create_schema(conn_id, suffix):
    vertex_label = f"AlgoVertex_{suffix}"
    edge_label = f"AlgoEdge_{suffix}"
    pk_names = {
        "name": f"algo_name_{suffix}",
        "group": f"algo_group_{suffix}",
        "weight": f"algo_weight_{suffix}",
    }
    for name, data_type in (
        (pk_names["name"], "TEXT"),
        (pk_names["group"], "TEXT"),
        (pk_names["weight"], "DOUBLE"),
    ):
        hubble_api(
            "POST",
            f"/api/v1.2/graph-connections/{conn_id}/schema/propertykeys",
            {
                "name": name,
                "data_type": data_type,
                "cardinality": "SINGLE",
            },
        )
    hubble_api(
        "POST",
        f"/api/v1.2/graph-connections/{conn_id}/schema/vertexlabels",
        {
            "name": vertex_label,
            "id_strategy": "CUSTOMIZE_STRING",
            "properties": [
                {"name": pk_names["name"], "nullable": False},
                {"name": pk_names["group"], "nullable": True},
            ],
            "primary_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [pk_names["name"]],
                "join_symbols": ["-"],
            },
        },
    )
    hubble_api(
        "POST",
        f"/api/v1.2/graph-connections/{conn_id}/schema/edgelabels",
        {
            "name": edge_label,
            "source_label": vertex_label,
            "target_label": vertex_label,
            "link_multi_times": False,
            "properties": [{"name": pk_names["weight"], "nullable": False}],
            "sort_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [pk_names["weight"]],
                "join_symbols": ["-"],
            },
        },
    )
    return pk_names, vertex_label, edge_label


def create_graph_data(conn_id, suffix, pk_names, vertex_label, edge_label):
    def vid(name):
        return f"{name}_{suffix}"

    vertices = [
        ("a", "A", "left"),
        ("b", "B", "middle"),
        ("c", "C", "middle"),
        ("d", "D", "right"),
        ("e", "E", "shared"),
        ("f", "F", "shared"),
    ]
    for raw_id, name, group in vertices:
        hubble_api(
            "POST",
            f"/api/v1.2/graph-connections/{conn_id}/graph/vertex",
            {
                "id": vid(raw_id),
                "label": vertex_label,
                "properties": {
                    pk_names["name"]: name,
                    pk_names["group"]: group,
                },
            },
        )

    edges = [
        ("a", "b", 1.0),
        ("b", "c", 1.0),
        ("c", "d", 1.0),
        ("d", "a", 1.0),
        ("a", "e", 2.0),
        ("b", "e", 2.0),
        ("a", "f", 3.0),
        ("b", "f", 3.0),
        ("c", "f", 1.5),
    ]
    for source, target, weight in edges:
        hubble_api(
            "POST",
            f"/api/v1.2/graph-connections/{conn_id}/graph/edge",
            {
                "label": edge_label,
                "source": vid(source),
                "target": vid(target),
                "properties": {
                    pk_names["weight"]: weight,
                },
            },
        )
    return {
        "a": vid("a"),
        "b": vid("b"),
        "c": vid("c"),
        "d": vid("d"),
        "e": vid("e"),
        "f": vid("f"),
        "edge_label": edge_label,
        "vertex_label": vertex_label,
        "weight": pk_names["weight"],
    }


def build_algorithm_requests(data):
    common = {
        "direction": "BOTH",
        "label": data["edge_label"],
        "max_depth": 4,
        "max_degree": 10000,
        "capacity": 10000000,
        "limit": 20,
    }
    step = {
        "direction": "BOTH",
        "labels": [data["edge_label"]],
        "degree": 10000,
        "sample": 100,
    }
    rank_step = {
        "direction": "BOTH",
        "labels": [data["edge_label"]],
        "degree": 10000,
        "top": 100,
    }
    return {
        "shortestPath": {
            **common,
            "source": data["a"],
            "target": data["d"],
            "skip_degree": 0,
        },
        "shortpath": {
            **common,
            "source": data["a"],
            "target": data["d"],
            "skip_degree": 0,
        },
        "allshortpath": {
            **common,
            "source": data["a"],
            "target": data["d"],
            "skip_degree": 0,
        },
        "paths": {
            **common,
            "source": data["a"],
            "target": data["d"],
        },
        "rings": {
            **common,
            "source": data["a"],
            "source_in_ring": True,
        },
        "crosspoints": {
            **common,
            "source": data["a"],
            "target": data["b"],
        },
        "fsimilarity": {
            "sources": {"ids": [data["a"], data["b"]]},
            "direction": "BOTH",
            "label": data["edge_label"],
            "min_neighbors": 1,
            "alpha": 0.5,
            "min_similars": 1,
            "top": 10,
            "max_degree": 10000,
            "capacity": 10000000,
            "limit": 20,
            "with_intermediary": True,
            "with_vertex": True,
        },
        "neighborrank": {
            "source": data["a"],
            "alpha": 0.85,
            "capacity": 10000000,
            "steps": [rank_step],
        },
        "kneighbor": {
            **common,
            "source": data["a"],
        },
        "kout": {
            **common,
            "source": data["a"],
            "nearest": True,
        },
        "customizedpaths": {
            "sources": {"ids": [data["a"]]},
            "sort_by": "NONE",
            "capacity": 10000000,
            "limit": 20,
            "steps": [step],
        },
        "rays": {
            **common,
            "source": data["a"],
        },
        "sameneighbors": {
            "vertex": data["a"],
            "other": data["b"],
            "direction": "BOTH",
            "label": data["edge_label"],
            "max_degree": 10000,
            "limit": 20,
        },
        "weightedshortpath": {
            **common,
            "source": data["a"],
            "target": data["d"],
            "weight": data["weight"],
            "skip_degree": 0,
            "with_vertex": True,
        },
        "singleshortpath": {
            **common,
            "source": data["a"],
            "weight": data["weight"],
            "skip_degree": 0,
            "with_vertex": True,
        },
        "jaccardsimilarity": {
            "vertex": data["a"],
            "other": data["b"],
            "direction": "BOTH",
            "label": data["edge_label"],
            "max_degree": 10000,
        },
        "personalrank": {
            "source": data["a"],
            "label": data["edge_label"],
            "alpha": 0.85,
            "max_depth": 4,
            "with_label": "BOTH_LABEL",
            "degree": 10000,
            "limit": 20,
            "sorted": True,
        },
    }


def hubble_post_algorithm(conn_id, url, body):
    status, parsed, text = request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/algorithms/{url}",
        body,
        timeout=30,
    )
    business_status = None
    message = ""
    data_type = None
    graph_vertices = None
    graph_edges = None
    if isinstance(parsed, dict):
        business_status = parsed.get("status")
        message = parsed.get("message") or parsed.get("error") or ""
        data = parsed.get("data")
        if isinstance(data, dict):
            data_type = data.get("type")
            graph_view = data.get("graph_view") or {}
            vertices = graph_view.get("vertices")
            edges = graph_view.get("edges")
            graph_vertices = len(vertices) if isinstance(vertices, list) else None
            graph_edges = len(edges) if isinstance(edges, list) else None
    return {
        "url": url,
        "http_status": status,
        "business_status": business_status,
        "message": str(message)[:500],
        "data_type": data_type,
        "graph_vertices": graph_vertices,
        "graph_edges": graph_edges,
        "raw_sample": text[:500],
    }


def main():
    smoke_suffix = str(int(time.time()))
    report = {
        "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "status": "FAILED",
        "hubble_url": HUBBLE,
        "server_url": SERVER,
        "smoke_suffix": smoke_suffix,
        "checks": {},
    }
    started = False
    try:
        if not is_hubble_up():
            code, output = run_script("start-hubble.sh")
            report["checks"]["hubble_start"] = {"code": code, "output": output}
            if code != 0:
                raise ProbeError(f"start-hubble.sh failed: {output}")
            started = True
        report["checks"]["hubble_health"] = wait_health()

        conn, conn_mode = create_or_reuse_connection()
        conn_id = conn["id"]
        report["checks"]["connection"] = {
            "id": conn_id,
            "name": conn.get("name"),
            "graph": conn.get("graph"),
            "host": conn.get("host"),
            "port": conn.get("port"),
            "mode": conn_mode,
        }

        pk_names, vertex_label, edge_label = create_schema(conn_id, smoke_suffix)
        data = create_graph_data(conn_id, smoke_suffix, pk_names, vertex_label,
                                 edge_label)
        vertex_count = server_gremlin(f"g.V().hasLabel('{vertex_label}').count()")
        edge_count = server_gremlin(f"g.E().hasLabel('{edge_label}').count()")
        if vertex_count != 6 or edge_count != 9:
            raise ProbeError(
                f"Expected algorithm smoke graph 6/9, got {vertex_count}/{edge_count}"
            )
        report["checks"]["smoke_graph"] = {
            "vertex_label": vertex_label,
            "edge_label": edge_label,
            "weight_property": pk_names["weight"],
            "vertices": vertex_count,
            "edges": edge_count,
            "ids": data,
        }

        urls = [
            "shortestPath",
            "shortpath",
            "allshortpath",
            "paths",
            "rings",
            "crosspoints",
            "fsimilarity",
            "neighborrank",
            "kneighbor",
            "kout",
            "customizedpaths",
            "rays",
            "sameneighbors",
            "weightedshortpath",
            "singleshortpath",
            "jaccardsimilarity",
            "personalrank",
        ]
        requests = build_algorithm_requests(data)
        results = []
        for url in urls:
            results.append(hubble_post_algorithm(conn_id, url, requests[url]))
        report["checks"]["algorithm_results"] = results
        report["status"] = "SUCCESS"
    except Exception as e:
        report["error"] = str(e)
        raise
    finally:
        if started:
            code, output = run_script("stop-hubble.sh")
            report["checks"]["hubble_stop"] = {"code": code, "output": output}
        else:
            report["checks"]["hubble_stop"] = {
                "code": 0,
                "output": "skipped; Hubble was already running or start failed before ownership",
            }
        report["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        json_path = OUT_DIR / "hubble_algorithm_api_inventory_result.json"
        md_path = OUT_DIR / "hubble_algorithm_api_inventory_result.md"
        json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                             encoding="utf-8")

        results = report.get("checks", {}).get("algorithm_results", [])
        implemented = [
            item for item in results
            if item.get("http_status") == 200 and item.get("business_status") == 200
        ]
        non_success = [
            item for item in results
            if item.get("http_status") != 200 or item.get("business_status") != 200
        ]
        lines = [
            "### Hubble Algorithm API Inventory - 2026-06-09",
            "",
            f"- Status: `{report['status']}`",
            f"- Hubble URL: `{HUBBLE}`",
            f"- Server URL: `{SERVER}`",
            f"- Smoke suffix: `{report['smoke_suffix']}`",
            f"- Implemented/successful algorithm endpoints: `{len(implemented)}`",
            f"- Non-success FE algorithm slug endpoints: `{len(non_success)}`",
            f"- Hubble stop: `{report['checks']['hubble_stop']['output']}`",
            "",
            "| URL | HTTP | Business | Type | Graph V/E | Message |",
            "|-----|------|----------|------|-----------|---------|",
        ]
        for item in results:
            graph_shape = (
                f"{item.get('graph_vertices')}/{item.get('graph_edges')}"
                if item.get("graph_vertices") is not None else ""
            )
            lines.append(
                f"| `{item['url']}` | `{item.get('http_status')}` | "
                f"`{item.get('business_status')}` | `{item.get('data_type')}` | "
                f"`{graph_shape}` | `{item.get('message')}` |"
            )
        if report.get("error"):
            lines.append("")
            lines.append(f"- Error: `{report['error']}`")
        md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"wrote {json_path}")
        print(f"wrote {md_path}")
        print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

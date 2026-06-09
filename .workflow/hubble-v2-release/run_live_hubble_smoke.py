#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership.
#

import json
import gzip
import os
import signal
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "hugegraph-hubble" / "hubble-dist" / "apache-hugegraph-hubble-1.7.0"
DATASET = ROOT / "dataset" / "hlm.zip"
OUT_DIR = ROOT / ".workflow" / "hubble-v2-release"
HUBBLE = "http://127.0.0.1:8088"
SERVER = "http://127.0.0.1:8080"


class SmokeError(RuntimeError):
    pass


def request(method, url, body=None, headers=None, timeout=30, expect=(200,)):
    data = None
    headers = dict(headers or {})
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers.setdefault("Content-Type", "application/json")
        elif isinstance(body, bytes):
            data = body
        else:
            data = str(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        status = e.code
    except urllib.error.URLError as e:
        raise SmokeError(f"{method} {url} failed: {e}") from e
    if raw.startswith(b"\x1f\x8b"):
        raw = gzip.decompress(raw)
    text = raw.decode("utf-8", errors="replace")
    parsed = None
    if text:
        try:
            parsed = json.loads(text)
        except json.JSONDecodeError:
            parsed = text
    if status not in expect:
        raise SmokeError(f"{method} {url} returned {status}: {text[:1000]}")
    if url.startswith(f"{HUBBLE}/api/") and isinstance(parsed, dict) and "status" in parsed:
        app_status = parsed.get("status")
        if app_status != 200:
            raise SmokeError(f"{method} {url} returned app status {app_status}: {text[:1000]}")
        parsed = parsed.get("data")
    return status, parsed, text


def multipart_upload(url, fields, file_field, path, timeout=60):
    boundary = f"----hubble-smoke-{int(time.time() * 1000)}"
    chunks = []
    for name, value in fields.items():
        chunks.append(f"--{boundary}\r\n".encode())
        chunks.append(
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode()
        )
        chunks.append(str(value).encode("utf-8"))
        chunks.append(b"\r\n")
    chunks.append(f"--{boundary}\r\n".encode())
    chunks.append(
        (
            f'Content-Disposition: form-data; name="{file_field}"; '
            f'filename="{path.name}"\r\n'
        ).encode()
    )
    chunks.append(b"Content-Type: text/plain; charset=utf-8\r\n\r\n")
    chunks.append(path.read_bytes())
    chunks.append(b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode())
    body = b"".join(chunks)
    return request(
        "POST",
        url,
        body=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        timeout=timeout,
    )


def wait_health(url, timeout=90):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            status, parsed, _ = request("GET", url, timeout=5)
            return status, parsed
        except SmokeError as e:
            last = str(e)
            time.sleep(2)
    raise SmokeError(f"Timed out waiting for {url}: {last}")


def start_hubble():
    proc = subprocess.run(
        ["./start-hubble.sh"],
        cwd=DIST / "bin",
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=90,
    )
    if proc.returncode != 0:
        raise SmokeError(f"start-hubble.sh failed:\n{proc.stdout}")
    wait_health(f"{HUBBLE}/actuator/health", timeout=90)
    return proc.stdout


def stop_hubble():
    try:
        proc = subprocess.run(
            ["./stop-hubble.sh"],
            cwd=DIST / "bin",
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
        )
        return proc.stdout.strip(), proc.returncode
    except Exception as e:
        return str(e), -1


def write_hlm_file(suffix):
    tmp_dir = Path("/tmp") / f"hubble-live-smoke-{suffix}"
    tmp_dir.mkdir(parents=True, exist_ok=True)
    target = tmp_dir / f"HLM_SMOKE_{suffix}.TXT"
    with zipfile.ZipFile(DATASET) as zf:
        raw = zf.read("hlm/hlm.txt").decode("utf-8")
    header = (
        "source_name,source_sex,source_age,source_title,source_feature,"
        "target_name,target_sex,target_age,target_title,target_feature,relation"
    )
    lines = [header]
    for line in raw.splitlines():
        cols = line.split(",")
        if len(cols) != 11:
            raise SmokeError(f"Unexpected HLM column count: {line}")
        cols[0] = f"{cols[0]}_{suffix}"
        cols[5] = f"{cols[5]}_{suffix}"
        lines.append(",".join(cols))
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return target


def first_data_value(response):
    data = response.get("json_view", {}).get("data", [])
    if not data:
        return None
    value = data[0]
    if isinstance(value, dict) and "object" in value:
        return value["object"]
    return value


def gremlin_server(gremlin):
    body = {
        "gremlin": gremlin,
        "language": "gremlin-groovy",
        "aliases": {
            "graph": "DEFAULT-hugegraph",
            "g": "__g_DEFAULT-hugegraph",
        },
    }
    _, parsed, text = request("POST", f"{SERVER}/gremlin", body)
    if not isinstance(parsed, dict):
        raise SmokeError(f"Server gremlin response is not JSON object: {text[:1000]}")
    data = parsed.get("result", {}).get("data", [])
    return data[0] if data else None


def create_or_reuse_connection(suffix):
    body = {
        "name": f"SmokeConn_{suffix}",
        "graph": "hugegraph",
        "host": "127.0.0.1",
        "port": 8080,
        "username": "",
        "password": "",
    }
    try:
        _, conn, _ = request(
            "POST",
            f"{HUBBLE}/api/v1.2/graph-connections",
            body,
            timeout=30,
        )
        return conn, "created"
    except SmokeError as e:
        if "Already exists connection with same graph" not in str(e):
            raise
    _, page, _ = request(
        "GET",
        f"{HUBBLE}/api/v1.2/graph-connections?page_no=1&page_size=100",
        timeout=30,
    )
    for conn in page.get("records", []):
        if (
            conn.get("graph") == body["graph"]
            and conn.get("host") == body["host"]
            and conn.get("port") == body["port"]
        ):
            _, loaded, _ = request(
                "GET",
                f"{HUBBLE}/api/v1.2/graph-connections/{conn['id']}",
                timeout=30,
            )
            return loaded, "reused"
    raise SmokeError("Connection exists by uniqueness check but was not found in list")


def create_schema(conn_id, suffix):
    props = {
        "name": "TEXT",
        "sex": "TEXT",
        "age": "INT",
        "title": "TEXT",
        "feature": "TEXT",
        "relation": "TEXT",
    }
    pk_names = {k: f"smoke_{k}_{suffix}" for k in props}
    for key, data_type in props.items():
        request(
            "POST",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/propertykeys",
            {
                "name": pk_names[key],
                "data_type": data_type,
                "cardinality": "SINGLE",
            },
        )

    vertex_label = f"SmokePerson_{suffix}"
    edge_label = f"SmokeRelation_{suffix}"
    vertex_props = [
        {"name": pk_names["name"], "nullable": False},
        {"name": pk_names["sex"], "nullable": True},
        {"name": pk_names["age"], "nullable": True},
        {"name": pk_names["title"], "nullable": True},
        {"name": pk_names["feature"], "nullable": True},
    ]
    request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/vertexlabels",
        {
            "name": vertex_label,
            "id_strategy": "CUSTOMIZE_STRING",
            "properties": vertex_props,
            "primary_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [pk_names["name"]],
                "join_symbols": ["-"],
            },
        },
    )
    request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/edgelabels",
        {
            "name": edge_label,
            "source_label": vertex_label,
            "target_label": vertex_label,
            "link_multi_times": False,
            "properties": [{"name": pk_names["relation"], "nullable": False}],
            "sort_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [pk_names["relation"]],
                "join_symbols": ["-"],
            },
        },
    )
    return pk_names, vertex_label, edge_label


def import_hlm(conn_id, suffix, data_file, pk_names, vertex_label, edge_label):
    _, job, _ = request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager",
        {
            "job_name": f"SmokeJob_{suffix}",
            "job_remarks": f"SmokeJob_{suffix}",
        },
    )
    job_id = job["id"]
    _, tokens, _ = request(
        "GET",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/upload-file/token?names="
            f"{urllib.parse.quote(data_file.name)}"
        ),
    )
    token = tokens[data_file.name]
    _, upload, _ = multipart_upload(
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}/upload-file",
        {
            "name": data_file.name,
            "size": data_file.stat().st_size,
            "token": token,
            "total": 1,
            "index": 0,
        },
        "file",
        data_file,
    )
    file_mapping_id = upload["id"]
    request(
        "PUT",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}/upload-file/next-step",
    )
    columns = [
        "source_name",
        "source_sex",
        "source_age",
        "source_title",
        "source_feature",
        "target_name",
        "target_sex",
        "target_age",
        "target_title",
        "target_feature",
        "relation",
    ]
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/file-setting"
        ),
        {
            "has_header": True,
            "column_names": columns,
            "format": "CSV",
            "delimiter": ",",
            "charset": "UTF-8",
            "date_format": "yyyy-MM-dd HH:mm:ss",
            "time_zone": "GMT+8",
            "skipped_line": "(^#|^//).*|",
            "list_format": {
                "start_symbol": "[",
                "end_symbol": "]",
                "elem_delimiter": "|",
            },
        },
    )

    null_values = {"checked": [], "customized": ["-"]}
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/vertex-mappings"
        ),
        {
            "label": vertex_label,
            "id_fields": ["source_name"],
            "field_mapping": [
                {"column_name": "source_name", "mapped_name": pk_names["name"]},
                {"column_name": "source_sex", "mapped_name": pk_names["sex"]},
                {"column_name": "source_age", "mapped_name": pk_names["age"]},
                {"column_name": "source_title", "mapped_name": pk_names["title"]},
                {"column_name": "source_feature", "mapped_name": pk_names["feature"]},
            ],
            "value_mapping": [],
            "null_values": null_values,
        },
    )
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/vertex-mappings"
        ),
        {
            "label": vertex_label,
            "id_fields": ["target_name"],
            "field_mapping": [
                {"column_name": "target_name", "mapped_name": pk_names["name"]},
                {"column_name": "target_sex", "mapped_name": pk_names["sex"]},
                {"column_name": "target_age", "mapped_name": pk_names["age"]},
                {"column_name": "target_title", "mapped_name": pk_names["title"]},
                {"column_name": "target_feature", "mapped_name": pk_names["feature"]},
            ],
            "value_mapping": [],
            "null_values": null_values,
        },
    )
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/edge-mappings"
        ),
        {
            "label": edge_label,
            "source_fields": ["source_name"],
            "target_fields": ["target_name"],
            "field_mapping": [
                {"column_name": "relation", "mapped_name": pk_names["relation"]},
            ],
            "value_mapping": [],
            "null_values": null_values,
        },
    )
    request(
        "PUT",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}/file-mappings/next-step",
    )
    _, tasks, _ = request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/load-tasks/start?file_mapping_ids={file_mapping_id}"
        ),
        {},
        timeout=60,
    )
    task_id = tasks[0]["id"]
    final_job = None
    final_task = None
    deadline = time.time() + 180
    while time.time() < deadline:
        _, final_job, _ = request(
            "GET",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}",
        )
        _, final_task, _ = request(
            "GET",
            (
                f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
                f"{job_id}/load-tasks/{task_id}"
            ),
        )
        if final_task["status"] in ("SUCCESS", "SUCCEED", "FAILED", "STOPPED"):
            break
        time.sleep(3)
    if final_task is None or final_task["status"] not in ("SUCCESS", "SUCCEED"):
        _, reason, _ = request(
            "GET",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}/reason",
            expect=(200, 500),
        )
        raise SmokeError(
            f"Load task did not succeed: job={final_job}, task={final_task}, reason={reason}"
        )
    return job_id, task_id, final_job, final_task


def run():
    suffix = str(int(time.time()))
    report = {
        "suffix": suffix,
        "started_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "status": "FAILED",
        "checks": {},
    }
    started = False
    stop_output = None
    try:
        request("GET", f"{SERVER}/versions", timeout=10)
        start_output = start_hubble()
        started = True
        report["checks"]["hubble_start"] = start_output.strip()

        data_file = write_hlm_file(suffix)
        report["checks"]["data_file"] = str(data_file)

        conn, conn_mode = create_or_reuse_connection(suffix)
        conn_id = conn["id"]
        report["checks"]["conn_id"] = conn_id
        report["checks"]["connection_mode"] = conn_mode

        pk_names, vertex_label, edge_label = create_schema(conn_id, suffix)
        report["checks"]["schema"] = {
            "property_keys": pk_names,
            "vertex_label": vertex_label,
            "edge_label": edge_label,
        }

        job_id, task_id, final_job, final_task = import_hlm(
            conn_id, suffix, data_file, pk_names, vertex_label, edge_label
        )
        report["checks"]["import"] = {
            "job_id": job_id,
            "task_id": task_id,
            "job_status": final_job["job_status"],
            "task_status": final_task["status"],
            "loaded_vertices": final_task.get("loaded_vertex_count"),
            "loaded_edges": final_task.get("loaded_edge_count"),
            "total_lines": final_task.get("total_lines"),
        }

        vertex_query = f"g.V().hasLabel('{vertex_label}').count()"
        edge_query = f"g.E().hasLabel('{edge_label}').count()"
        _, hubble_v, _ = request(
            "POST",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/gremlin-query",
            {"content": vertex_query},
        )
        _, hubble_e, _ = request(
            "POST",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/gremlin-query",
            {"content": edge_query},
        )
        hubble_vertex_count = first_data_value(hubble_v)
        hubble_edge_count = first_data_value(hubble_e)
        server_vertex_count = gremlin_server(vertex_query)
        server_edge_count = gremlin_server(edge_query)
        report["checks"]["gremlin_counts"] = {
            "hubble_vertices": hubble_vertex_count,
            "hubble_edges": hubble_edge_count,
            "server_vertices": server_vertex_count,
            "server_edges": server_edge_count,
        }
        if hubble_vertex_count != 41 or hubble_edge_count != 51:
            raise SmokeError(f"Unexpected Hubble counts: V={hubble_vertex_count}, E={hubble_edge_count}")
        if server_vertex_count != 41 or server_edge_count != 51:
            raise SmokeError(f"Unexpected Server counts: V={server_vertex_count}, E={server_edge_count}")

        source = f"贾太公_{suffix}"
        target = f"贾蓉_{suffix}"
        shortest_body = {
            "source": source,
            "target": target,
            "direction": "OUT",
            "label": edge_label,
            "max_depth": 10,
            "max_degree": 10000,
            "skip_degree": 0,
            "capacity": 10000000,
        }
        _, shortest, _ = request(
            "POST",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/algorithms/shortestPath",
            shortest_body,
            timeout=60,
        )
        encoded_source = urllib.parse.quote(json.dumps(source, ensure_ascii=False))
        encoded_target = urllib.parse.quote(json.dumps(target, ensure_ascii=False))
        server_sp_url = (
            f"{SERVER}/graphspaces/DEFAULT/graphs/hugegraph/traversers/shortestpath"
            f"?source={encoded_source}&target={encoded_target}&direction=OUT"
            f"&label={urllib.parse.quote(edge_label)}&max_depth=10"
            f"&max_degree=10000&skip_degree=0&capacity=10000000"
        )
        _, server_sp, _ = request("GET", server_sp_url, timeout=60)
        graph_view = shortest.get("graph_view") or {}
        hubble_sp_vertices = len(graph_view.get("vertices") or [])
        hubble_sp_edges = len(graph_view.get("edges") or [])
        server_sp_vertices = len(server_sp.get("vertices") or [])
        server_sp_edges = len(server_sp.get("edges") or [])
        server_path = server_sp.get("path") or []
        if isinstance(server_path, dict):
            server_path_len = len(server_path.get("objects", []) or [])
        else:
            server_path_len = len(server_path)
        report["checks"]["shortest_path"] = {
            "hubble_type": shortest.get("type"),
            "hubble_graph_vertices": hubble_sp_vertices,
            "hubble_graph_edges": hubble_sp_edges,
            "server_vertices": server_sp_vertices,
            "server_edges": server_sp_edges,
            "server_path_len": server_path_len,
        }
        if shortest.get("type") != "PATH" or hubble_sp_vertices < 2 or hubble_sp_edges < 1:
            raise SmokeError(f"Unexpected Hubble shortestPath result: {report['checks']['shortest_path']}")
        if server_sp_vertices < 2 or server_sp_edges < 1:
            raise SmokeError(f"Unexpected Server shortestPath result: {server_sp}")

        _, hubble_pk, _ = request(
            "GET",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/propertykeys/{pk_names['name']}",
        )
        _, hubble_vl, _ = request(
            "GET",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/vertexlabels/{vertex_label}",
        )
        _, hubble_el, _ = request(
            "GET",
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/edgelabels/{edge_label}",
        )
        _, server_pk, _ = request(
            "GET",
            f"{SERVER}/graphspaces/DEFAULT/graphs/hugegraph/schema/propertykeys/{pk_names['name']}",
        )
        _, server_vl, _ = request(
            "GET",
            f"{SERVER}/graphspaces/DEFAULT/graphs/hugegraph/schema/vertexlabels/{vertex_label}",
        )
        _, server_el, _ = request(
            "GET",
            f"{SERVER}/graphspaces/DEFAULT/graphs/hugegraph/schema/edgelabels/{edge_label}",
        )
        report["checks"]["schema_compare"] = {
            "property_key": {
                "hubble": hubble_pk["name"],
                "server": server_pk["name"],
                "data_type_match": hubble_pk["data_type"] == server_pk["data_type"],
            },
            "vertex_label": {
                "hubble": hubble_vl["name"],
                "server": server_vl["name"],
                "id_strategy_match": hubble_vl["id_strategy"] == server_vl["id_strategy"],
            },
            "edge_label": {
                "hubble": hubble_el["name"],
                "server": server_el["name"],
                "source_target_match": (
                    hubble_el["source_label"] == server_el["source_label"]
                    and hubble_el["target_label"] == server_el["target_label"]
                ),
            },
        }

        report["status"] = "SUCCESS"
        return 0, report
    except Exception as e:
        report["error"] = str(e)
        report["traceback"] = traceback.format_exc()
        return 1, report
    finally:
        if started:
            stop_output = stop_hubble()
        if stop_output is not None:
            report["checks"]["hubble_stop"] = stop_output
        report["finished_at"] = time.strftime("%Y-%m-%d %H:%M:%S %z")


def write_reports(report):
    json_path = OUT_DIR / "hubble_live_smoke_result.json"
    md_path = OUT_DIR / "hubble_live_smoke_result.md"
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    checks = report.get("checks", {})
    counts = checks.get("gremlin_counts", {})
    sp = checks.get("shortest_path", {})
    schema = checks.get("schema", {})
    imp = checks.get("import", {})
    lines = [
        "### Live Hubble + Server Smoke - 2026-06-09",
        "",
        f"- Status: `{report.get('status')}`",
        f"- Suffix: `{report.get('suffix')}`",
        f"- Connection id: `{checks.get('conn_id')}`",
        f"- Vertex label: `{schema.get('vertex_label')}`",
        f"- Edge label: `{schema.get('edge_label')}`",
        f"- Import: job `{imp.get('job_id')}`, task `{imp.get('task_id')}`, "
        f"job status `{imp.get('job_status')}`, task status `{imp.get('task_status')}`",
        f"- Gremlin counts: Hubble V/E `{counts.get('hubble_vertices')}`/"
        f"`{counts.get('hubble_edges')}`, Server V/E `{counts.get('server_vertices')}`/"
        f"`{counts.get('server_edges')}`",
        f"- shortestPath: Hubble type `{sp.get('hubble_type')}`, graph_view V/E "
        f"`{sp.get('hubble_graph_vertices')}`/`{sp.get('hubble_graph_edges')}`, "
        f"Server traverser V/E `{sp.get('server_vertices')}`/`{sp.get('server_edges')}`",
    ]
    if report.get("status") != "SUCCESS":
        lines.append(f"- Error: `{report.get('error')}`")
    schema_compare = checks.get("schema_compare")
    if schema_compare:
        lines.extend(
            [
                "- Schema compare: Hubble-created property key, vertex label, and edge label "
                "were visible through direct Server schema endpoints with matching core fields.",
            ]
        )
    stop = checks.get("hubble_stop")
    if stop:
        lines.append(f"- Hubble stop: `{stop[0]}`")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, md_path


if __name__ == "__main__":
    code, report = run()
    json_path, md_path = write_reports(report)
    print(f"wrote {json_path}")
    print(f"wrote {md_path}")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    sys.exit(code)

#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership.
#

import csv
import importlib.util
import json
import sys
import time
import traceback
import urllib.parse
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / ".workflow" / "hubble-v2-release"
DATASET = ROOT / "dataset" / "movie 2.zip"
LIVE_SMOKE = OUT_DIR / "run_live_hubble_smoke.py"


spec = importlib.util.spec_from_file_location("live_smoke", LIVE_SMOKE)
live_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(live_smoke)

HUBBLE = live_smoke.HUBBLE
SERVER = live_smoke.SERVER
SmokeError = live_smoke.SmokeError

COL_MOVIE = "movie_name"
COL_DIRECTOR = "director"
COL_ACTOR = "actor"
COL_GENRE = "genre"
COL_YEAR = "release_year"
COLUMNS = [COL_MOVIE, COL_DIRECTOR, COL_ACTOR, COL_GENRE, COL_YEAR]


def first_value(value):
    if isinstance(value, list) and value:
        return value[0].strip()
    if isinstance(value, str):
        return value.split("|", 1)[0].strip()
    return ""


def write_movie_file(suffix):
    tmp_dir = Path("/tmp") / f"hubble-movie-smoke-{suffix}"
    tmp_dir.mkdir(parents=True, exist_ok=True)
    target = tmp_dir / f"MOVIE_SMOKE_{suffix}.CSV"

    rows = 0
    with zipfile.ZipFile(DATASET) as zf:
        with zf.open("movie/movie.csv") as raw:
            text = raw.read().decode("utf-8-sig")
    reader = csv.DictReader(text.splitlines())
    with target.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=COLUMNS)
        writer.writeheader()
        for row in reader:
            movie = (row.get("\u540d\u79f0") or "").strip()
            director = first_value(row.get("\u5bfc\u6f14") or "")
            actor = first_value(row.get("\u6f14\u5458") or "")
            genre = first_value(row.get("\u7c7b\u578b") or "")
            year = (row.get("\u53d1\u884c\u65f6\u95f4") or "").strip()
            if not movie or not director or not actor or not genre or not year:
                continue
            writer.writerow({
                COL_MOVIE: f"{movie}_{suffix}",
                COL_DIRECTOR: f"{director}_{suffix}",
                COL_ACTOR: f"{actor}_{suffix}",
                COL_GENRE: f"{genre}_{suffix}",
                COL_YEAR: year,
            })
            rows += 1
    if rows == 0:
        raise SmokeError("No usable movie rows were extracted from movie dataset")
    return target, rows


def request(method, url, body=None, headers=None, timeout=30, expect=(200,)):
    return live_smoke.request(method, url, body, headers, timeout, expect)


def create_or_reuse_connection(suffix):
    return live_smoke.create_or_reuse_connection(suffix)


def create_property_key(conn_id, name, data_type="TEXT"):
    request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/propertykeys",
        {
            "name": name,
            "data_type": data_type,
            "cardinality": "SINGLE",
        },
    )


def property_ref(name, nullable=True):
    return {"name": name, "nullable": nullable}


def create_vertex_label(conn_id, label, props, id_display_prop):
    request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/vertexlabels",
        {
            "name": label,
            "id_strategy": "CUSTOMIZE_STRING",
            "properties": props,
            "primary_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [id_display_prop],
                "join_symbols": ["-"],
            },
        },
    )


def create_edge_label(conn_id, label, source_label, target_label):
    request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/schema/edgelabels",
        {
            "name": label,
            "source_label": source_label,
            "target_label": target_label,
            "link_multi_times": False,
            "properties": [],
            "sort_keys": [],
            "property_indexes": [],
            "open_label_index": True,
            "style": {
                "display_fields": [],
                "join_symbols": ["-"],
            },
        },
    )


def create_schema(conn_id, suffix):
    props = {
        "movie_name": f"movie_name_{suffix}",
        "artist_name": f"artist_name_{suffix}",
        "genre_name": f"genre_name_{suffix}",
        "release_year": f"release_year_{suffix}",
    }
    for name in props.values():
        create_property_key(conn_id, name)

    labels = {
        "movie": f"Movie_{suffix}",
        "artist": f"Artist_{suffix}",
        "genre": f"Genre_{suffix}",
        "directed": f"Directed_{suffix}",
        "acted_in": f"ActedIn_{suffix}",
        "in_genre": f"InGenre_{suffix}",
    }
    create_vertex_label(
        conn_id,
        labels["movie"],
        [
            property_ref(props["movie_name"], nullable=False),
            property_ref(props["release_year"]),
        ],
        props["movie_name"],
    )
    create_vertex_label(
        conn_id,
        labels["artist"],
        [property_ref(props["artist_name"], nullable=False)],
        props["artist_name"],
    )
    create_vertex_label(
        conn_id,
        labels["genre"],
        [property_ref(props["genre_name"], nullable=False)],
        props["genre_name"],
    )
    create_edge_label(conn_id, labels["directed"], labels["artist"], labels["movie"])
    create_edge_label(conn_id, labels["acted_in"], labels["artist"], labels["movie"])
    create_edge_label(conn_id, labels["in_genre"], labels["movie"], labels["genre"])
    return props, labels


def upload_file(conn_id, suffix, data_file):
    _, job, _ = request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager",
        {
            "job_name": f"MovieSmokeJob_{suffix}",
            "job_remarks": f"MovieSmokeJob_{suffix}",
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
    _, upload, _ = live_smoke.multipart_upload(
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
    request(
        "PUT",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/{job_id}/upload-file/next-step",
    )
    return job_id, upload["id"]


def add_file_setting(conn_id, job_id, file_mapping_id):
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/file-setting"
        ),
        {
            "has_header": True,
            "column_names": COLUMNS,
            "format": "CSV",
            "delimiter": ",",
            "charset": "UTF-8",
            "date_format": "yyyy",
            "time_zone": "GMT+8",
            "skipped_line": "(^#|^//).*|",
            "list_format": {
                "start_symbol": "[",
                "end_symbol": "]",
                "elem_delimiter": "|",
            },
        },
    )


def add_vertex_mapping(conn_id, job_id, file_mapping_id, label, id_field, field_mapping):
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/vertex-mappings"
        ),
        {
            "label": label,
            "id_fields": [id_field],
            "field_mapping": [
                {"column_name": column, "mapped_name": prop}
                for column, prop in field_mapping.items()
            ],
            "value_mapping": [],
            "null_values": {"checked": [], "customized": ["", "NULL", "null"]},
        },
    )


def add_edge_mapping(conn_id, job_id, file_mapping_id, label, source, target):
    request(
        "POST",
        (
            f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/job-manager/"
            f"{job_id}/file-mappings/{file_mapping_id}/edge-mappings"
        ),
        {
            "label": label,
            "source_fields": [source],
            "target_fields": [target],
            "field_mapping": [],
            "value_mapping": [],
            "null_values": {"checked": [], "customized": ["", "NULL", "null"]},
        },
    )


def import_movie(conn_id, suffix, data_file, props, labels):
    job_id, file_mapping_id = upload_file(conn_id, suffix, data_file)
    add_file_setting(conn_id, job_id, file_mapping_id)
    add_vertex_mapping(
        conn_id,
        job_id,
        file_mapping_id,
        labels["movie"],
        COL_MOVIE,
        {
            COL_MOVIE: props["movie_name"],
            COL_YEAR: props["release_year"],
        },
    )
    add_vertex_mapping(
        conn_id,
        job_id,
        file_mapping_id,
        labels["artist"],
        COL_DIRECTOR,
        {COL_DIRECTOR: props["artist_name"]},
    )
    add_vertex_mapping(
        conn_id,
        job_id,
        file_mapping_id,
        labels["artist"],
        COL_ACTOR,
        {COL_ACTOR: props["artist_name"]},
    )
    add_vertex_mapping(
        conn_id,
        job_id,
        file_mapping_id,
        labels["genre"],
        COL_GENRE,
        {COL_GENRE: props["genre_name"]},
    )
    add_edge_mapping(conn_id, job_id, file_mapping_id, labels["directed"], COL_DIRECTOR, COL_MOVIE)
    add_edge_mapping(conn_id, job_id, file_mapping_id, labels["acted_in"], COL_ACTOR, COL_MOVIE)
    add_edge_mapping(conn_id, job_id, file_mapping_id, labels["in_genre"], COL_MOVIE, COL_GENRE)
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
    deadline = time.time() + 300
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
            f"Movie load task did not succeed: job={final_job}, task={final_task}, "
            f"reason={reason}"
        )
    return job_id, task_id, final_job, final_task


def gremlin_count(conn_id, gremlin):
    _, hubble_response, _ = request(
        "POST",
        f"{HUBBLE}/api/v1.2/graph-connections/{conn_id}/gremlin-query",
        {"content": gremlin},
    )
    return live_smoke.first_data_value(hubble_response), live_smoke.gremlin_server(gremlin)


def server_count(gremlin):
    return live_smoke.gremlin_server(gremlin)


def run():
    suffix = str(int(time.time()))
    report = {
        "suffix": suffix,
        "started_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "status": "FAILED",
        "dataset": str(DATASET),
        "checks": {},
    }
    started = False
    stop_output = None
    try:
        request("GET", f"{SERVER}/versions", timeout=10)
        start_output = live_smoke.start_hubble()
        started = True
        report["checks"]["hubble_start"] = start_output.strip()

        data_file, extracted_rows = write_movie_file(suffix)
        report["checks"]["derived_file"] = str(data_file)
        report["checks"]["extracted_rows"] = extracted_rows

        conn, conn_mode = create_or_reuse_connection(suffix)
        conn_id = conn["id"]
        report["checks"]["conn_id"] = conn_id
        report["checks"]["connection_mode"] = conn_mode

        props, labels = create_schema(conn_id, suffix)
        report["checks"]["schema"] = {
            "property_keys": props,
            "labels": labels,
        }

        job_id, task_id, final_job, final_task = import_movie(
            conn_id, suffix, data_file, props, labels
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

        counts = {}
        for key in ("movie", "artist", "genre"):
            label = labels[key]
            server_value = server_count(f"g.V().hasLabel('{label}').count()")
            counts[f"{key}_vertices"] = {
                "server": server_value,
            }
        report["checks"]["gremlin_counts"] = counts

        # Do not use full edge-label counts here. On this dataset HugeGraph can
        # materialize a large edge-id IN query for g.E().hasLabel(...).count(),
        # which trips the Server big-id length guard even after import succeeds.
        movie_count = counts["movie_vertices"]["server"]
        artist_count = counts["artist_vertices"]["server"]
        genre_count = counts["genre_vertices"]["server"]
        if movie_count <= 0 or artist_count <= 0 or genre_count <= 0:
            raise SmokeError(f"Unexpected movie import counts: {counts}")
        if movie_count > extracted_rows:
            raise SmokeError(
                f"Movie vertex count {movie_count} exceeds extracted rows {extracted_rows}"
            )

        sample_name = f"0.5\u6beb\u7c73_{suffix}"
        sample_director = f"\u5b89\u85e4\u6843\u5b50_{suffix}"
        sample_actor = f"\u5b89\u85e4\u6a31_{suffix}"
        sample_genre = f"\u5267\u60c5_{suffix}"
        sample_queries = {
            "directed": (
                f"g.V('{sample_director}').outE('{labels['directed']}')"
                f".inV().hasId('{sample_name}').count()"
            ),
            "acted_in": (
                f"g.V('{sample_actor}').outE('{labels['acted_in']}')"
                f".inV().hasId('{sample_name}').count()"
            ),
            "in_genre": (
                f"g.V('{sample_name}').outE('{labels['in_genre']}')"
                f".inV().hasId('{sample_genre}').count()"
            ),
        }
        sample_results = {}
        for name, gremlin in sample_queries.items():
            hubble_sample, server_sample = gremlin_count(conn_id, gremlin)
            sample_results[name] = {
                "gremlin": gremlin,
                "hubble": hubble_sample,
                "server": server_sample,
            }
            if hubble_sample != 1 or server_sample != 1:
                raise SmokeError(f"Unexpected sample query result: {sample_results[name]}")
        report["checks"]["sample_queries"] = sample_results

        hubble_vertex_sample, server_vertex_sample = gremlin_count(
            conn_id,
            f"g.V().hasLabel('{labels['movie']}').limit(1).count()",
        )
        report["checks"]["hubble_api_sample"] = {
            "movie_limit_count": {
                "hubble": hubble_vertex_sample,
                "server": server_vertex_sample,
            }
        }
        if hubble_vertex_sample != 1 or server_vertex_sample != 1:
            raise SmokeError(
                f"Unexpected Hubble API sample result: "
                f"{report['checks']['hubble_api_sample']}"
            )

        report["status"] = "SUCCESS"
        return 0, report
    except Exception as e:
        report["error"] = str(e)
        report["traceback"] = traceback.format_exc()
        return 1, report
    finally:
        if started:
            stop_output = live_smoke.stop_hubble()
        if stop_output is not None:
            report["checks"]["hubble_stop"] = stop_output
        report["finished_at"] = time.strftime("%Y-%m-%d %H:%M:%S %z")


def write_reports(report):
    json_path = OUT_DIR / "hubble_movie_dataset_smoke_result.json"
    md_path = OUT_DIR / "hubble_movie_dataset_smoke_result.md"
    json_path.write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    checks = report.get("checks", {})
    schema = checks.get("schema", {})
    labels = schema.get("labels", {})
    imp = checks.get("import", {})
    counts = checks.get("gremlin_counts", {})
    lines = [
        "### Movie Dataset Hubble + Server Smoke - 2026-06-09",
        "",
        f"- Status: `{report.get('status')}`",
        f"- Dataset: `{report.get('dataset')}`",
        f"- Suffix: `{report.get('suffix')}`",
        f"- Connection id: `{checks.get('conn_id')}`",
        f"- Derived CSV rows: `{checks.get('extracted_rows')}`",
        f"- Movie vertex label: `{labels.get('movie')}`",
        f"- Import: job `{imp.get('job_id')}`, task `{imp.get('task_id')}`, "
        f"job status `{imp.get('job_status')}`, task status `{imp.get('task_status')}`",
    ]
    for name in (
        "movie_vertices",
        "artist_vertices",
        "genre_vertices",
    ):
        value = counts.get(name, {})
        lines.append(
            f"- {name}: Server `{value.get('server')}`"
        )
    samples = checks.get("sample_queries", {})
    for name, sample in samples.items():
        lines.append(
            f"- Sample {name}: Hubble `{sample.get('hubble')}`, "
            f"Server `{sample.get('server')}`"
        )
    if report.get("status") != "SUCCESS":
        lines.append(f"- Error: `{report.get('error')}`")
    stop = checks.get("hubble_stop")
    if stop:
        lines.append(f"- Hubble stop: `{stop[0]}`")
    lines.append("")
    lines.append(
        "The source archive remains local-only and is not treated as ASF release evidence."
    )
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, md_path


if __name__ == "__main__":
    code, result = run()
    json_report, md_report = write_reports(result)
    print(f"wrote {json_report}")
    print(f"wrote {md_report}")
    print(json.dumps(result, indent=2, ensure_ascii=False))
    sys.exit(code)

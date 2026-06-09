#!/usr/bin/env python3

import json
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "hugegraph-hubble" / "hubble-dist" / "apache-hugegraph-hubble-1.7.0"
OUT_DIR = ROOT / ".workflow" / "hubble-v2-release"
HUBBLE = "http://127.0.0.1:8088"


class SmokeError(RuntimeError):
    pass


def request(path, timeout=15):
    url = f"{HUBBLE}{path}"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            raw = resp.read()
            status = resp.status
            headers = dict(resp.headers.items())
    except urllib.error.HTTPError as e:
        raw = e.read()
        status = e.code
        headers = dict(e.headers.items())
    if status != 200:
        raise SmokeError(f"GET {path} returned {status}: {raw[:200]!r}")
    return status, headers, raw


def wait_health(timeout=90):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            status, _, raw = request("/actuator/health", timeout=5)
            return json.loads(raw.decode("utf-8"))
        except Exception as e:
            last = str(e)
            time.sleep(2)
    raise SmokeError(f"Timed out waiting for Hubble health: {last}")


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
    wait_health()
    return proc.stdout.strip()


def stop_hubble():
    proc = subprocess.run(
        ["./stop-hubble.sh"],
        cwd=DIST / "bin",
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=30,
    )
    return proc.stdout.strip(), proc.returncode


def paths_from_index(index_html):
    return re.findall(r'(?:href|src)="([^"]+)"', index_html)


def run():
    report = {
        "started_at": time.strftime("%Y-%m-%d %H:%M:%S %z"),
        "status": "FAILED",
        "checks": {},
    }
    started = False
    try:
        report["checks"]["hubble_start"] = start_hubble()
        started = True
        _, index_headers, index_raw = request("/")
        index_html = index_raw.decode("utf-8", errors="replace")
        if '<div id="root"></div>' not in index_html:
            raise SmokeError("index.html does not contain React root")
        asset_paths = paths_from_index(index_html)
        report["checks"]["index"] = {
            "content_type": index_headers.get("Content-Type"),
            "has_root": True,
            "asset_paths": asset_paths,
        }
        assets = {}
        for path in asset_paths:
            parsed = urllib.parse.urlparse(path)
            asset_path = parsed.path
            if not asset_path or asset_path == "/":
                continue
            _, headers, raw = request(asset_path)
            assets[asset_path] = {
                "content_type": headers.get("Content-Type"),
                "bytes": len(raw),
            }
            if asset_path.endswith(".js") and "/main." in asset_path:
                text = raw.decode("utf-8", errors="replace")
                if "HugeGraph" not in text and "graph-connections" not in text:
                    raise SmokeError(f"JS asset {asset_path} lacks expected app markers")
            if asset_path.endswith(".js") and len(raw) < 100:
                raise SmokeError(f"JS asset {asset_path} is unexpectedly small")
            if asset_path.endswith(".css") and len(raw) < 100:
                raise SmokeError(f"CSS asset {asset_path} is unexpectedly small")
        report["checks"]["assets"] = assets

        _, manifest_headers, manifest_raw = request("/asset-manifest.json")
        asset_manifest = json.loads(manifest_raw.decode("utf-8"))
        report["checks"]["asset_manifest"] = {
            "content_type": manifest_headers.get("Content-Type"),
            "entry_count": len(asset_manifest.get("files", {})),
        }
        _, api_headers, api_raw = request("/api/v1.2/setting/config")
        config = json.loads(api_raw.decode("utf-8"))
        if config.get("status") != 200:
            raise SmokeError(f"setting config returned unexpected payload: {config}")
        report["checks"]["setting_config"] = {
            "content_type": api_headers.get("Content-Type"),
            "status": config.get("status"),
            "keys": sorted((config.get("data") or {}).keys()),
        }
        report["status"] = "SUCCESS"
        return 0, report
    except Exception as e:
        report["error"] = str(e)
        return 1, report
    finally:
        if started:
            report["checks"]["hubble_stop"] = stop_hubble()
        report["finished_at"] = time.strftime("%Y-%m-%d %H:%M:%S %z")


def write_reports(report):
    json_path = OUT_DIR / "hubble_ui_served_smoke_result.json"
    md_path = OUT_DIR / "hubble_ui_served_smoke_result.md"
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    checks = report.get("checks", {})
    assets = checks.get("assets", {})
    js_assets = [path for path in assets if path.endswith(".js")]
    css_assets = [path for path in assets if path.endswith(".css")]
    lines = [
        "### UI Served Smoke - 2026-06-09",
        "",
        f"- Status: `{report.get('status')}`",
        f"- Index served: `{checks.get('index', {}).get('has_root')}`",
        f"- JS assets served: `{len(js_assets)}`",
        f"- CSS assets served: `{len(css_assets)}`",
        f"- Asset manifest entries: `{checks.get('asset_manifest', {}).get('entry_count')}`",
        f"- Setting config status: `{checks.get('setting_config', {}).get('status')}`",
    ]
    if report.get("status") != "SUCCESS":
        lines.append(f"- Error: `{report.get('error')}`")
    stop = checks.get("hubble_stop")
    if stop:
        lines.append(f"- Hubble stop: `{stop[0]}`")
    lines.append("")
    lines.append("Note: this is an HTTP-served asset smoke, not a browser rendering/click test.")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return json_path, md_path


if __name__ == "__main__":
    code, report = run()
    json_path, md_path = write_reports(report)
    print(f"wrote {json_path}")
    print(f"wrote {md_path}")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    raise SystemExit(code)

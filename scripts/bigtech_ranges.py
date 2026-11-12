#!/usr/bin/env python3
"""Automated resilient asset updater script for MangosWatch.
Fetches public IP ranges for major tech providers and updates the respective
IPv4 and IPv6 .txt files in docs/data/. Resilient to non-critical source failures.

Usage: python3 scripts/bigtech_ranges.py
"""
import ipaddress
import os
import re
import sys
from datetime import date
from functools import partial
import uuid
import requests

OUTPUT_DIR = "docs/data"
S = requests.Session()
S.headers["User-Agent"] = "MangosWatch-AssetUpdater/1.0"

# Critical sources whose failure should abort the pipeline
CRITICAL_SOURCES = {"google", "amazon", "azure"}

# BGP ASNs for organizations without direct JSON endpoints
ASNS = {
    "meta": [32934, 63293, 54115],
    "apple": [714, 6185],
    "netflix": [2906, 40027, 55095],
    "nvidia": [11414, 20347, 50889, 398037, 38834, 60977],
    "microsoft": [8075],
}

# Static entries not covered by dynamic JSON feeds
STATIC = [
    ("anthropic", "160.79.104.0/23", "inbound"),
    ("anthropic", "2607:6bc0::/48", "inbound"),
    ("anthropic", "160.79.104.0/21", "outbound"),
    ("apple", "17.0.0.0/8", "apple-owned"),
]

collected_data = {}  # org -> set of ip_network objects


def add_prefix(org, prefix, source, tag=""):
    try:
        net = ipaddress.ip_network(prefix.strip(), strict=False)
        collected_data.setdefault(org, set()).add(net)
    except Exception as e:
        print(f"Warning: invalid prefix '{prefix}' for org '{org}': {e}", file=sys.stderr)


def get(url, **kw):
    r = S.get(url, timeout=30, **kw)
    r.raise_for_status()
    return r


def subtract(nets, holes):
    result = list(ipaddress.collapse_addresses(nets))
    for hole in ipaddress.collapse_addresses(holes):
        nxt = []
        for n in result:
            if not n.overlaps(hole):
                nxt.append(n)
            elif hole.supernet_of(n):
                continue
            else:
                nxt.extend(n.address_exclude(hole))
        result = nxt
    return list(ipaddress.collapse_addresses(result))


def fetch_google():
    goog_url = "https://www.gstatic.com/ipranges/goog.json"
    cloud_url = "https://www.gstatic.com/ipranges/cloud.json"

    def get_nets(url):
        return [ipaddress.ip_network(p.get("ipv4Prefix") or p["ipv6Prefix"]) for p in get(url).json()["prefixes"]]

    goog = get_nets(goog_url)
    cloud = get_nets(cloud_url)

    for v in (4, 6):
        goog_v = [n for n in goog if n.version == v]
        cloud_v = [n for n in cloud if n.version == v]
        for n in subtract(goog_v, cloud_v):
            add_prefix("google", str(n), "goog-minus-cloud")


def fetch_amazon():
    d = get("https://ip-ranges.amazonaws.com/ip-ranges.json").json()
    for p in d["prefixes"]:
        if p["service"] == "AMAZON":
            add_prefix("amazon", p["ip_prefix"], "aws-ip-ranges")
    for p in d["ipv6_prefixes"]:
        if p["service"] == "AMAZON":
            add_prefix("amazon", p["ip_prefix"], "aws-ip-ranges")


def fetch_azure():
    url = os.environ.get("MS_TAGS_URL")
    if not url:
        try:
            html = get("https://www.microsoft.com/en-us/download/details.aspx?id=56519").text
            m = re.search(r"https://download\.microsoft\.com/download/[^\"'\s]+?ServiceTags_Public_\d+\.json", html)
            url = m.group(0) if m else None
        except Exception:
            pass
    if not url:
        raise RuntimeError("Azure Service Tags URL not found; set MS_TAGS_URL environment variable")

    for v in get(url).json()["values"]:
        if v["name"] == "AzureCloud":
            for p in v["properties"]["addressPrefixes"]:
                add_prefix("microsoft", p, "azure-service-tags")


def fetch_m365():
    d = get(f"https://endpoints.office.com/endpoints/worldwide?clientrequestid={uuid.uuid4()}").json()
    for e in d:
        for ip in e.get("ips", []):
            add_prefix("microsoft", ip, "m365-endpoints")


def fetch_openai(bot):
    for p in get(f"https://openai.com/{bot}.json").json()["prefixes"]:
        add_prefix("openai", p.get("ipv4Prefix") or p["ipv6Prefix"], f"openai-{bot}")


def fetch_applebot():
    for p in get("https://search.developer.apple.com/applebot.json").json()["prefixes"]:
        add_prefix("apple", p.get("ipv4Prefix") or p["ipv6Prefix"], "applebot")


def fetch_static():
    for org, prefix, _ in STATIC:
        add_prefix(org, prefix, "static")


def fetch_asn(org, n):
    d = get("https://stat.ripe.net/data/announced-prefixes/data.json", params={"resource": f"AS{n}"}).json()
    for p in d["data"]["prefixes"]:
        add_prefix(org, p["prefix"], f"bgp-AS{n}")


# Register sources
SOURCES = {
    "google": fetch_google,
    "amazon": fetch_amazon,
    "azure": fetch_azure,
    "m365": fetch_m365,
    "applebot": fetch_applebot,
    "static": fetch_static,
}
for bot in ("gptbot", "searchbot", "chatgpt-user", "adsbot"):
    SOURCES[f"openai:{bot}"] = partial(fetch_openai, bot)
for org, ns in ASNS.items():
    for n in ns:
        SOURCES[f"{org}:AS{n}"] = partial(fetch_asn, org, n)


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    failed = []

    for name, fn in SOURCES.items():
        try:
            fn()
            print(f"OK: {name}", file=sys.stderr)
        except Exception as e:
            failed.append(name)
            print(f"WARNING/FAIL: {name} -> {e}", file=sys.stderr)

    # Write out individual <org>_ipv4.txt and <org>_ipv6.txt files for successfully fetched data
    for org, nets in collected_data.items():
        for v in (4, 6):
            version_nets = [n for n in nets if n.version == v]
            if not version_nets:
                continue
            collapsed = list(ipaddress.collapse_addresses(version_nets))
            filepath = os.path.join(OUTPUT_DIR, f"{org}_ipv{v}.txt")
            with open(filepath, "w") as f:
                f.write(f"# Updated automatically on {date.today()} ({org.upper()} IPv{v})\n")
                for net in collapsed:
                    f.write(f"{net}\n")
            print(f"Wrote {filepath} ({len(collapsed)} prefixes)", file=sys.stderr)

    if failed:
        print(f"\n[NOTIFICATION] The following sources failed to update: {failed}", file=sys.stderr)
        # Check if any critical source failed
        critical_failed = [s for s in failed if any(c in s for c in CRITICAL_SOURCES)]
        if critical_failed or len(failed) == len(SOURCES):
            print(f"CRITICAL: Critical sources failed ({critical_failed}) or all sources failed. Exiting with error.", file=sys.stderr)
            sys.exit(1)
        else:
            print("Non-critical sources failed. Proceeding with partial update successfully.", file=sys.stderr)
            sys.exit(0)
    else:
        print("All asset ranges successfully updated.", file=sys.stderr)
        sys.exit(0)


if __name__ == "__main__":
    main()

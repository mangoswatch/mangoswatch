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
DROP_THRESHOLD_RATIO = 0.70  # Alert and skip overwrite if prefix count drops below 70% of current (30% drop)

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
    "x_twitter": [13414],
    "xiaomi": [55960, 133957],
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
    all_prefixes = d.get("prefixes", []) + d.get("ipv6_prefixes", [])
    customer_services = {"EC2", "EC2_INSTANCE_CONNECT", "CLOUDFRONT", "S3", "ROUTE53_HEALTHCHECKS"}
    for v in (4, 6):
        amazon_nets = [ipaddress.ip_network(p.get("ip_prefix") or p.get("ipv6_prefix")) for p in all_prefixes if p.get("service") in ("AMAZON", "GLOBAL") and ipaddress.ip_network(p.get("ip_prefix") or p.get("ipv6_prefix")).version == v]
        customer_nets = [ipaddress.ip_network(p.get("ip_prefix") or p.get("ipv6_prefix")) for p in all_prefixes if p.get("service") in customer_services and ipaddress.ip_network(p.get("ip_prefix") or p.get("ipv6_prefix")).version == v]
        for n in subtract(amazon_nets, customer_nets):
            add_prefix("amazon", str(n), "aws-amazon-minus-customer-services")


def fetch_azure():
    try:
        html = get("https://www.microsoft.com/en-us/download/details.aspx?id=56519").text
        m = re.search(r"https://download\.microsoft\.com/download/[^\"'\s]+?ServiceTags_Public_\d+\.json", html)
        url = m.group(0) if m else None
    except Exception:
        url = None

    if not url:
        raise RuntimeError("Azure Service Tags URL could not be retrieved from Microsoft download page.")

    first_party_services = {"AzureActiveDirectory", "AzureResourceManager"}
    for v in get(url).json()["values"]:
        if v["name"] in first_party_services:
            for p in v["properties"]["addressPrefixes"]:
                add_prefix("microsoft", p, f"azure-service-{v['name']}")


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


# Register sources and map each to its organization
SOURCES = {
    "google": fetch_google,
    "amazon": fetch_amazon,
    "azure": fetch_azure,
    "m365": fetch_m365,
    "applebot": fetch_applebot,
    "static": fetch_static,
}
SOURCE_TO_ORG = {
    "google": "google",
    "amazon": "amazon",
    "azure": "microsoft",
    "m365": "microsoft",
    "applebot": "apple",
    "static": "static",
}
for bot in ("gptbot", "searchbot", "chatgpt-user", "adsbot"):
    src_key = f"openai:{bot}"
    SOURCES[src_key] = partial(fetch_openai, bot)
    SOURCE_TO_ORG[src_key] = "openai"

for org, ns in ASNS.items():
    for n in ns:
        src_key = f"{org}:AS{n}"
        SOURCES[src_key] = partial(fetch_asn, org, n)
        SOURCE_TO_ORG[src_key] = org


def count_existing_prefixes(filepath):
    if not os.path.exists(filepath):
        return 0
    try:
        with open(filepath, "r") as f:
            return sum(1 for line in f if line.strip() and not line.strip().startswith("#"))
    except Exception:
        return 0


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    failed_sources = []
    failed_orgs = set()

    for name, fn in SOURCES.items():
        try:
            fn()
            print(f"OK: {name}", file=sys.stderr)
        except Exception as e:
            failed_sources.append(name)
            org = SOURCE_TO_ORG.get(name)
            if org and org != "static":
                failed_orgs.add(org)
            print(f"WARNING/FAIL: {name} -> {e}", file=sys.stderr)

    # Write out individual <org>_ipv4.txt and <org>_ipv6.txt files for organizations without source failures
    for org, nets in collected_data.items():
        if org in failed_orgs:
            print(f"SKIPPING: Organization '{org}' had failed sources. File overwrite aborted.", file=sys.stderr)
            continue

        org_failed_drop = False
        files_to_write = []

        for v in (4, 6):
            version_nets = [n for n in nets if n.version == v]
            if not version_nets:
                continue
            collapsed = list(ipaddress.collapse_addresses(version_nets))
            filepath = os.path.join(OUTPUT_DIR, f"{org}_ipv{v}.txt")

            existing_count = count_existing_prefixes(filepath)
            if existing_count > 0 and len(collapsed) < existing_count * DROP_THRESHOLD_RATIO:
                print(
                    f"ERROR: Prefix count for {org} IPv{v} dropped by >30% ({existing_count} -> {len(collapsed)}). Aborting overwrite for '{org}'.",
                    file=sys.stderr
                )
                org_failed_drop = True
                break
            files_to_write.append((filepath, collapsed, org, v))

        if org_failed_drop:
            failed_orgs.add(org)
            continue

        for filepath, collapsed, org_name, v in files_to_write:
            with open(filepath, "w") as f:
                f.write(f"# Updated automatically on {date.today()} ({org_name.upper()} IPv{v})\n")
                for net in collapsed:
                    f.write(f"{net}\n")
            print(f"Wrote {filepath} ({len(collapsed)} prefixes)", file=sys.stderr)

    if failed_sources or failed_orgs:
        print(f"\n[NOTIFICATION] Failed sources: {failed_sources}", file=sys.stderr)
        print(f"[NOTIFICATION] Failed orgs (not overwritten): {sorted(failed_orgs)}", file=sys.stderr)
        critical_failed = [s for s in failed_sources if any(c in s for c in CRITICAL_SOURCES)]
        if critical_failed or failed_orgs or len(failed_sources) == len(SOURCES):
            print(f"CRITICAL: Failures detected in pipeline ({failed_sources}). Exiting with error.", file=sys.stderr)
            sys.exit(1)

    print("All asset ranges successfully updated.", file=sys.stderr)
    sys.exit(0)


if __name__ == "__main__":
    main()

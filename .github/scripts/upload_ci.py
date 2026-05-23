"""
Telegram release uploader for Nnngram CI.

Flow (all routed through the local telegram-bot-api on 127.0.0.1:8081):
  1. POST grouped HTML changelog (all commits in this push) to METADATA_CHANNEL.
  2. POST arm64 APK with a short HTML caption to CHAT_ID.
  3. POST same APK to APK_CHANNEL and capture `message_id - 1` as start_id.
  4. POST `<version>,<code>,<start_id>,<changes_id>,false` to METADATA_CHANNEL
     as the tracking record consumed by the in-app update checker.

Message format:
  Commits are bucketed by Conventional Commits type (fix/feat/docs/...) so
  the channel post reads like a release note instead of a raw `git log`.
  HTML parse_mode keeps escaping rules trivial — only `< > &` need swapping.

Inputs (env vars):
  TELEGRAM_TOKEN          bot token
  CHAT_ID                 main chat that receives the APK + caption
  METADATA_CHANNEL_ID     optional override (default: legacy channel)
  APK_CHANNEL_ID          optional override (default: legacy channel)
  VERSION_NAME            e.g. "12.7.3-da90be4"
  VERSION_CODE            e.g. "1779541780"
  COMMITS_JSON            JSON array from github.event.commits (push event)
  HEAD_COMMIT_MESSAGE     fallback when COMMITS_JSON is empty (workflow_dispatch)
  REPO_URL                e.g. "https://github.com/NextAlone/Nnngram"; if set,
                          commit shas are rendered as deep-links into GitHub
  APK_DIR                 optional APK source dir (default ./apks)
"""

from __future__ import annotations

import json
import os
import re
import shutil
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

import requests

API_BASE = "http://127.0.0.1:8081/bot"
DEFAULT_METADATA_CHANNEL = -1002135305446
DEFAULT_APK_CHANNEL = -1001848519901

# Telegram caps: caption 1024, message text 4096. Reserve headroom for the
# header + group labels + sha codes + truncation marker.
CAPTION_BUDGET = 900
MESSAGE_BUDGET = 3800

# Conventional Commits buckets. Ordering here drives the rendered group order.
GROUPS: List[Tuple[str, str, Tuple[str, ...]]] = [
    ("feat",     "✨ <b>Features</b>",    ("feat",)),
    ("fix",      "🔧 <b>Fixes</b>",       ("fix",)),
    ("perf",     "⚡ <b>Performance</b>", ("perf",)),
    ("refactor", "♻️ <b>Refactor</b>",   ("refactor", "refa")),
    ("docs",     "📝 <b>Docs</b>",        ("docs", "doc")),
    ("test",     "✅ <b>Tests</b>",       ("test",)),
    ("build",    "📦 <b>Build</b>",       ("build",)),
    ("ci",       "⚙️ <b>CI</b>",         ("ci",)),
    ("style",    "💄 <b>Style</b>",       ("style",)),
    ("chore",    "🧹 <b>Chore</b>",       ("chore",)),
    ("revert",   "⏪ <b>Revert</b>",      ("revert",)),
    ("merge",    "🔀 <b>Merge</b>",       ("merge",)),
    ("other",    "📌 <b>Other</b>",       ()),
]
GROUP_INDEX: Dict[str, int] = {key: i for i, (key, _label, _) in enumerate(GROUPS)}
TYPE_TO_GROUP: Dict[str, str] = {t: key for key, _label, types in GROUPS for t in types}

# `<type>(<scope>)?: <subject>` per Conventional Commits.
SUBJECT_RE = re.compile(r"^(?P<type>[A-Za-z]+)(?:\([^)]*\))?!?:\s*(?P<rest>.+)$")


@dataclass
class Commit:
    sha: str
    subject: str
    body: str
    group_key: str = field(default="other")
    type_label: str = field(default="")

    @property
    def short_sha(self) -> str:
        return self.sha[:7] if self.sha else ""

    @classmethod
    def from_event(cls, payload: Dict[str, Any]) -> "Commit":
        msg = (payload.get("message") or "").replace("\r", "").strip()
        subject, _, body = msg.partition("\n")
        return cls._classify(payload.get("id") or "", subject.strip(), body.strip())

    @classmethod
    def from_raw(cls, raw: str) -> "Commit":
        text = raw.replace("\r", "").strip()
        subject, _, body = text.partition("\n")
        return cls._classify("", subject.strip(), body.strip())

    @classmethod
    def _classify(cls, sha: str, subject: str, body: str) -> "Commit":
        m = SUBJECT_RE.match(subject)
        if m:
            t = m.group("type").lower()
            group_key = TYPE_TO_GROUP.get(t, "other")
            type_label = t
        elif subject.lower().startswith("merge "):
            group_key, type_label = "merge", "merge"
        else:
            group_key, type_label = "other", ""
        return cls(sha=sha, subject=subject, body=body, group_key=group_key, type_label=type_label)


def url(method: str) -> str:
    token = os.environ.get("TELEGRAM_TOKEN", "")
    return f"{API_BASE}{token}/{method}"


def env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        print(f"warn: {name}={raw!r} is not an int, falling back to {default}")
        return default


def load_commits() -> List[Commit]:
    raw_json = os.environ.get("COMMITS_JSON") or ""
    if raw_json:
        try:
            arr = json.loads(raw_json) or []
        except json.JSONDecodeError as e:
            print(f"warn: COMMITS_JSON parse failed ({e}); falling back to HEAD")
            arr = []
        commits = [Commit.from_event(c) for c in arr if isinstance(c, dict)]
        if commits:
            return commits
    head = os.environ.get("HEAD_COMMIT_MESSAGE") or ""
    if head.strip():
        return [Commit.from_raw(head)]
    return []


def group_commits(commits: List[Commit]) -> List[Tuple[str, str, List[Commit]]]:
    """Return [(group_key, label, [commits]), ...] in canonical group order."""
    buckets: Dict[str, List[Commit]] = {}
    for c in commits:
        buckets.setdefault(c.group_key, []).append(c)
    out: List[Tuple[str, str, List[Commit]]] = []
    for key, label, _types in GROUPS:
        if key in buckets:
            out.append((key, label, buckets[key]))
    return out


def html_escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def repo_url() -> str:
    return (os.environ.get("REPO_URL") or "").rstrip("/")


def sha_chip(full_sha: str) -> str:
    """Render `[short_sha]` — linked to GitHub when REPO_URL is configured."""
    if not full_sha:
        return ""
    short = full_sha[:7]
    chip = f"[{html_escape(short)}]"
    base = repo_url()
    if not base:
        return f"<code>{chip}</code>"
    return f'<a href="{base}/commit/{full_sha}">{chip}</a>'


def truncate_plain(s: str, budget: int) -> str:
    """Byte-slice truncation — only safe for plain text (no markup)."""
    if len(s) <= budget:
        return s
    return s[:budget].rstrip() + "\n…(truncated)"


def header_html(version_name: str, version_code: str) -> str:
    return (
        f"<b>Nnngram</b> <code>{html_escape(version_name)}</code> "
        f"<i>({html_escape(version_code)})</i>"
    )


def _assemble(header: str, sections: List[List[str]], dropped: int) -> str:
    """Glue header + populated sections + optional dropped-commits footer."""
    blocks = [header]
    body_blocks = [lines[0] + "\n" + "\n".join(lines[1:]) for lines in sections if len(lines) > 1]
    if body_blocks:
        blocks.append("\n\n".join(body_blocks))
    if dropped > 0:
        plural = "" if dropped == 1 else "s"
        blocks.append(f"<i>+ {dropped} more commit{plural} — see metadata channel</i>")
    return "\n\n".join(blocks)


def _shrink(header: str, sections: List[List[str]], budget: int) -> str:
    """Repeatedly drop the trailing entry until the rendered string fits the budget.

    Never slices inside an HTML tag (each entry is a self-contained string)."""
    dropped = 0
    while True:
        rendered = _assemble(header, sections, dropped)
        if len(rendered) <= budget:
            return rendered
        # Find the last group that still has entries and drop its last entry.
        for i in range(len(sections) - 1, -1, -1):
            if len(sections[i]) > 1:
                sections[i].pop()
                dropped += 1
                break
        else:
            # Nothing left to drop — header alone is the floor.
            return rendered


def render_caption(version_name: str, version_code: str, commits: List[Commit]) -> str:
    header = header_html(version_name, version_code)
    if not commits:
        return header + "\n\nNo commit metadata."
    sections: List[List[str]] = []
    for _key, label, items in group_commits(commits):
        lines = [label]
        for c in items:
            chip = sha_chip(c.sha)
            prefix = f"• {chip} " if chip else "• "
            lines.append(prefix + html_escape(c.subject))
        sections.append(lines)
    return _shrink(header, sections, CAPTION_BUDGET)


def render_full_changelog(version_name: str, version_code: str, commits: List[Commit]) -> str:
    header = header_html(version_name, version_code)
    if not commits:
        return header + "\n\nNo commit metadata."
    sections: List[List[str]] = []
    for _key, label, items in group_commits(commits):
        lines = [label]
        for c in items:
            chip = sha_chip(c.sha)
            entry = (f"{chip} " if chip else "") + html_escape(c.subject)
            if c.body:
                entry += "\n" + html_escape(c.body)
            lines.append(entry)
        sections.append(lines)
    dropped = 0
    while True:
        rendered = _assemble_changelog(header, sections, dropped)
        if len(rendered) <= MESSAGE_BUDGET:
            return rendered
        for i in range(len(sections) - 1, -1, -1):
            if len(sections[i]) > 1:
                sections[i].pop()
                dropped += 1
                break
        else:
            return rendered


def _assemble_changelog(header: str, sections: List[List[str]], dropped: int) -> str:
    blocks = [header]
    for lines in sections:
        if len(lines) <= 1:
            continue
        label, *entries = lines
        blocks.append(label + "\n\n" + "\n\n".join(entries))
    if dropped > 0:
        plural = "" if dropped == 1 else "s"
        blocks.append(f"<i>+ {dropped} more commit{plural} omitted</i>")
    return "\n\n".join(blocks)


def find_arm64_apk(apk_dir: str) -> Optional[str]:
    if not os.path.isdir(apk_dir):
        return None
    for root, _dirs, files in os.walk(apk_dir):
        for name in files:
            if "arm64" in name and name.endswith(".apk"):
                return os.path.join(root, name)
    return None


def prepare_apk_payload(apk_dir: str) -> Dict[str, Any]:
    src = find_arm64_apk(apk_dir)
    if not src:
        raise FileNotFoundError(f"no arm64 APK found under {apk_dir!r}")
    stamp = time.strftime("%Y%m%d%H%M%S", time.localtime())
    dst = os.path.join(apk_dir, f"{stamp}.apk")
    shutil.copyfile(src, dst)
    print(f"apk: {src} -> {dst}")
    return {"document": (os.path.basename(src), open(dst, "rb"))}


def post(method: str, data: Dict[str, Any], files: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    data = {**data, "parse_mode": "HTML"}
    print(f"-> {method} payload keys: {sorted(data.keys())}")
    resp = requests.post(url(method), data=data, files=files, timeout=600)
    resp.raise_for_status()
    body = resp.json()
    if not body.get("ok", False):
        raise RuntimeError(f"{method} returned not-ok: {body}")
    return body


def send_full_changelog(channel: int, text: str) -> int:
    body = post("sendMessage", {"chat_id": channel, "text": text, "disable_web_page_preview": "true"})
    msg_id = body["result"]["message_id"]
    print(f"changelog message_id={msg_id}")
    return msg_id


def send_apk(chat_id: int, caption: str, apk_dir: str) -> int:
    files = prepare_apk_payload(apk_dir)
    body = post("sendDocument", {"chat_id": chat_id, "caption": caption}, files=files)
    msg_id = body["result"]["message_id"]
    print(f"apk message_id={msg_id} (chat={chat_id})")
    return msg_id


def send_tracking(channel: int, version_name: str, version_code: str, start_id: int, changes_id: int) -> None:
    text = f"{version_name},{version_code},{start_id},{changes_id},false"
    # Tracking record is consumed by the in-app updater as plain CSV; bypass HTML.
    resp = requests.post(
        url("sendMessage"),
        data={"chat_id": channel, "text": text},
        timeout=60,
    )
    resp.raise_for_status()
    print(f"tracking: {text}")


def main() -> int:
    version_name = os.environ.get("VERSION_NAME", "Unknown")
    version_code = os.environ.get("VERSION_CODE", "Unknown")
    chat_id = env_int("CHAT_ID", 0)
    metadata_channel = env_int("METADATA_CHANNEL_ID", DEFAULT_METADATA_CHANNEL)
    apk_channel = env_int("APK_CHANNEL_ID", DEFAULT_APK_CHANNEL)
    apk_dir = os.environ.get("APK_DIR", "./apks")

    if not chat_id:
        print("error: CHAT_ID is required", file=sys.stderr)
        return 2
    if not os.environ.get("TELEGRAM_TOKEN"):
        print("error: TELEGRAM_TOKEN is required", file=sys.stderr)
        return 2

    commits = load_commits()
    print(f"version: {version_name} ({version_code})")
    print(f"commits: {len(commits)}")
    for c in commits:
        print(f"  - [{c.short_sha}] ({c.group_key}) {c.subject}")

    caption = render_caption(version_name, version_code, commits)
    changelog = render_full_changelog(version_name, version_code, commits)

    print("\n--- caption ---\n" + caption + "\n--- /caption ---\n")
    print("--- changelog ---\n" + changelog + "\n--- /changelog ---\n")

    print("[1/4] full changelog -> metadata channel")
    changes_id = send_full_changelog(metadata_channel, changelog)

    print("[2/4] APK -> main chat")
    send_apk(chat_id, caption, apk_dir)

    print("[3/4] APK -> APK channel")
    apk_msg_id = send_apk(apk_channel, caption, apk_dir)
    start_id = apk_msg_id - 1

    print("[4/4] tracking record -> metadata channel")
    send_tracking(metadata_channel, version_name, version_code, start_id, changes_id)

    print("done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

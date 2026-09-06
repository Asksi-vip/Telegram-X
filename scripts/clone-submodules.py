#!/usr/bin/env python3
"""Clone all submodules listed in .gitmodules files, recursively.

This repository stores its sources as a plain tree: .gitmodules lists the
submodule paths, but the index contains no gitlinks, so 'git submodule
update' cannot be used. For each declared path we clone the repository
shallowly; if the branch declared in .gitmodules does not exist on the
remote (e.g. webrtc uses 'telegram' and tgcalls uses 'development' instead
of 'main'), we fall back to the remote's default branch.
"""
import configparser
import os
import shutil
import subprocess
import sys


def has_files(path):
    if not os.path.exists(path):
        return False
    try:
        entries = os.listdir(path)
    except OSError:
        return False
    return any(name != ".git" for name in entries)


def clone(url, dest, branch=None):
    if os.path.exists(dest):
        shutil.rmtree(dest, ignore_errors=True)
    parent = os.path.dirname(dest)
    if parent:
        os.makedirs(parent, exist_ok=True)
    if branch:
        result = subprocess.run(
            ["git", "clone", "--depth", "1", "-b", branch, url, dest],
            capture_output=True, text=True)
        if result.returncode == 0:
            return True
        print(f"  branch '{branch}' not found for {url}, falling back to default branch")
        if os.path.exists(dest):
            shutil.rmtree(dest, ignore_errors=True)
    result = subprocess.run(
        ["git", "clone", "--depth", "1", url, dest],
        capture_output=True, text=True)
    if result.returncode != 0:
        print("  FAILED:", result.stderr.strip()[-500:], file=sys.stderr)
        return False
    return True


def process(base_dir):
    gitmodules = os.path.join(base_dir, ".gitmodules")
    if not os.path.exists(gitmodules):
        return
    config = configparser.ConfigParser()
    config.read(gitmodules)
    for section in config.sections():
        path = config.get(section, "path", fallback=None)
        url = config.get(section, "url", fallback=None)
        if not path or not url:
            continue
        branch = config.get(section, "branch", fallback=None)
        full_path = os.path.join(base_dir, path)
        if has_files(full_path):
            continue
        print(f"Cloning {url} [{branch or 'default'}] -> {full_path}")
        if clone(url, full_path, branch=branch):
            process(full_path)


if __name__ == "__main__":
    process(".")

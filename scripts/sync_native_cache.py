#!/usr/bin/env python3
"""Refresh and validate the local ARM64 cache from a successful source-built APK."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
ABI = "arm64-v8a"
CACHE = APP / "src/prebuiltNative/jniLibs" / ABI
MANIFEST = APP / "src/prebuiltNative/native-bundle.json"
MAIN_JNI_LIBS = APP / "src/main/jniLibs" / ABI
REQUIRED = {
    "libc++_shared.so", "libgmp.so", "libgmpxx.so", "libmpfr.so",
    "libslic3r.so", "libTKXDE.so", "libTKXDESTEP.so", "libTKXCAF.so",
    "libTKLCAF.so", "libTKCAF.so", "libTKCDF.so", "libTKV3d.so",
    "libTKMesh.so", "libTKXMesh.so", "libTKBO.so", "libTKPrim.so",
    "libTKHLR.so", "libTKShHealing.so", "libTKTopAlgo.so",
    "libTKGeomAlgo.so", "libTKGeomBase.so", "libTKBRep.so",
    "libTKG3d.so", "libTKG2d.so", "libTKMath.so", "libTKernel.so",
    "libTKService.so",
}
SOURCE_EXTENSIONS = {".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx", ".inc", ".cmake", ".txt"}
BINARY_EXTENSIONS = {".a", ".so"}


def app_version() -> tuple[str, int]:
    gradle = (APP / "build.gradle").read_text(encoding="utf-8")
    name = re.search(r'(?m)^\s*versionName\s+"([^"]+)"', gradle)
    code = re.search(r"(?m)^\s*versionCode\s+(\d+)", gradle)
    if not name or not code:
        raise RuntimeError("Could not resolve app version from app/build.gradle")
    return name.group(1), int(code.group(1))


def native_source_fingerprint() -> str:
    """Hash native source/config contents plus identity of imported binary inputs."""
    digest = hashlib.sha256()
    input_roots = [
        APP / "CMakeLists.txt",
        APP / "src/main/jni",
        APP / "src/main/jniImports",
        APP / "src/main/occt/include",
    ]
    records: list[tuple[str, Path, str]] = []
    for root in input_roots:
        if root.is_file():
            records.append((root.relative_to(APP).as_posix(), root, "source"))
            continue
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            if path.suffix.lower() in SOURCE_EXTENSIONS:
                records.append((path.relative_to(APP).as_posix(), path, "source"))
            elif path.suffix.lower() in BINARY_EXTENSIONS:
                records.append((path.relative_to(APP).as_posix(), path, "binary"))
    for relative, path, kind in sorted(records):
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        if kind == "source":
            digest.update(path.read_bytes())
        else:
            stat = path.stat()
            digest.update(f"{stat.st_size}:{stat.st_mtime_ns}".encode("ascii"))
        digest.update(b"\0")
    return digest.hexdigest()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


# The published 0.8.204 APK has byte-identical libraries to this committed
# manifest, but its source-built fingerprint predates the distributed checkout.
# Pin both the entire manifest and the distributed native input tree; never
# accept another stale cache or silently label an imported APK as source-built.
LEGACY_MANIFEST_SHA256 = "b6a9467875fa267e7826a47700c3c2cad2889fea4160efe5cc7a7c7271854da3"
LEGACY_NATIVE_FINGERPRINT = "34abc4d56968f382cf9643698c02144a1eccd76ab77b5a4bae586c4991fd0c81"
LEGACY_APK_SHA256 = "818eb539ef608de6e100b3c4dce38064d14dbb3252d783d3d38585c77ff70b1f"


def is_verified_legacy_import(bundle: dict, fingerprint: str) -> bool:
    return (
        sha256(MANIFEST) == LEGACY_MANIFEST_SHA256
        and fingerprint == LEGACY_NATIVE_FINGERPRINT
        and bundle.get("sourceApkSha256") == LEGACY_APK_SHA256
    )


def validate_bundle() -> dict:
    if not MANIFEST.is_file():
        raise RuntimeError("No source-built native cache manifest exists. Run a full source-native build first.")
    bundle = json.loads(MANIFEST.read_text(encoding="utf-8"))
    version_name, version_code = app_version()
    if bundle.get("appVersion") != version_name or bundle.get("versionCode") != version_code:
        raise RuntimeError(
            f"Native cache is for {bundle.get('appVersion')} ({bundle.get('versionCode')}), "
            f"but this app is {version_name} ({version_code}). Run the grouped full source build."
        )
    current_fingerprint = native_source_fingerprint()
    if bundle.get("nativeSourceFingerprint") != current_fingerprint and not is_verified_legacy_import(bundle, current_fingerprint):
        raise RuntimeError("Native source or imported dependency inputs changed since the cache was built. Rebuild native code from source.")

    declared: dict[str, dict] = {}
    for item in bundle.get("files", []):
        name = item.get("name")
        if not isinstance(name, str) or Path(name).name != name or not name.endswith(".so"):
            raise RuntimeError("Native cache manifest contains an invalid library name.")
        if name in declared:
            raise RuntimeError(f"Native cache manifest contains duplicate library {name}.")
        declared[name] = item
    present = {path.name: path for path in CACHE.glob("*.so")} if CACHE.is_dir() else {}
    if set(present) != set(declared):
        raise RuntimeError(
            f"Native cache contents differ from its manifest. Missing: {sorted(set(declared) - set(present))}; "
            f"extra: {sorted(set(present) - set(declared))}."
        )
    missing_required = REQUIRED - set(declared) - {p.name for p in MAIN_JNI_LIBS.glob("*.so")}
    if missing_required:
        raise RuntimeError(f"Native cache is missing required runtime libraries: {sorted(missing_required)}")
    for name, path in present.items():
        item = declared[name]
        if path.stat().st_size != item.get("size") or sha256(path) != item.get("sha256"):
            raise RuntimeError(f"Native cache library was changed or truncated: {name}")
        with path.open("rb") as stream:
            if stream.read(4) != b"\x7fELF":
                raise RuntimeError(f"Native cache entry is not an ELF library: {name}")
    if bundle.get("nativeSourceFingerprint") != current_fingerprint:
        print("Native cache provenance: pinned published 0.8.204 APK import; "
              "manifest, distributed native inputs and every library verified (not a new source-native build).")
    return bundle


def sync_from_apk(apk: Path) -> dict:
    apk = apk.resolve()
    if not apk.is_file():
        raise RuntimeError(f"Source-built APK does not exist: {apk}")
    version_name, version_code = app_version()
    fingerprint = native_source_fingerprint()
    stage = CACHE.parent / f"{ABI}.stage-{time.time_ns()}"
    stage.mkdir(parents=True)
    main_names = {path.name for path in MAIN_JNI_LIBS.glob("*.so")}
    try:
        with zipfile.ZipFile(apk) as archive:
            entries = sorted(name for name in archive.namelist() if name.startswith(f"lib/{ABI}/") and name.endswith(".so"))
            if not entries:
                raise RuntimeError(f"APK contains no {ABI} native libraries: {apk}")
            for entry in entries:
                name = Path(entry).name
                if name in main_names:
                    continue
                payload = archive.read(entry)
                if len(payload) < 4 or payload[:4] != b"\x7fELF":
                    raise RuntimeError(f"APK entry is not a valid ELF library: {entry}")
                (stage / name).write_bytes(payload)
        staged_names = {path.name for path in stage.glob("*.so")}
        missing_required = REQUIRED - staged_names - main_names
        if missing_required:
            raise RuntimeError(f"Source-built APK lacks required libraries: {sorted(missing_required)}")

        CACHE.parent.mkdir(parents=True, exist_ok=True)
        if CACHE.exists():
            for path in CACHE.glob("*.so"):
                path.unlink()
        else:
            CACHE.mkdir(parents=True)
        for path in stage.glob("*.so"):
            shutil.copy2(path, CACHE / path.name)
        files = [
            {"name": path.name, "size": path.stat().st_size, "sha256": sha256(path)}
            for path in sorted(CACHE.glob("*.so"), key=lambda item: item.name)
        ]
        manifest = {
            "formatVersion": 1,
            "abi": ABI,
            "appVersion": version_name,
            "versionCode": version_code,
            "nativeSourceFingerprint": fingerprint,
            "sourceApk": str(apk.relative_to(ROOT)) if apk.is_relative_to(ROOT) else str(apk),
            "sourceApkSha256": sha256(apk),
            "files": files,
        }
        MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        return validate_bundle()
    finally:
        if stage.exists():
            shutil.rmtree(stage)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("sync", "check"))
    parser.add_argument("--apk", type=Path, help="Source-built Debug APK used to refresh the native cache")
    args = parser.parse_args()
    try:
        if args.action == "sync":
            if args.apk is None:
                raise RuntimeError("sync requires --apk")
            bundle = sync_from_apk(args.apk if args.apk.is_absolute() else ROOT / args.apk)
            print(f"Native cache refreshed from {bundle['sourceApk']}: {len(bundle['files'])} ARM64 libraries, source fingerprint verified.")
        else:
            bundle = validate_bundle()
            print(f"Native cache verified for OrkanSlicer {bundle['appVersion']} ({len(bundle['files'])} ARM64 libraries).")
        return 0
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
        print(f"Native cache check failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

"""Regression checks for the narrowly pinned published-APK import exception."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("native_cache", Path(__file__).with_name("sync_native_cache.py"))
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class LegacyImportTest(unittest.TestCase):
    def test_exact_published_provenance_is_accepted(self):
        with patch.object(cache, "sha256", return_value=cache.LEGACY_MANIFEST_SHA256):
            self.assertTrue(cache.is_verified_legacy_import(
                {"sourceApkSha256": cache.LEGACY_APK_SHA256}, cache.LEGACY_NATIVE_FINGERPRINT))

    def test_changed_native_inputs_are_rejected(self):
        with patch.object(cache, "sha256", return_value=cache.LEGACY_MANIFEST_SHA256):
            self.assertFalse(cache.is_verified_legacy_import(
                {"sourceApkSha256": cache.LEGACY_APK_SHA256}, "modified"))

    def test_changed_manifest_is_rejected(self):
        with patch.object(cache, "sha256", return_value="modified"):
            self.assertFalse(cache.is_verified_legacy_import(
                {"sourceApkSha256": cache.LEGACY_APK_SHA256}, cache.LEGACY_NATIVE_FINGERPRINT))

    def test_changed_apk_provenance_is_rejected(self):
        with patch.object(cache, "sha256", return_value=cache.LEGACY_MANIFEST_SHA256):
            self.assertFalse(cache.is_verified_legacy_import(
                {"sourceApkSha256": "modified"}, cache.LEGACY_NATIVE_FINGERPRINT))


if __name__ == "__main__":
    unittest.main()

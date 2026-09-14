"""只复制紧凑回放到临时目录，原始证据保持只读。"""
import hashlib
import json
from pathlib import Path
import tempfile
import sys
import unittest
import zipfile
import l6_capacity_calculator as calculator


class CapacityReplayTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        source = Path(SOURCE_ROOT)
        for name in [BUNDLE, MANIFEST]:
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((source/name).read_bytes())

    def mutate(self, name, transform, rehash=False):
        bundle = self.root / BUNDLE
        with zipfile.ZipFile(bundle) as archive:
            items = {n: archive.read(n) for n in archive.namelist()}
        items[name] = transform(items[name])
        if rehash:
            manifest = json.loads(items['raw-manifest.json'])
            for item in manifest['files']:
                if item['path'] == name:
                    item['sha256'] = hashlib.sha256(items[name]).hexdigest()
            items['raw-manifest.json'] = json.dumps(manifest).encode()
        with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as archive:
            for n, data in items.items():
                archive.writestr(n, data)

    def test_corrupted_raw_sha_is_rejected(self):
        self.mutate('resources.ndjson', lambda b: b+b'\n')
        with self.assertRaisesRegex(ValueError, 'SHA mismatch'):
            calculator.calculate(self.root, MANIFEST, BUNDLE)

    def test_rehashed_missing_sample_is_rejected(self):
        self.mutate('resources.ndjson', lambda b: b'\n'.join(b.splitlines()[:-1]), True)
        with self.assertRaisesRegex(ValueError, 'complete calibration'):
            calculator.calculate(self.root, MANIFEST, BUNDLE)

    def test_unaccepted_source_is_rejected(self):
        def change(b):
            data = json.loads(b); data['storageCalibrationAccepted'] = False
            return json.dumps(data).encode()
        self.mutate('accepted-summary.json', change)
        with self.assertRaisesRegex(ValueError, 'accepted storage source'):
            calculator.calculate(self.root, MANIFEST, BUNDLE)

    def test_manifest_drift_is_rejected(self):
        path = self.root/MANIFEST
        path.write_bytes(path.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError, 'manifest identity'):
            calculator.calculate(self.root, MANIFEST, BUNDLE)


if __name__ == '__main__':
    SOURCE_ROOT, MANIFEST, BUNDLE = sys.argv[1:4]
    unittest.main(argv=[sys.argv[0]])

#!/usr/bin/env python3
"""Static inventory only; this is NOT an APK build or an Android test."""
from pathlib import Path
import zipfile
root=Path(__file__).resolve().parent.parent
src=root/'app/src/main/java/com/panahoops/next'
required=['MainActivity.java','FitService.java','FitCounter.kt','FitDistance.kt','ResetPolicy.kt','ResetManager.java','TrainingStore.java','CameraPerformance.java','VisionActivity.java','SocialActivity.java','MatchActivity.java']
for name in required:
    assert (src/name).is_file(), name
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'android:name=".FitService"' in manifest
assert 'PanaHoops FIT' in manifest
assert 'FOREGROUND_SERVICE_HEALTH' in manifest
assert 'ACTIVITY_RECOGNITION' in manifest
build=(root/'app/build.gradle').read_text()
assert 'versionCode 311' in build
assert 'com.panahoops.courtside' in build
assert (root/'app/src/main/assets/court_detector.tflite').stat().st_size>100_000
assert (root/'gradle/wrapper/gradle-wrapper.jar').stat().st_size > 10000
assert (root/'.github/workflows/android.yml').is_file()
print(f'OK: inventario fuente: {len(required)} componentes y archivo de modelo. No verifica compilación.')

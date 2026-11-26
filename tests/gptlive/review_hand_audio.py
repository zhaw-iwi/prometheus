"""Transcribe complete received recordings locally; never assigns an acceptance verdict."""
import argparse
import hashlib
import importlib.metadata
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', nargs='+', type=Path)
    parser.add_argument('--download-model', action='store_true', help='Allow downloading the public base.en weights; no OpenAI calls')
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT / 'target/hand-audio-tools'))
    os.environ['HF_HUB_DISABLE_TELEMETRY'] = '1'
    os.environ['HF_HUB_DISABLE_SYMLINKS_WARNING'] = '1'
    from faster_whisper import WhisperModel
    model = WhisperModel('base.en', device='cpu', compute_type='int8', cpu_threads=2,
                         download_root=str(ROOT / 'target/hand-audio-models'), local_files_only=not args.download_model)
    weights = {str(p.relative_to(ROOT / 'target/hand-audio-models')): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in (ROOT / 'target/hand-audio-models').rglob('model.bin')}
    versions = {name: importlib.metadata.version(name) for name in ('faster-whisper', 'ctranslate2', 'av')}
    for directory in args.artifacts:
        report_path = directory / 'journey.json'
        if not report_path.exists():
            report_path = directory / 'forum.json'
        report = json.loads(report_path.read_text(encoding='utf-8'))
        files = []
        for name in report.get('audioFiles', []):
            if Path(name).name != name:
                raise ValueError('Audio must be inside its artifact directory')
            file = directory / name
            segments, info = model.transcribe(str(file), language='en', beam_size=5,
                                              vad_filter=True, condition_on_previous_text=False)
            rows = [dict(start=s.start, end=s.end, text=s.text, logprob=s.avg_logprob) for s in segments]
            files.append(dict(file=name, sha256=hashlib.sha256(file.read_bytes()).hexdigest(),
                              duration=info.duration, segments=rows))
        result = dict(method='local faster-whisper base.en int8; complete received audio; no provider upload or grading',
                      physicalAudibility='NOT_RUN', versions=versions, modelWeights=weights, files=files)
        (directory / 'audio-review.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(directory.name, ' '.join(s['text'] for f in files for s in f['segments']), flush=True)


if __name__ == '__main__':
    main()

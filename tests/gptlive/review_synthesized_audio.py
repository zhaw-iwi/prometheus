"""Optional local ASR evidence for retained Speech PCM; never a semantic acceptance verdict.

Install faster-whisper==1.2.1 and av==16.1.0 separately (not application dependencies). The small.en
model downloads once; transcription runs locally on CPU without another paid API.
"""
import argparse
import hashlib
import importlib.metadata
import io
import json
from pathlib import Path
import wave


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', nargs='?')
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    from faster_whisper import WhisperModel
    model = WhisperModel('small.en', device='cpu', compute_type='int8', cpu_threads=4,
                         download_root=str(Path('target/es08-audio-review/models').resolve()))
    if args.prepare_only:
        print('Local small.en audio reviewer ready.', flush=True)
        return
    if not args.artifact:
        parser.error('An acceptance artifact directory is required')
    root = Path(args.artifact).resolve()
    journey_path = root / 'journey.json'
    journey = json.loads(journey_path.read_text(encoding='utf-8'))
    result = {'status': 'PENDING_SEMANTIC_REVIEW', 'method': 'local faster-whisper small.en / CPU int8 / beam 5 / no prompt',
              'version': importlib.metadata.version('faster-whisper'),
              'journeySha256': hashlib.sha256(journey_path.read_bytes()).hexdigest(), 'clips': [],
              'limitations': 'ASR can err. Compare meaning manually; this is not physical audibility evidence.'}
    for clip in journey.get('synthesizedAudio', []):
        file = (root / clip['file']).resolve()
        if not file.is_relative_to(root):
            raise ValueError('Audio path escapes the artifact directory')
        pcm = file.read_bytes()
        if hashlib.sha256(pcm).hexdigest() != clip['sha256'] or not 0 < len(pcm) <= 2160000 or len(pcm) % 2:
            raise ValueError('Audio artifact identity or size mismatch')
        stream = io.BytesIO()
        with wave.open(stream, 'wb') as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            wav.writeframes(pcm)
        stream.seek(0)
        segments, info = model.transcribe(stream, language='en', beam_size=5, condition_on_previous_text=False)
        transcript = ' '.join(segment.text.strip() for segment in segments)
        entry = {**clip, 'transcript': transcript, 'decodedSeconds': info.duration}
        result['clips'].append(entry)
        (root / 'audio-review.json').write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
        print(json.dumps({'file': clip['file'], 'expected': clip['text'], 'transcript': transcript}, ensure_ascii=True), flush=True)


if __name__ == '__main__':
    main()

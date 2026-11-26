"""Local hand-acceptance stimuli; no OpenAI call, credential or bill."""
import hashlib
import argparse
import json
import wave
from pathlib import Path
from win32com.client import constants, gencache

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--forum', action='store_true', help='Prepare the separate forum playbook clips')
forum = parser.parse_args().forum
output = root / ('target/forum-audio' if forum else 'target/hand-interaction-audio')
cases = root / ('tests/gptlive/forum-cases.json' if forum else 'tests/gptlive/hand-cases.json')
texts = json.loads(cases.read_text(encoding='utf-8'))
output.mkdir(parents=True, exist_ok=True)
voice = gencache.EnsureDispatch('SAPI.SpVoice')
voices = [v for v in voice.GetVoices() if '409' in v.GetAttribute('Language').split(';')]
if not voices:
    raise RuntimeError('A local Windows en-US speech voice is required')
voice.Voice = next((v for v in voices if v.GetAttribute('Gender') == 'Male'), voices[0])
for name, text in texts.items():
    if not name.isalpha() or not name.islower():
        raise ValueError('Invalid stimulus name')
    stream = gencache.EnsureDispatch('SAPI.SpFileStream')
    stream.Format.Type = constants.SAFT24kHz16BitMono
    stream.Open(str(output / (name + '.wav')), constants.SSFMCreateForWrite)
    try:
        voice.AudioOutputStream = stream
        voice.Speak(text, 0)
    finally:
        stream.Close()
manifest = {'providerCalls': 0, 'sampleRate': 24000,
            'voice': voice.Voice.GetDescription(), 'clips': {}}
for name, text in texts.items():
    with wave.open(str(output / (name + '.wav')), 'rb') as wav:
        assert (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) == (1, 2, 24000)
        pcm = wav.readframes(wav.getnframes())
    assert 0 < len(pcm) <= 2160000, 'Stimulus exceeds 45 seconds'
    (output / (name + '.pcm')).write_bytes(pcm)
    manifest['clips'][name] = {'file': name + '.pcm', 'text': text, 'bytes': len(pcm),
                              'sha256': hashlib.sha256(pcm).hexdigest(), 'seconds': len(pcm) / 48000}
(output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print('Prepared', len(texts), 'local clips; provider calls: 0')

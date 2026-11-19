"""Generate local synthetic visitor speech; no provider access or billing."""
import hashlib, json, subprocess, wave
from pathlib import Path

root=Path(__file__).resolve().parents[2]
output=root/'target/commanding-audio'
subprocess.run(['powershell','-NoProfile','-File',str(Path(__file__).with_suffix('.ps1')),
                '-OutputDirectory',str(output)],check=True)
texts=json.loads(Path(__file__).with_name('commanding-cases.json').read_text(encoding='utf-8'))
manifest={'providerCalls':0,'sampleRate':24000,'voice':(output/'voice.txt').read_text(encoding='utf-8-sig').strip(),'clips':{}}
for name,text in texts.items():
    with wave.open(str(output/(name+'.wav')),'rb') as wav:
        assert (wav.getnchannels(),wav.getsampwidth(),wav.getframerate())==(1,2,24000)
        pcm=wav.readframes(wav.getnframes())
    assert 0<len(pcm)<=7200000
    (output/(name+'.pcm')).write_bytes(pcm)
    manifest['clips'][name]={'file':name+'.pcm','text':text,'bytes':len(pcm),'sha256':hashlib.sha256(pcm).hexdigest(),'seconds':len(pcm)/48000}
(output/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print('Prepared',len(texts),'local clips; provider calls: 0')

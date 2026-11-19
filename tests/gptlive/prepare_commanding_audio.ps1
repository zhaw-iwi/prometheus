param([string]$OutputDirectory = 'target/commanding-audio')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$clips = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'commanding-cases.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$null = New-Item -ItemType Directory -Force -Path $OutputDirectory
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Male, [System.Speech.Synthesis.VoiceAge]::Adult, 0, [Globalization.CultureInfo]::GetCultureInfo('en-US'))
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(24000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    foreach ($clip in $clips.PSObject.Properties) {
        if ($clip.Name -notmatch '^[a-z]+$') { throw 'Invalid stimulus name' }
        $file = Join-Path ([IO.Path]::GetFullPath($OutputDirectory)) ($clip.Name + '.wav')
        $synth.SetOutputToWaveFile($file, $format)
        $synth.Speak([string]$clip.Value)
        $synth.SetOutputToNull()
    }
    $synth.Voice.Name | Set-Content -LiteralPath (Join-Path $OutputDirectory 'voice.txt') -Encoding UTF8
} finally { $synth.Dispose() }

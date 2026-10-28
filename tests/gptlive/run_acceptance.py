"""Isolated acceptance; offline unless --paid-core is explicitly selected. Requires local MySQL administration and Python pymysql.

Always provisions a random schema and restricted user. Never targets the configured
application schema. Test provider endpoints exist solely on the test classpath.
"""
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen
import argparse
import os
import re
import secrets
import shutil
import socket
import subprocess
import time
import uuid
import pymysql


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-tests', default='LiveCockpitSmokeIntegrationTest', help='Comma-separated Maven test names, all, or none')
    parser.add_argument('--browser', action='store_true', help='Run real-app smoke enabled and disabled, then focused UI regressions')
    parser.add_argument('--browser-grep', help='Optional Playwright title filter for focused reruns')
    parser.add_argument('--browser-mode', choices=['both', 'enabled', 'disabled'], default='both', help='Select fixture modes for focused browser checks')
    parser.add_argument('--live-only', action='store_true', help='With --browser, omit already verified legacy browser regression specs')
    parser.add_argument('--database-properties', default='src/main/resources/application-test.properties',
                        help='Local MySQL administration properties; only loopback hosts are accepted')
    parser.add_argument('--paid-core', choices=['live', 'face', 'social', 'space', 'task', 'space-task', 'rps', 'user-guess', 'agent-guess', 'care-guess', 'coordination', 'coordination-text'], help='Explicitly paid real OpenAI core journey; local disposable database only')
    parser.add_argument('--coordination-scenario', choices=['baseline', 'lifecycle', 'recognition', 'revision', 'capture', 'delivery', 'multimodal', 'dialogue'], default='baseline')
    args = parser.parse_args()
    if args.paid_core and args.browser:
        parser.error('--paid-core and mocked --browser acceptance must run separately')
    if args.browser_mode != 'both' and not args.browser:
        parser.error('--browser-mode requires --browser')
    if args.coordination_scenario != 'baseline' and args.paid_core not in ('coordination', 'coordination-text'):
        parser.error('--coordination-scenario requires a paid Coordination journey')
    root = Path(__file__).resolve().parents[2]
    os.chdir(root)
    provider_props = {}
    if args.paid_core:
        # Preflight before provisioning or billing: a worktree may lack ignored local config.
        for name in ('openai.properties', 'application.properties'):
            config = root / 'src/main/resources' / name
            if config.exists():
                for line in config.read_text(encoding='utf-8-sig').splitlines():
                    if '=' in line and not line.lstrip().startswith('#'):
                        key, value = line.split('=', 1)
                        provider_props[key.strip()] = value.strip()
        key = os.environ.get('OPENAI_API_KEY') or provider_props.get('openai.key')
        model = os.environ.get('OPENAI_MODEL') or provider_props.get('openai.model', '')
        if not key or key.startswith('${') or not re.fullmatch(r'[a-zA-Z0-9._:-]{1,128}', model):
            raise SystemExit('Paid core acceptance requires an API key and configured openai.model (or OPENAI_MODEL).')
    props = {}
    local = root / args.database_properties
    if local.exists():
        for line in local.read_text(encoding='utf-8-sig').splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                props[key.strip()] = value.strip()
    url = urlparse(os.environ.get('GPTLIVE_MYSQL_ADMIN_URL', props.get('spring.datasource.url', '')).removeprefix('jdbc:'))
    if url.hostname not in ('localhost', '127.0.0.1', '::1'):
        raise SystemExit('Local MySQL administration URL required; no remote database is permitted.')
    suffix = uuid.uuid4().hex[:10]
    schema, username, password = 'prometheus_gptlive_' + suffix, 'gptlive_' + suffix, secrets.token_hex(24)
    assert re.fullmatch(r'prometheus_gptlive_[0-9a-f]{10}', schema)
    assert re.fullmatch(r'gptlive_[0-9a-f]{10}', username)
    artifacts = root / 'target' / ('gptlive-acceptance-' + suffix)
    artifacts.mkdir(parents=True)
    database = pymysql.connect(host=url.hostname, port=url.port or 3306,
        user=os.environ.get('GPTLIVE_MYSQL_ADMIN_USER', props.get('spring.datasource.username')),
        password=os.environ.get('GPTLIVE_MYSQL_ADMIN_PASSWORD', props.get('spring.datasource.password')), autocommit=True)
    created_schema = created_user = False
    app = None
    env = dict(os.environ)
    env.update(SPRING_DATASOURCE_URL=f'jdbc:mysql://{url.hostname}:{url.port or 3306}/{schema}',
        SPRING_DATASOURCE_USERNAME=username, SPRING_DATASOURCE_PASSWORD=password,
        SPRING_JPA_HIBERNATE_DDL_AUTO='update', OPENAI_KEY='offline-test', OPENAI_URL='http://127.0.0.1:9/no-provider',
        PROMETHEUS_SPEECH_URL='http://127.0.0.1:9/no-provider', PROMETHEUS_LIVE_SESSIONS_URL='http://127.0.0.1:9/no-provider',
        PROMETHEUS_RUNTIME_TICK_ENABLED='false', SERVER_SHUTDOWN='immediate', PROMETHEUS_ADMIN_TOKEN=secrets.token_hex(20),
        SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='10',
        PROMETHEUS_SKIP_WEBSERVER='true')
    maven = ['mvnw.cmd'] if os.name == 'nt' else ['bash', './mvnw']
    npm = 'npx.cmd' if os.name == 'nt' else 'npx'

    def run(command, name):
        with (artifacts / (name + '.log')).open('w', encoding='utf-8') as log:
            child = subprocess.Popen(command, env=env, stdout=log, stderr=subprocess.STDOUT,
                                     start_new_session=os.name != 'nt')
            try:
                code = child.wait(timeout=420 if name == 'paid-journey' else 1800)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                # Stop the owned browser/JVM descendants as well as their launcher.
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(child.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
                else:
                    import signal
                    os.killpg(child.pid, signal.SIGKILL)
                child.wait(timeout=15)
                raise
        print(f'{name}: exit {code}', flush=True)
        if code:
            raise SystemExit(code)

    def stop():
        nonlocal app
        if app and app.poll() is None:
            if os.name == 'nt':
                # Some Windows Java launchers spawn a child JVM; terminate the owned tree
                # before the launcher exits, otherwise that child can outlive schema cleanup.
                subprocess.run(['taskkill', '/PID', str(app.pid), '/T', '/F'],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
            else:
                app.terminate()
            try:
                app.wait(timeout=15)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait(timeout=5)
        app = None

    try:
        with database.cursor() as cursor:
            cursor.execute('CREATE DATABASE `' + schema + '` CHARACTER SET utf8mb4')
            created_schema = True
            cursor.execute('CREATE USER %s@%s IDENTIFIED BY %s', (username, 'localhost', password))
            created_user = True
            cursor.execute('GRANT ALL PRIVILEGES ON `' + schema + '`.* TO %s@%s', (username, 'localhost'))
        print('Disposable schema: ' + schema + '; artifacts: ' + str(artifacts), flush=True)
        if args.java_tests != 'none':
            selection = [] if args.java_tests == 'all' else ['-Dtest=' + args.java_tests]
            # Every cached Spring context owns a pool. Bound aggregate local connections
            # for full-suite runs without changing any application's production pool size.
            run([*maven, '-q', '-Dspring.test.context.cache.maxSize=4', *selection, 'test'], 'java')
        if args.paid_core:
            key = os.environ.get('OPENAI_API_KEY') or provider_props.get('openai.key')
            env.update(OPENAI_KEY=key.removeprefix('Bearer '), OPENAI_API_KEY=key.removeprefix('Bearer '),
                OPENAI_MODEL=model,
                OPENAI_OPENAIVSAZUREOPENAI='openai', OPENAI_URL='https://api.openai.com/v1/chat/completions',
                PROMETHEUS_SPEECH_URL='https://api.openai.com/v1/audio/speech',
                PROMETHEUS_LIVE_SESSIONS_URL='https://api.openai.com/v1/live/sessions',
                PROMETHEUS_LIVE_ENABLED='true', PROMETHEUS_LIVE_REQUEST_TIMEOUT_MS='30000')
            run([*maven, '-q', '-DskipTests', 'test-compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(artifacts / 'classpath.txt')], 'compile')
            fixture_root = artifacts / 'provider-classes'
            fixtures = fixture_root / 'fixtures' / 'provider'
            fixtures.mkdir(parents=True)
            for source in (root / 'target/test-classes/fixtures/provider').glob('*.class'):
                shutil.copy2(source, fixtures / source.name)
            classpath = os.pathsep.join([str(root / 'target/classes'), str(fixture_root), (artifacts / 'classpath.txt').read_text().strip()])
            env['PROMETHEUS_PROVIDER_ARTIFACT'] = str(artifacts / 'provider.jsonl')
            with socket.socket() as port_socket:
                port_socket.bind(('127.0.0.1', 0))
                port = port_socket.getsockname()[1]
            env.update(SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(port), PROMETHEUS_BASE_URL=f'http://127.0.0.1:{port}')
            with (artifacts / 'app-paid.log').open('w', encoding='utf-8') as log:
                app = subprocess.Popen(['java', '-cp', classpath, 'fixtures.provider.ProviderCaptureApplication'], env=env,
                    stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
                for attempt in range(180):
                    if app.poll() is not None:
                        raise RuntimeError('Isolated real-provider app exited; inspect artifact log.')
                    try:
                        with urlopen(env['PROMETHEUS_BASE_URL'] + '/valerian/', timeout=1) as response:
                            if response.status == 200:
                                break
                    except Exception:
                        time.sleep(0.5)
                else:
                    raise RuntimeError('Isolated real-provider app startup timed out.')
                journey = 'coordination_text_journey.mjs' if args.paid_core == 'coordination-text' else 'core_backend_journey.mjs'
                run(['node', 'tests/gptlive/' + journey, '--allow-paid-provider', str(artifacts / 'journey.json'),
                    '--agent=' + args.paid_core, '--scenario=' + args.coordination_scenario], 'paid-journey')
                stop()
        if args.browser:
            run([*maven, '-q', '-DskipTests', 'test-compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(artifacts / 'classpath.txt')], 'compile')
            # Isolate just the explicit fixtures; unrelated test controllers never enter component scanning.
            fixtures = artifacts / 'fixture-classes' / 'fixtures' / 'gptlive'
            fixtures.mkdir(parents=True)
            for source in (root / 'target/test-classes/fixtures/gptlive').glob('*.class'):
                shutil.copy2(source, fixtures / source.name)
            classpath = os.pathsep.join([str(root / 'target/classes'), str(artifacts / 'fixture-classes'),
                (artifacts / 'classpath.txt').read_text().strip()])
            for enabled in (('true', 'false') if args.browser_mode == 'both' else ('true',) if args.browser_mode == 'enabled' else ('false',)):
                with socket.socket() as port_socket:
                    port_socket.bind(('127.0.0.1', 0))
                    port = port_socket.getsockname()[1]
                env.update(SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(port), PROMETHEUS_BASE_URL=f'http://127.0.0.1:{port}',
                    PROMETHEUS_LIVE_ENABLED=enabled, PROMETHEUS_LIVE_EXPECT_ENABLED=enabled)
                with (artifacts / ('app-' + enabled + '.log')).open('w', encoding='utf-8') as log:
                    app = subprocess.Popen(['java', '-cp', classpath, 'fixtures.gptlive.LiveSmokeApplication',
                        '--prometheus.core.social-space.reception=true'], env=env,
                        stdout=log, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
                    for attempt in range(180):
                        if app.poll() is not None:
                            raise RuntimeError('Isolated app exited; inspect artifact log.')
                        try:
                            with urlopen(env['PROMETHEUS_BASE_URL'] + '/valerian/', timeout=1) as response:
                                if response.status == 200:
                                    break
                        except Exception:
                            time.sleep(0.5)
                    else:
                        raise RuntimeError('Isolated app startup timed out.')
                    specs = ['tests/playwright/valerian-gptlive-smoke.spec.mjs', 'tests/playwright/valerian-state-view.spec.mjs']
                    if enabled == 'true':
                        specs += ['tests/playwright/valerian-gptlive.spec.mjs']
                        if not args.live_only:
                            specs += ['tests/playwright/valerian-lifecycle.spec.mjs', 'tests/playwright/valerian-transcription.spec.mjs',
                                'tests/playwright/valerian-column-expansion.spec.mjs', 'tests/playwright/valerian-classroom-smoke.spec.mjs']
                    run([npm, 'playwright', 'test', '--config=playwright.config.mjs', *specs,
                        '--output=' + str(artifacts / ('browser-' + enabled)),
                        *(['--grep', args.browser_grep] if args.browser_grep else [])], 'browser-' + enabled)
                    stop()
    finally:
        stop()
        with database.cursor() as cursor:
            if created_schema:
                cursor.execute('DROP DATABASE `' + schema + '`')
            if created_user:
                cursor.execute('DROP USER %s@%s', (username, 'localhost'))
        database.close()
        print('Owned app stopped; disposable schema/account removed.', flush=True)


if __name__ == '__main__':
    main()

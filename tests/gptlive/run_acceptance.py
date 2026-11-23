"""Isolated acceptance. Offline unless --paid-core or --paid-gala is selected. Requires local MySQL administration.

Always provisions a random schema and restricted user. Never targets the configured
application schema. Test provider endpoints exist solely on the test classpath.
"""
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import time
import uuid
import pymysql
from paid_budget import reserve
import commanding_preflight
from process_guard import run_owned


def escalation_cases(root):
    fixture = json.loads((root / 'src/test/resources/coordination/escalation-cases.json').read_text(encoding='utf-8'))
    cases = fixture['cases']
    if fixture.get('baselineIsAcceptance') is not False or len({case['id'] for case in cases}) != len(cases):
        raise ValueError('Escalation fixtures must have unique cases and cannot claim baseline acceptance')
    for case in cases:
        if not case['input'] or not case['expected'] or not re.fullmatch(r'ES-\d{2}', case['milestone']):
            raise ValueError('Invalid escalation regression case')
    return fixture


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java-tests', default='LiveCockpitSmokeIntegrationTest', help='Comma-separated Maven test names, all, or none')
    parser.add_argument('--browser', action='store_true', help='Run real-app smoke enabled and disabled, then focused UI regressions')
    parser.add_argument('--embodiment', action='store_true', help='Robot browser regression or real-provider persisted-plan rendering smoke')
    parser.add_argument('--browser-grep', help='Optional Playwright title filter for focused reruns')
    parser.add_argument('--browser-mode', choices=['both', 'enabled', 'disabled'], default='both', help='Select fixture modes for focused browser checks')
    parser.add_argument('--live-only', action='store_true', help='With --browser, omit already verified legacy browser regression specs')
    parser.add_argument('--paid-gala', action='store_true', help='Opt in to real OpenAI audio/control journeys on the disposable local database')
    parser.add_argument('--connection-only', action='store_true', help='With --paid-gala, probe greeting/question/context update without games')
    parser.add_argument('--game', choices=['all', 'RPS', 'GUESS'], default='all', help='Paid journey selection; each game includes two rounds/replay')
    parser.add_argument('--classroom-capacity', action='store_true', help='Dedicated offline six-client minute with MySQL Questions measurement')
    parser.add_argument('--database-properties', default='src/main/resources/application-test.properties',
                        help='Local MySQL administration properties; only loopback hosts are accepted')
    parser.add_argument('--paid-core', choices=['live', 'face', 'social', 'space', 'task', 'space-task', 'rps', 'user-guess', 'agent-guess', 'care-guess', 'coordination', 'coordination-text', 'repair-baseline', 'repair-live', 'repair-continuous', 'repair-text', *commanding_preflight.MODES], help='Explicitly paid real OpenAI core journey; local disposable database only')
    parser.add_argument('--coordination-scenario', choices=['baseline', 'lifecycle', 'recognition', 'revision', 'capture', 'delivery', 'multimodal', 'dialogue', 'interaction', 'escalation-recovery', 'escalation-dialogue', 'escalation-social'], default='baseline')
    commanding_authorization = parser.add_mutually_exclusive_group()
    commanding_authorization.add_argument('--commanding-retests', action='store_true', help='Use the separately authorized two-attempt/$10 envelope for HP-02 and HP-03')
    commanding_authorization.add_argument('--commanding-hp3-retest', action='store_true', help='Use the separately authorized one-attempt/$5 HP-03 follow-up envelope')
    args = parser.parse_args()
    repair = bool(args.paid_core and args.paid_core.startswith('repair-'))
    commanding = args.paid_core in commanding_preflight.MODES
    if args.commanding_retests and args.paid_core not in ('commanding-hp2','commanding-hp3'):
        parser.error('--commanding-retests only authorizes commanding-hp2 or commanding-hp3')
    if args.commanding_hp3_retest and args.paid_core != 'commanding-hp3':
        parser.error('--commanding-hp3-retest only authorizes commanding-hp3')
    envelope_name = 'commanding-hp3-retest' if args.commanding_hp3_retest else 'commanding-retests' if args.commanding_retests else 'commanding-paid'
    commanding_plan = 'tests/gptlive/' + envelope_name + '-plan.json'
    commanding_ledger = 'target/' + envelope_name + '-budget.json'
    if args.paid_gala and args.browser:
        parser.error('--paid-gala and mocked --browser acceptance are separate runs')
    if args.connection_only and not args.paid_gala:
        parser.error('--connection-only requires --paid-gala')
    if args.embodiment and (args.paid_gala or args.classroom_capacity or not (args.browser or args.paid_core == 'coordination-text')):
        parser.error('--embodiment requires --browser or --paid-core coordination-text, without capacity or Gala mode')
    if args.classroom_capacity and (args.paid_core or args.paid_gala or args.browser):
        parser.error('--classroom-capacity requires a separate offline run')
    if args.classroom_capacity and args.java_tests != 'ClassroomCapacityIntegrationTest':
        parser.error('--classroom-capacity requires --java-tests ClassroomCapacityIntegrationTest alone')
    if args.paid_core and args.browser:
        parser.error('--paid-core and mocked --browser acceptance must run separately')
    if args.paid_gala and args.paid_core:
        parser.error('--paid-gala and --paid-core must run separately')
    if args.browser_mode != 'both' and not args.browser:
        parser.error('--browser-mode requires --browser')
    if args.coordination_scenario != 'baseline' and args.paid_core not in ('coordination', 'coordination-text'):
        parser.error('--coordination-scenario requires a paid Coordination journey')
    if args.coordination_scenario == 'interaction' and args.paid_core != 'coordination':
        parser.error('--coordination-scenario interaction requires the Live Coordination journey')
    escalation = args.coordination_scenario.startswith('escalation-')
    if escalation and args.paid_core != 'coordination':
        parser.error('Escalation acceptance requires the Live Coordination journey')
    root = Path(__file__).resolve().parents[2]
    os.chdir(root)
    recovery_fixture = escalation_cases(root)
    provider_props = {}
    if args.paid_core or args.paid_gala:
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
        if not key or key.startswith('${') or args.paid_core and not re.fullmatch(r'[a-zA-Z0-9._:-]{1,128}', model):
            raise SystemExit('Paid core acceptance requires an API key and configured openai.model (or OPENAI_MODEL).' if args.paid_core else 'Paid Gala acceptance requires an API key.')
        if repair and model != 'gpt-5.6-luna':
            raise SystemExit('Paid repair plan only prices gpt-5.6-luna; revise the budget before billing another model.')
        if commanding:
            plan=json.loads((root/commanding_plan).read_text(encoding='utf-8'))
            commanding_preflight.validate(plan,model)
            commanding_preflight.validate_audio(root)
            commanding_preflight.validate_ledger(root/commanding_ledger,plan,args.paid_core)
            commanding_preflight.available(key,plan)
    if args.paid_core == 'coordination':
        raise SystemExit('The committed-Speech Coordination harness is retired; use --paid-core repair-live.')
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
    schema, username, password = 'prometheus_gptlive_' + suffix, 'gptlive_' + suffix, 'Aa1!' + secrets.token_hex(24)
    assert re.fullmatch(r'prometheus_gptlive_[0-9a-f]{10}', schema)
    assert re.fullmatch(r'gptlive_[0-9a-f]{10}', username)
    artifacts = root / 'target' / ('gptlive-acceptance-' + suffix)
    artifacts.mkdir(parents=True)
    if args.embodiment:
        sources = [p for directory in ('src/main', 'src/test', 'tests/playwright', 'tests/js/embodiment')
                   for p in (root/directory).rglob('*')
                   if p.is_file() and p.suffix in ('.java', '.js', '.mjs', '.json', '.html', '.css')]
        sources += [root/'pom.xml', root/'tests/gptlive/run_acceptance.py', root/'playwright.config.mjs',
                    root/'tests/gptlive/embodiment_browser_smoke.mjs', root/'tests/gptlive/coordination_text_journey.mjs']
        manifest = {'schema': 'embodiment-acceptance-v1',
                    'sourceRevision': subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip(),
                    'selection': {'java': args.java_tests, 'browser': args.browser,
                                  'scenario': args.coordination_scenario, 'paid': bool(args.paid_core)},
                    'files': {str(p.relative_to(root)).replace('\\','/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
                    'limits': {'dbPool': 10, 'scheduledTicks': False,
                               'providerCalls': 24 if args.paid_core else 0, 'providerTokens': 200000 if args.paid_core else 0}}
        (artifacts/'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if repair or commanding:
        sources = [p for directory in ('src/main/java', 'src/main/resources/public', 'src/test/java', 'tests/gptlive', 'tests/playwright')
                   for p in (root/directory).rglob('*')
                   if p.is_file() and p.suffix in ('.java', '.js', '.mjs', '.json', '.html', '.css', '.py')]
        manifest = {'schema': 'commanding-source-v1' if commanding else 'gptlivefix-source-v1',
                    'sourceRevision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                    'mode': args.paid_core,
                    'files': {str(p.relative_to(root)).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}}
        (artifacts/'source.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    (artifacts / 'escalation-cases.json').write_text(json.dumps(recovery_fixture, indent=2), encoding='utf-8')
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
    env['PROMETHEUS_CLASSROOM_CAPACITY'] = 'true' if args.classroom_capacity else 'false'
    maven = ['mvnw.cmd'] if os.name == 'nt' else ['bash', './mvnw']
    # Isolate acceptance from IDE compilation and classes left by another branch.
    build_dir = artifacts/'build'
    maven.append('-Dprometheus.build.directory=' + str(build_dir))
    npm = 'npx.cmd' if os.name == 'nt' else 'npx'

    def run(command, name):
        with (artifacts / (name + '.log')).open('w', encoding='utf-8') as log:
            code = run_owned(command, env, log, (390 if commanding else 420) if name == 'paid-journey' else 1800)
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
            if args.classroom_capacity:
                capacity = root / 'target/classroom-capacity-report.json'
                if not capacity.exists():
                    raise SystemExit('Capacity measurement did not produce a report; skipped tests are not acceptance.')
                measured = json.loads(capacity.read_text(encoding='utf-8'))
                if measured.get('schema') != schema or not measured.get('functionalAcceptance'):
                    raise SystemExit('Capacity measurement lacks passing evidence for this owned schema.')
                print('Capacity numeric headroom: ' + ('PASS' if measured.get('numericHeadroomMet') else 'FAIL')
                      + '; classroom readiness: ' + str(measured.get('classroomReady')), flush=True)
        if args.paid_core or args.paid_gala:
            key = os.environ.get('OPENAI_API_KEY') or provider_props.get('openai.key')
            env.update(OPENAI_KEY=key.removeprefix('Bearer '), OPENAI_API_KEY=key.removeprefix('Bearer '),
                **({"OPENAI_MODEL": model} if args.paid_core else {}),
                OPENAI_OPENAIVSAZUREOPENAI='openai', OPENAI_URL='https://api.openai.com/v1/chat/completions',
                PROMETHEUS_SPEECH_URL='https://api.openai.com/v1/audio/speech',
                PROMETHEUS_LIVE_SESSIONS_URL='https://api.openai.com/v1/live/sessions',
                PROMETHEUS_LIVE_ENABLED='true', PROMETHEUS_LIVE_REQUEST_TIMEOUT_MS='30000')
            if args.embodiment:
                env.update(PROMETHEUS_LIVE_ENABLED='false', PROMETHEUS_PROVIDER_MAX_REQUESTS='24',
                    PROMETHEUS_PROVIDER_MAX_TOKENS='200000', PROMETHEUS_PROVIDER_MAX_SESSIONS='0',
                    OPENAI_MAX_COMPLETION_TOKENS='2048', OPENAI_REQUEST_TIMEOUT_MS='30000')
            if escalation:
                env.update(PROMETHEUS_PROVIDER_MAX_REQUESTS='24', PROMETHEUS_PROVIDER_MAX_TOKENS='200000',
                    PROMETHEUS_PROVIDER_MAX_SESSIONS='1', PROMETHEUS_LIVE_SESSION_LIFETIME_SECONDS='360',
                    OPENAI_MAX_COMPLETION_TOKENS='2048', OPENAI_REQUEST_TIMEOUT_MS='30000')
                for purpose in ('BEHAVIOUR', 'NONVERBAL', 'DECISION', 'EXTRACTION', 'SUMMARY'):
                    env['OPENAI_ROUTES_' + purpose + '_MAX_COMPLETION_TOKENS'] = '2048'
            if repair or commanding:
                plan = json.loads((root/(commanding_plan if commanding else 'tests/gptlive/gptlivefix-paid-plan.json')).read_text(encoding='utf-8'))
                env.update(PROMETHEUS_PROVIDER_MAX_REQUESTS='36', PROMETHEUS_PROVIDER_MAX_TOKENS='250000',
                    PROMETHEUS_PROVIDER_MAX_SESSIONS='1', PROMETHEUS_LIVE_SESSION_LIFETIME_SECONDS='360',
                    OPENAI_MAX_COMPLETION_TOKENS='2048', OPENAI_REQUEST_TIMEOUT_MS='30000',
                    PROMETHEUS_SPEECH_MODEL='gpt-4o-mini-tts', PROMETHEUS_LIVE_MODEL='gpt-live-1')
                env['PROMETHEUS_PROVIDER_MAX_SPEECH_CALLS'] = '12' if args.paid_core == 'repair-continuous' else '0'
                if args.paid_core == 'repair-text':
                    env['PROMETHEUS_PROVIDER_MAX_SESSIONS'] = '0'
                for purpose in ('BEHAVIOUR', 'NONVERBAL', 'DECISION', 'EXTRACTION', 'SUMMARY'):
                    env['OPENAI_ROUTES_' + purpose + '_MODEL'] = model
                    env['OPENAI_ROUTES_' + purpose + '_MAX_COMPLETION_TOKENS'] = '2048'
                    env['OPENAI_ROUTES_' + purpose + '_TIMEOUT_MS'] = '30000'
            run([*maven, '-q', '-DskipTests', 'test-compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(artifacts / 'classpath.txt')], 'compile')
            fixture_root = artifacts / 'provider-classes'
            fixtures = fixture_root / 'fixtures' / 'provider'
            fixtures.mkdir(parents=True)
            for source in (build_dir / 'test-classes/fixtures/provider').glob('*.class'):
                shutil.copy2(source, fixtures / source.name)
            classpath = os.pathsep.join([str(build_dir / 'classes'), str(fixture_root), (artifacts / 'classpath.txt').read_text().strip()])
            env['PROMETHEUS_PROVIDER_ARTIFACT'] = str(artifacts / 'provider.jsonl')
            if repair or commanding:
                ledger = root/(commanding_ledger if commanding else 'target/gptlivefix-paid-budget.json')
                if commanding: commanding_preflight.validate_ledger(ledger,plan,args.paid_core)
                reservation = reserve(ledger, plan, args.paid_core, artifacts)
                (artifacts/'paid-plan.json').write_text(json.dumps(plan, indent=2), encoding='utf-8')
                env['PROMETHEUS_COMMANDING_BUDGET_LEDGER' if commanding else 'PROMETHEUS_REPAIR_BUDGET_LEDGER'] = str(ledger)
                if commanding: env['PROMETHEUS_COMMANDING_PAID_PLAN'] = str(artifacts/'paid-plan.json')
                print('Paid '+('commanding' if commanding else 'repair')+' cumulative reserved ceiling: $' + str(reservation['reservedUsd']), flush=True)
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
                if args.paid_gala:
                    run(['node', 'tests/gptlive/gala_backend_journey.mjs', '--allow-paid-provider', str(artifacts / 'journey.json'),
                        '--game=' + args.game, *(['--connection-only'] if args.connection_only else [])], 'paid-journey')
                else:
                    if args.embodiment:
                        env['PROMETHEUS_EMBODIMENT_SMOKE'] = 'true'
                    journey = 'coordination_text_journey.mjs' if args.paid_core == 'coordination-text' else 'core_backend_journey.mjs'
                    if repair:
                        journey = 'gptlivefix_journey.mjs'
                    if commanding:
                        journey = 'commanding_journey.mjs'
                    run(['node', 'tests/gptlive/' + journey, '--allow-paid-provider', str(artifacts / 'journey.json'),
                        '--agent=' + args.paid_core, '--scenario=' + args.coordination_scenario], 'paid-journey')
                stop()
        if args.browser:
            run([*maven, '-q', '-DskipTests', 'test-compile', 'dependency:build-classpath',
                '-Dmdep.outputFile=' + str(artifacts / 'classpath.txt')], 'compile')
            # Isolate just the explicit fixtures; unrelated test controllers never enter component scanning.
            fixtures = artifacts / 'fixture-classes' / 'fixtures' / 'gptlive'
            fixtures.mkdir(parents=True)
            for source in (build_dir / 'test-classes/fixtures/gptlive').glob('*.class'):
                shutil.copy2(source, fixtures / source.name)
            classpath = os.pathsep.join([str(build_dir / 'classes'), str(artifacts / 'fixture-classes'),
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
                    specs = ['tests/playwright/valerian-instance-labels.spec.mjs', 'tests/playwright/valerian-commanding.spec.mjs', 'tests/playwright/valerian-gptlive-smoke.spec.mjs', 'tests/playwright/valerian-gptlive-native.spec.mjs', 'tests/playwright/valerian-continuous-smoke.spec.mjs', 'tests/playwright/valerian-state-view.spec.mjs',
                        'tests/playwright/valerian-state-graph.spec.mjs']
                    if enabled == 'true':
                        specs += ['tests/playwright/valerian-gptlive.spec.mjs']
                        if not args.live_only:
                            specs += ['tests/playwright/valerian-lifecycle.spec.mjs', 'tests/playwright/valerian-transcription.spec.mjs',
                                'tests/playwright/valerian-column-expansion.spec.mjs', 'tests/playwright/valerian-classroom-smoke.spec.mjs']
                    if args.embodiment:
                        specs = ['tests/playwright/valerian-embodiment.spec.mjs', 'tests/playwright/valerian-lifecycle.spec.mjs',
                            'tests/playwright/valerian-transcription.spec.mjs', 'tests/playwright/valerian-column-expansion.spec.mjs']
                    run([npm, 'playwright', 'test', '--config=playwright.config.mjs', *specs,
                        '--output=' + str(artifacts / ('browser-' + enabled)),
                        *(['--grep', args.browser_grep] if args.browser_grep else [])], 'browser-' + enabled)
                    stop()
    finally:
        stop()
        if args.embodiment or commanding:
            final_hashes = {str(p.relative_to(root)).replace('\\','/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p.exists()}
            manifest['filesAtEnd'] = final_hashes
            manifest['sourceChangedDuringRun'] = [name for name, value in manifest['files'].items() if final_hashes.get(name) != value]
            (artifacts/('source.json' if commanding else 'manifest.json')).write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        capacity_report = root / 'target/classroom-capacity-report.json'
        if args.classroom_capacity and capacity_report.exists():
            try:
                if json.loads(capacity_report.read_text(encoding='utf-8')).get('schema') == schema:
                    shutil.copy2(capacity_report, artifacts / capacity_report.name)
            except (ValueError, OSError):
                print('Capacity report could not be retained; inspect the Java log.', flush=True)
        with database.cursor() as cursor:
            if created_schema:
                cursor.execute('DROP DATABASE `' + schema + '`')
            if created_user:
                cursor.execute('DROP USER %s@%s', (username, 'localhost'))
        database.close()
        print('Owned app stopped; disposable schema/account removed.', flush=True)


if __name__ == '__main__':
    main()

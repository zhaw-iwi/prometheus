import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../../src/main/resources/public/valerian/script.js', import.meta.url), 'utf8');
const context = vm.createContext({
  Date, weatherLocationPayload: () => ({ location_label: 'Zurich', timezone: 'Europe/Zurich' }),
  weatherCondition: () => 'clear', weatherIntensity: () => 'none', weatherWind: () => 'calm',
  maxNumber: Math.max, weatherMax: Math.max, round: value => value, roundNumber: value => value,
  normalizeForecastDays: daily => daily.time, normalizeOpenMeteoForecastDays: daily => daily.time,
});
for (const name of ['weatherObservedAt', 'normalizeOpenMeteoWeather']) {
  const start = new RegExp('^function ' + name + '\\(', 'm').exec(source)?.index;
  assert.notEqual(start, undefined, name);
  const rest = source.slice(start);
  vm.runInContext(rest.slice(0, /^}/m.exec(rest).index + 1), context);
}
function normalize(time, offset) {
  return context.normalizeOpenMeteoWeather('Zurich', {}, {
    utc_offset_seconds: offset,
    current: { time, temperature_2m: 9 },
    daily: { time: ['2026-10-23'] },
  });
}

test('current source time uses the provider offset, including summer, winter and fractional offsets', () => {
  for (const [time, offset, expected] of [
    ['2026-10-22T22:00', 7200, '2026-10-22T20:00:00.000Z'],
    ['2026-12-22T22:00', 3600, '2026-12-22T21:00:00.000Z'],
    ['2026-10-22T01:00', 20700, '2026-10-21T19:15:00.000Z'],
    ['2026-10-22T22:00', -14400, '2026-10-23T02:00:00.000Z'],
    ['2026-10-22T22:00', 0, '2026-10-22T22:00:00.000Z'],
  ]) {
    const result = normalize(time, offset);
    assert.equal(result.current.observed_at, expected);
    assert.equal(result.current.temperature_c, 9);
    assert.equal(result.current.location_label, 'Zurich');
    assert.deepEqual(result.forecast.days, ['2026-10-23']);
    assert.ok(Number.isFinite(Date.parse(result.forecast.ts)));
  }
});

test('explicit source offsets are respected without applying the provider offset twice', () => {
  assert.equal(normalize('2026-10-22T22:00:00+02:00', 7200).current.observed_at, '2026-10-22T20:00:00.000Z');
  assert.equal(normalize('2026-10-22T20:00:00Z', 7200).current.observed_at, '2026-10-22T20:00:00.000Z');
});

test('missing or invalid source time/offset never becomes fresh fetch time', () => {
  for (const [time, offset] of [
    [undefined, 7200], ['bad-date', 7200], ['2026-02-30T22:00', 7200],
    ['2026-10-22T22:00', undefined], ['2026-10-22T22:00', null],
    ['2026-10-22T22:00', '7200'], ['2026-10-22T22:00', 90000],
  ]) {
    const result = normalize(time, offset);
    assert.equal(result.current.observed_at, null);
    assert.ok(Number.isFinite(Date.parse(result.current.ts)));
  }
  const old = normalize('2020-01-01T00:00', 0);
  assert.equal(old.current.observed_at, '2020-01-01T00:00:00.000Z');
  assert.equal(context.weatherObservedAt('2026-10-22T20:00Z'), '2026-10-22T20:00:00.000Z');
});

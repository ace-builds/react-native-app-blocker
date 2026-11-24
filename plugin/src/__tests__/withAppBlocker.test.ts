import fs from 'fs';
import os from 'os';
import path from 'path';

import withAppBlocker from '..';

const mockWithTargetsDir = jest.fn((config) => config);
jest.mock('@bacons/apple-targets/app.plugin', () => mockWithTargetsDir);

function makeConfig(projectRoot: string, ios: object = { bundleIdentifier: 'com.acme.app' }) {
  return { name: 'acme', slug: 'acme', ios, _internal: { projectRoot } } as any;
}

let root: string;
beforeEach(() => {
  root = fs.mkdtempSync(path.join(os.tmpdir(), 'app-blocker-'));
  mockWithTargetsDir.mockClear();
});
afterEach(() => fs.rmSync(root, { recursive: true, force: true }));

it('adds Family Controls and the default App Group without duplicating it', () => {
  const config = makeConfig(root, {
    bundleIdentifier: 'com.acme.app',
    entitlements: { 'com.apple.security.application-groups': ['group.com.acme.app', 'group.other'] },
  });
  const out = withAppBlocker(config, { iosExtensions: false });
  expect(out.ios.entitlements).toEqual({
    'com.apple.developer.family-controls': true,
    'com.apple.security.application-groups': ['group.com.acme.app', 'group.other'],
  });
  expect(mockWithTargetsDir).not.toHaveBeenCalled();
});

it('throws when there is no bundle identifier and no appGroup', () => {
  expect(() => withAppBlocker(makeConfig(root, {}), {})).toThrow(/bundleIdentifier/);
});

it('copies the extensions once, keeps user edits, and always rewrites the App Group constant', () => {
  withAppBlocker(makeConfig(root), { appGroup: 'group.first' });
  const dir = path.join(root, 'targets/app-blocker');
  expect(fs.readdirSync(dir).sort()).toEqual(['shield-action', 'shield-config']);
  expect(mockWithTargetsDir).toHaveBeenCalledWith(expect.anything(), {
    root: './targets/app-blocker',
    match: '*',
  });

  const swift = path.join(dir, 'shield-config/ShieldConfigurationExtension.swift');
  fs.writeFileSync(swift, '// my edit');
  withAppBlocker(makeConfig(root), { appGroup: 'group.second' });

  expect(fs.readFileSync(swift, 'utf8')).toBe('// my edit');
  expect(fs.readFileSync(path.join(dir, 'shield-config/AppBlockerConfig.swift'), 'utf8')).toContain(
    'let appBlockerAppGroup = "group.second"'
  );
});

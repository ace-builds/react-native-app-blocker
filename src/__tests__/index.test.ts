const mockPlatform = { OS: 'android' };
// Getters: jest.mock is hoisted above these consts, so read them lazily.
jest.mock('react-native', () => ({
  Platform: {
    get OS() {
      return mockPlatform.OS;
    },
  },
}));

const mockNative = {
  startBlocking: jest.fn(),
  setShieldOptions: jest.fn(),
  getInstalledApps: jest.fn(async () => [{ bundleId: 'x' }]),
  setSelectedApps: jest.fn(),
  presentAppPicker: jest.fn(),
};
jest.mock('../AppBlockerModule', () => ({
  __esModule: true,
  get default() {
    return mockNative;
  },
}));

// eslint-disable-next-line import/first
import * as AppBlocker from '..';

beforeEach(() => {
  jest.clearAllMocks();
  mockPlatform.OS = 'android';
});

it('startBlocking fills in defaults', () => {
  AppBlocker.startBlocking();
  expect(mockNative.startBlocking).toHaveBeenCalledWith('allowlist', 0, '');
  AppBlocker.startBlocking({ mode: 'blocklist', endTime: 5, message: 'hi' });
  expect(mockNative.startBlocking).toHaveBeenLastCalledWith('blocklist', 5, 'hi');
});

it('setShieldOptions drops empty values', () => {
  AppBlocker.setShieldOptions({ title: 'T', subtitle: undefined, textColor: '#fff' });
  expect(mockNative.setShieldOptions).toHaveBeenCalledWith({ title: 'T', textColor: '#fff' });
});

it('platform-only calls reject on the other platform', async () => {
  await expect(AppBlocker.presentAppPicker()).rejects.toThrow(/ios-only/);
  mockPlatform.OS = 'ios';
  await expect(AppBlocker.setSelectedApps([])).rejects.toThrow(/android-only/);
  expect(mockNative.setSelectedApps).not.toHaveBeenCalled();
});

it('getInstalledApps is empty on iOS', async () => {
  expect(await AppBlocker.getInstalledApps()).toEqual([{ bundleId: 'x' }]);
  mockPlatform.OS = 'ios';
  expect(await AppBlocker.getInstalledApps()).toEqual([]);
});

#!/usr/bin/env python3
"""Observer clocks: separate UTC, awake monotonic time, and elapsed time including suspend."""
import ctypes
import sys
import time


def continuous_reader():
    if sys.platform == 'darwin':
        lib = ctypes.CDLL('/usr/lib/libSystem.B.dylib')
        class Timebase(ctypes.Structure):
            _fields_ = [('numer', ctypes.c_uint32), ('denom', ctypes.c_uint32)]
        scale = Timebase()
        if lib.mach_timebase_info(ctypes.byref(scale)) != 0:
            raise RuntimeError('mach_timebase_info failed')
        lib.mach_continuous_time.restype = ctypes.c_uint64
        return lambda: lib.mach_continuous_time() * scale.numer // scale.denom
    if hasattr(time, 'CLOCK_BOOTTIME'):
        return lambda: time.clock_gettime_ns(time.CLOCK_BOOTTIME)
    raise RuntimeError('No suspend-inclusive clock available; do not silently substitute one')


class ObserverClock:
    def __init__(self):
        self.continuous = continuous_reader()
        self.origin = self._read()

    def _read(self):
        return {'utcNs': time.time_ns(), 'awakeNs': time.monotonic_ns(),
                'continuousNs': self.continuous()}

    def sample(self):
        now = self._read()
        elapsed = {k: (v - self.origin[k]) / 1e9 for k, v in now.items()}
        return {**now, 'elapsedSeconds': elapsed['continuousNs'],
                'awakeSeconds': elapsed['awakeNs'], 'utcElapsedSeconds': elapsed['utcNs'],
                'suspendSeconds': elapsed['continuousNs'] - elapsed['awakeNs'],
                'utcDriftSeconds': elapsed['utcNs'] - elapsed['continuousNs']}

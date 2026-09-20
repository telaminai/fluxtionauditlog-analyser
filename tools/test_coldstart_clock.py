import unittest
from coldstart_clock import ObserverClock

class ObserverClockTests(unittest.TestCase):
    def clock(self, end):
        clock = ObserverClock.__new__(ObserverClock)
        clock.origin = {'utcNs': 100_000_000_000, 'awakeNs': 10_000_000_000, 'continuousNs': 20_000_000_000}
        clock._read = lambda: end
        return clock

    def test_suspend_is_retained_in_elapsed_and_separated_from_awake(self):
        stamp = self.clock({'utcNs': 700_000_000_000, 'awakeNs': 110_000_000_000,
                            'continuousNs': 620_000_000_000}).sample()
        self.assertEqual(stamp['elapsedSeconds'], 600)
        self.assertEqual(stamp['awakeSeconds'], 100)
        self.assertEqual(stamp['suspendSeconds'], 500)
        self.assertEqual(stamp['utcDriftSeconds'], 0)

    def test_wall_clock_adjustment_does_not_extend_task_deadline(self):
        stamp = self.clock({'utcNs': 99_000_000_000, 'awakeNs': 12_000_000_000,
                            'continuousNs': 22_000_000_000}).sample()
        self.assertEqual(stamp['elapsedSeconds'], 2)
        self.assertEqual(stamp['utcDriftSeconds'], -3)
        self.assertEqual(stamp['suspendSeconds'], 0)

if __name__ == '__main__':
    unittest.main()

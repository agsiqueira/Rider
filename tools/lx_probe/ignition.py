"""Debounced inference from validated line reads, separate from transport."""
import math


class IgnitionTracker:
    def __init__(self, confirmations=2, stale_after=15):
        if confirmations < 2 or not math.isfinite(stale_after) or stale_after <= 0:
            raise ValueError("Require at least two confirmations and a positive freshness limit")
        self.confirmations = confirmations
        self.stale_after = stale_after
        self.current = "unknown"
        self.last_confirmed = "unknown"
        self.pending = None
        self.count = 0
        self.last_valid_at = None
        self.last_update = None
        self.gap = False

    def update(self, observation, now):
        if not math.isfinite(now) or (self.last_update is not None and now <= self.last_update):
            raise ValueError("Observation time must strictly increase")
        self.last_update = now
        if self.last_valid_at is not None and now - self.last_valid_at > self.stale_after:
            self._lose_connection()
        valid = (observation.get("status") == "decoded"
                 and observation.get("identity_matches_candidate") is True
                 and observation.get("protocol_closed") is True
                 and observation.get("wake_up_line") in ("on", "off"))
        event = None
        if not valid:
            self._lose_connection()
        else:
            value = observation["wake_up_line"]
            self.last_valid_at = now
            if self.pending == value:
                self.count += 1
            else:
                self.pending, self.count = value, 1
            if value != self.current:
                self.current = "unknown"
            if self.count >= self.confirmations:
                if self.current != value:
                    if self.last_confirmed == "unknown":
                        event = "state_established"
                    elif self.gap:
                        event = "state_recovered" if value == self.last_confirmed else "change_observed_after_gap"
                    elif value != self.last_confirmed:
                        event = "ignition_" + value
                    self.current = self.last_confirmed = value
                    self.gap = False
        return {"ignition": self.current, "last_confirmed": self.last_confirmed,
                "event": event, "evidence": "wake_up_line_correlation",
                "pending_confirmations": self.count,
                "last_valid_age_seconds": None if self.last_valid_at is None else now - self.last_valid_at}

    def _lose_connection(self):
        self.current, self.pending, self.count = "unknown", None, 0
        self.gap = True

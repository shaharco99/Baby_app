import Toybox.Application;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Time.Gregorian;

//! What one clock shows right now. [state] is one of :running, :paused, :next, :overdue,
//! :nothing; [clock] is null only for :nothing.
(:glance, :background)
class Side {
    public var state as Symbol;
    public var clock as String?;
    public var lastAt as Long?;

    public function initialize(state as Symbol, clock as String?, lastAt as Long?) {
        self.state = state;
        self.clock = clock;
        self.lastAt = lastAt;
    }
}

//! Reads the times the phone sent and turns them into clocks. Mirrors the Wear OS app: a timer
//! running on the phone wins; else the countdown to the next one, "overdue" once it passes.
//!
//! The phone's keys and units (epoch milliseconds) are WatchTimers' in android/core/watch.
(:glance, :background)
module Clocks {
    const STORAGE_KEY = "timers";

    function save(data as Object?) as Void {
        if (data instanceof Dictionary) {
            Application.Storage.setValue(STORAGE_KEY, data as Dictionary<Application.Storage.KeyType, Application.Storage.ValueType>);
        }
    }

    function stored() as Dictionary? {
        var value = Application.Storage.getValue(STORAGE_KEY);
        return (value instanceof Dictionary) ? value as Dictionary : null;
    }

    function nowMillis() as Long {
        return Time.now().value().toLong() * 1000l;
    }

    function feed(timers as Dictionary, now as Long) as Side {
        return side(timers, "nursing", "feed-due-at", "last-fed-at", now);
    }

    function pump(timers as Dictionary, now as Long) as Side {
        return side(timers, "pumping", "pump-due-at", "last-pump-at", now);
    }

    function side(timers as Dictionary, running as String, dueKey as String, lastKey as String, now as Long) as Side {
        var lastAt = number(timers, lastKey);
        var startedAt = number(timers, running + "-started-at");
        if (startedAt != null) {
            var pausedAt = number(timers, running + "-paused-at");
            var pausedMillis = number(timers, running + "-paused-millis");
            if (pausedMillis == null) {
                pausedMillis = 0l;
            }
            var until = (pausedAt != null) ? pausedAt : now;
            return new Side((pausedAt != null) ? :paused : :running, duration(until - startedAt - pausedMillis), lastAt);
        }

        var dueAt = number(timers, dueKey);
        if (dueAt == null) {
            return new Side(:nothing, null, lastAt);
        }
        if (dueAt <= now) {
            return new Side(:overdue, duration(now - dueAt), lastAt);
        }
        return new Side(:next, duration(dueAt - now), lastAt);
    }

    //! h:mm:ss, or m:ss under an hour — the phone's format.
    function duration(millis as Long) as String {
        var seconds = (millis < 0) ? 0 : (millis / 1000l).toNumber();
        var hours = seconds / 3600;
        var minutes = (seconds % 3600) / 60;
        var rest = seconds % 60;
        if (hours > 0) {
            return Lang.format("$1$:$2$:$3$", [hours, minutes.format("%02d"), rest.format("%02d")]);
        }
        return Lang.format("$1$:$2$", [minutes, rest.format("%02d")]);
    }

    function clockTime(epochMillis as Long) as String {
        var info = Gregorian.info(new Time.Moment((epochMillis / 1000l).toNumber()), Time.FORMAT_SHORT);
        return Lang.format("$1$:$2$", [(info.hour as Number).format("%02d"), (info.min as Number).format("%02d")]);
    }

    function number(timers as Dictionary, key as String) as Long? {
        var value = timers.get(key);
        if (value instanceof Long) {
            return value;
        }
        if (value instanceof Number) {
            return value.toLong();
        }
        return null;
    }
}

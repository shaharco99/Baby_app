import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! The glance-list row: one short line per clock, red when overdue.
(:glance)
class TimersGlance extends WatchUi.GlanceView {

    public function initialize() {
        GlanceView.initialize();
    }

    public function onUpdate(dc as Graphics.Dc) as Void {
        var timers = Clocks.stored();
        var height = dc.getHeight();
        if (timers == null) {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(0, height / 2, Graphics.FONT_GLANCE, WatchUi.loadResource(Rez.Strings.GlanceWaiting) as String,
                Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        var now = Clocks.nowMillis();
        line(dc, height / 4, Clocks.feed(timers, now),
            Rez.Strings.GlanceFeedNext, Rez.Strings.GlanceFeedLate, Rez.Strings.GlanceNursing, Rez.Strings.GlanceFeedNone);
        line(dc, height * 3 / 4, Clocks.pump(timers, now),
            Rez.Strings.GlancePumpNext, Rez.Strings.GlancePumpLate, Rez.Strings.GlancePumping, Rez.Strings.GlancePumpNone);
    }

    private function line(dc as Graphics.Dc, y as Number, side as Side, next as ResourceId, late as ResourceId,
            running as ResourceId, none as ResourceId) as Void {
        var label;
        switch (side.state) {
            case :running: label = running; break;
            case :paused: label = Rez.Strings.GlancePaused; break;
            case :next: label = next; break;
            case :overdue: label = late; break;
            default: label = none; break;
        }
        var text = WatchUi.loadResource(label) as String;
        var clock = side.clock;
        if (clock != null) {
            text = text + " " + clock;
        }
        dc.setColor((side.state == :overdue) ? Graphics.COLOR_RED : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, y, Graphics.FONT_GLANCE, text, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

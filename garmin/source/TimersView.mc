import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! Both clocks, full screen, ticking every second while shown.
class TimersView extends WatchUi.View {
    private var _timer as Timer.Timer?;

    public function initialize() {
        View.initialize();
    }

    public function onShow() as Void {
        var timer = new Timer.Timer();
        timer.start(method(:onTick), 1000, true);
        _timer = timer;
    }

    public function onHide() as Void {
        var timer = _timer;
        if (timer != null) {
            timer.stop();
            _timer = null;
        }
    }

    public function onTick() as Void {
        WatchUi.requestUpdate();
    }

    public function onUpdate(dc as Graphics.Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        var height = dc.getHeight();

        var timers = Clocks.stored();
        if (timers == null) {
            centered(dc, height / 2 - dc.getFontHeight(Graphics.FONT_SMALL), Graphics.FONT_SMALL, Graphics.COLOR_WHITE,
                WatchUi.loadResource(Rez.Strings.WaitingTitle) as String);
            centered(dc, height / 2 + 4, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY,
                WatchUi.loadResource(Rez.Strings.WaitingBody) as String);
            return;
        }

        var now = Clocks.nowMillis();
        var feed = Clocks.feed(timers, now);
        var pump = Clocks.pump(timers, now);
        // Both blocks, centred together, so a block with no "Last at" line does not leave a gap.
        var gap = height / 20;
        var y = (height - blockHeight(dc, feed) - gap - blockHeight(dc, pump)) / 2;
        y = drawSide(dc, y, Rez.Strings.Feeding, Rez.Strings.NursingNow, feed);
        drawSide(dc, y + gap, Rez.Strings.Pumping, Rez.Strings.PumpingNow, pump);
    }

    private function blockHeight(dc as Graphics.Dc, side as Side) as Number {
        var height = dc.getFontHeight(Graphics.FONT_XTINY);
        if (side.clock != null) {
            height += dc.getFontHeight(Graphics.FONT_NUMBER_MILD);
        }
        if (showsLastAt(side)) {
            height += dc.getFontHeight(Graphics.FONT_XTINY);
        }
        return height;
    }

    private function showsLastAt(side as Side) as Boolean {
        return side.lastAt != null && side.state != :running && side.state != :paused;
    }

    //! Label line ("Feeding · Next in"), the clock, then when the last one was.
    private function drawSide(dc as Graphics.Dc, top as Number, title as ResourceId, runningLabel as ResourceId, side as Side) as Number {
        var state;
        switch (side.state) {
            case :running: state = runningLabel; break;
            case :paused: state = Rez.Strings.Paused; break;
            case :next: state = Rez.Strings.NextIn; break;
            case :overdue: state = Rez.Strings.OverdueBy; break;
            default: state = Rez.Strings.NothingYet; break;
        }
        var line = Lang.format(WatchUi.loadResource(Rez.Strings.Line) as String,
            [WatchUi.loadResource(title), WatchUi.loadResource(state)]);
        var y = centered(dc, top, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, line);

        var clock = side.clock;
        if (clock != null) {
            var color = (side.state == :overdue) ? Graphics.COLOR_RED : Graphics.COLOR_WHITE;
            y = centered(dc, y, Graphics.FONT_NUMBER_MILD, color, clock);
        }

        var lastAt = side.lastAt;
        if (lastAt != null && showsLastAt(side)) {
            y = centered(dc, y, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY,
                Lang.format(WatchUi.loadResource(Rez.Strings.LastAt) as String, [Clocks.clockTime(lastAt)]));
        }
        return y;
    }

    //! Draws one centered line and returns the y just below it.
    private function centered(dc as Graphics.Dc, y as Number, font as Graphics.FontType, color as Graphics.ColorType, text as String) as Number {
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.drawText(dc.getWidth() / 2, y, font, text, Graphics.TEXT_JUSTIFY_CENTER);
        return y + dc.getFontHeight(font);
    }
}

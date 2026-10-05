import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

//! The feed and pump clocks, sent by the phone app over Garmin Connect.
//!
//! Holds no record content: the phone sends bare timestamps (see Clocks). A message that arrives
//! while the app is closed wakes the background service, which stores it; the glance and the
//! app read the stored copy and tick it locally.
(:background)
class TimersApp extends Application.AppBase {

    public function initialize() {
        AppBase.initialize();
    }

    public function onStart(state as Dictionary?) as Void {
        if (Background has :registerForPhoneAppMessageEvent) {
            Background.registerForPhoneAppMessageEvent();
        }
    }

    (:typecheck(disableGlanceCheck))
    public function getServiceDelegate() as [System.ServiceDelegate] {
        return [new $.TimersService()];
    }

    public function onBackgroundData(data as PersistableType) as Void {
        WatchUi.requestUpdate();
    }

    (:typecheck([disableBackgroundCheck, disableGlanceCheck]))
    public function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        if (Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        }
        return [new $.TimersView()];
    }

    (:glance, :typecheck(disableBackgroundCheck))
    public function getGlanceView() as [WatchUi.GlanceView] or [WatchUi.GlanceView, WatchUi.GlanceViewDelegate] or Null {
        return [new $.TimersGlance()];
    }

    (:typecheck([disableBackgroundCheck, disableGlanceCheck]))
    public function onPhoneMessage(message as Communications.PhoneAppMessage) as Void {
        Clocks.save(message.data);
        WatchUi.requestUpdate();
    }
}

//! Woken by a phone message while the app is not open: keep the times for the next look.
(:background)
class TimersService extends System.ServiceDelegate {

    public function initialize() {
        ServiceDelegate.initialize();
    }

    public function onPhoneAppMessage(message as Communications.PhoneAppMessage) as Void {
        Clocks.save(message.data);
        Background.exit(true);
    }
}

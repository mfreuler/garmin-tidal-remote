import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! Watch side of Tidal Remote: menus on the watch, commands go to the phone app.
//! Watch -> phone:  ["menu"] | ["mix", name] | ["genre", name] | ["search", term] | ["radio"]
//!                  | ["shuffle"] | ["playpause"] | ["next"] | ["prev"] | ["info"] | ["favon"] | ["favoff"] | ["poll"]
//! Phone -> watch:  ["menu", mixes[], genres[], searchTerms[], radioReady] | ["ok", text] | ["err", text] | ["song", text]
//! On newer watches (e.g. Fenix 7 Pro) a device app can also have a glance line, so one app covers both.
(:glance)
class TidalRemoteApp extends Application.AppBase {
    private var _result as ResultView?;
    private var _menu as WatchUi.Menu2?;
    // Created in getInitialView: the glance line can't load the menu classes.
    private var _title as StatusTitle?;
    private var _timer as Timer.Timer?;

    function initialize() {
        AppBase.initialize();
    }

    //! Only called when the full app opens, not for the glance line.
    function getInitialView() as [Views] or [Views, InputDelegates] {
        Communications.registerForPhoneAppMessages(method(:onPhone));
        // Refresh the menu lists in the background; the stored copy is used until the reply arrives.
        // Its reply also sets the connection dot next to the title.
        Communications.transmit(["menu"], null, new SilentListener());
        var timer = new Timer.Timer();
        timer.start(method(:onNoReply), 10000, false);
        _timer = timer;
        var title = new StatusTitle();
        _title = title;
        var menu = buildMainMenu(title);
        _menu = menu;
        return [menu, new MainMenuDelegate()];
    }

    function getGlanceView() as [WatchUi.GlanceView] or [WatchUi.GlanceView, WatchUi.GlanceViewDelegate] or Null {
        return [new TidalGlanceView()];
    }

    //! Sends a command to the phone and shows the result screen.
    function sendCommand(cmd as Array<String>, title as String) as Void {
        var info = "info".equals(cmd[0]);
        var view = new ResultView(title, !info);
        _result = view;
        WatchUi.pushView(view, info ? new NowPlayingDelegate(view) : new WatchUi.BehaviorDelegate(), WatchUi.SLIDE_LEFT);
        Communications.transmit(cmd, null, new CommandListener(view));
    }

    //! Updates the dot next to the menu title: did the phone app answer?
    function setReachable(reachable as Boolean) as Void {
        var timer = _timer;
        if (timer != null) {
            timer.stop();
            _timer = null;
        }
        var title = _title;
        if (title != null) {
            title.status = reachable ? StatusTitle.OK : StatusTitle.FAILED;
            WatchUi.requestUpdate();
        }
    }

    function onNoReply() as Void {
        _timer = null;
        var title = _title;
        if (title != null && title.status == StatusTitle.CHECKING) {
            setReachable(false);
        }
    }

    //! Track radio only works once the Tidal API login is set up on the phone; hide it until then.
    private function updateRadio(ready as Boolean) as Void {
        Application.Storage.setValue(Lists.RADIO_READY, ready);
        var menu = _menu;
        if (!ready && menu != null) {
            var index = menu.findItemById(:radio);
            if (index >= 0) {
                menu.deleteItem(index);
                WatchUi.requestUpdate();
            }
        }
    }

    function onPhone(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data;
        if (!(data instanceof Array) || data.size() == 0) {
            return;
        }
        // The phone's list may arrive wrapped in an outer array.
        if (data.size() == 1 && data[0] instanceof Array) {
            data = data[0] as Array;
        }
        var kind = data[0];
        setReachable(true);
        if ("menu".equals(kind) && data.size() >= 4) {
            Lists.save(Lists.MIXES, data[1]);
            Lists.save(Lists.GENRES, data[2]);
            Lists.save(Lists.SEARCH, data[3]);
            if (data.size() > 4) {
                updateRadio(data[4] == true);
            }
        } else if (_result != null && "song".equals(kind) && data.size() > 1) {
            (_result as ResultView).updateSong(data[1].toString());
        } else if (_result != null && ("ok".equals(kind) || "err".equals(kind))) {
            var text = data.size() > 1 ? data[1].toString() : "";
            (_result as ResultView).show("ok".equals(kind), text);
        }
    }
}

//! Silent transmit, used for the background menu refresh.
class SilentListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }

    function onComplete() as Void {
    }

    function onError() as Void {
        getTidalApp().setReachable(false);
    }
}

//! Reports a transmit failure on the result screen.
class CommandListener extends Communications.ConnectionListener {
    private var _view as ResultView;

    function initialize(view as ResultView) {
        ConnectionListener.initialize();
        _view = view;
    }

    //! Delivered to the phone; the screen stays as it is until the reply arrives.
    function onComplete() as Void {
    }

    function onError() as Void {
        _view.show(false, "Phone not reachable");
        getTidalApp().setReachable(false);
    }
}

function getTidalApp() as TidalRemoteApp {
    return Application.getApp() as TidalRemoteApp;
}

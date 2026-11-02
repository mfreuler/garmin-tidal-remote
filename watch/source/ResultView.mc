import Toybox.Attention;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! Shows the command with dots while waiting, then OK / Failed. Returns to the menu by itself after an OK.
//! With autoClose = false it stays open as a "now playing" screen: see NowPlayingDelegate.
class ResultView extends WatchUi.View {
    private const REPLY_TIMEOUT_MS = 25000;
    private const CLOSE_AFTER_OK_MS = 2500;
    private const POLL_MS = 30000;
    private const NOW_PLAYING = "Now playing";

    private var _title as String;
    private var _status as String = "...";
    private var _detail as String = "";
    private var _color as Number = Graphics.COLOR_DK_GRAY;
    private var _done as Boolean = false;
    private var _visible as Boolean = false;
    private var _timer as Timer.Timer = new Timer.Timer();
    private var _poll as Timer.Timer = new Timer.Timer();
    private var _autoClose as Boolean;
    private var _song as String = "";
    private var _pending as String = "info";

    //! autoClose = false keeps the result on screen until BACK, for things worth reading.
    function initialize(title as String, autoClose as Boolean) {
        View.initialize();
        _title = title;
        _autoClose = autoClose;
        _timer.start(method(:onTimeout), REPLY_TIMEOUT_MS, false);
    }

    function onShow() as Void {
        _visible = true;
        if (!_autoClose) {
            _poll.start(method(:onPoll), POLL_MS, true);
        }
    }

    function onHide() as Void {
        _visible = false;
        _timer.stop();
        _poll.stop();
    }

    //! Asks for the current song, so the screen follows when a track ends. Never vibrates or shows an error.
    function onPoll() as Void {
        if (_done) {
            Communications.transmit(["poll"], null, new SilentListener());
        }
    }

    function updateSong(text as String) as Void {
        if (text.length() > 0 && !text.equals(_song)) {
            _song = text;
            if (_done) {
                _title = NOW_PLAYING;
                _color = Graphics.COLOR_GREEN;
            }
            WatchUi.requestUpdate();
        }
    }

    function show(ok as Boolean, text as String) as Void {
        if (_done) {
            return;
        }
        _done = true;
        _timer.stop();
        _status = ok ? "OK" : "Failed";
        _detail = text;
        _color = ok ? Graphics.COLOR_GREEN : Graphics.COLOR_RED;
        if (!_autoClose) {
            // Skipping answers with the song, the others with the new state.
            var song = "info".equals(_pending) || "next".equals(_pending) || "prev".equals(_pending);
            _title = ok && !song ? text : NOW_PLAYING;
            if (ok && song) {
                _song = text;
            }
        }
        // Opening Now playing is not a command: it only buzzes if it fails.
        if (!ok || !"info".equals(_pending)) {
            buzz(ok);
        }
        WatchUi.requestUpdate();
        if (ok && _autoClose) {
            _timer.start(method(:close), CLOSE_AFTER_OK_MS, false);
        }
    }

    //! Information such as "nothing playing": grey text instead of a red "Failed", and it stays on screen.
    function showNote(text as String) as Void {
        if (_done) {
            return;
        }
        _done = true;
        _timer.stop();
        _status = "";
        _detail = text;
        _color = Graphics.COLOR_LT_GRAY;
        _song = "";
        if (!_autoClose) {
            _title = NOW_PLAYING;
        }
        if (!"info".equals(_pending)) {
            buzz(true);
        }
        WatchUi.requestUpdate();
    }

    //! Sends a playback command from the now playing screen. Ignored while the last one is still running.
    function send(command as String) as Void {
        if (!_done) {
            return;
        }
        _done = false;
        _pending = command;
        _title = "...";
        _color = Graphics.COLOR_DK_GRAY;
        _status = "...";
        _detail = "";
        WatchUi.requestUpdate();
        _timer.stop();
        _timer.start(method(:onTimeout), REPLY_TIMEOUT_MS, false);
        Communications.transmit([command], null, new CommandListener(self));
    }

    function onTimeout() as Void {
        log("no reply within 25 s");
        show(false, "No reply from phone");
    }

    function close() as Void {
        if (_visible) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.18, Graphics.FONT_TINY, _title, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);

        // Now playing: the song gets the whole screen and stays there during the next command.
        if (_song.length() > 0 && _color != Graphics.COLOR_RED) {
            var text = Graphics.fitTextToArea(_song, Graphics.FONT_SMALL, w * 0.78, h * 0.6, true);
            dc.drawText(w / 2, h * 0.55, Graphics.FONT_SMALL, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        // A note has no status line: its text sits in the middle, in grey.
        var note = _status.length() == 0;
        if (!note) {
            dc.setColor(_color, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.42, Graphics.FONT_MEDIUM, _status, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        if (_detail.length() > 0) {
            var text = Graphics.fitTextToArea(_detail, Graphics.FONT_SMALL, w * 0.75, h * 0.3, true);
            dc.setColor(note ? _color : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, note ? h * 0.5 : h * 0.68, Graphics.FONT_SMALL, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }

    private function buzz(ok as Boolean) as Void {
        if (Attention has :vibrate) {
            var profile = ok
                ? [new Attention.VibeProfile(60, 150)]
                : [new Attention.VibeProfile(80, 300), new Attention.VibeProfile(0, 150), new Attention.VibeProfile(80, 300)];
            Attention.vibrate(profile);
        }
    }
}

//! Now playing screen: tap or START = play/pause, swipe left or UP = next, swipe right or DOWN = previous,
//! touch and hold = shuffle, swipe up = add to favorites, swipe down = remove.
//! Buttons too, because touch can be off during an activity. BACK leaves the screen.
//! A plain InputDelegate: a BehaviorDelegate turns all swipes and UP/DOWN into next/previous page on a Fenix 7 Pro.
class NowPlayingDelegate extends WatchUi.InputDelegate {
    private var _view as ResultView;

    function initialize(view as ResultView) {
        InputDelegate.initialize();
        _view = view;
    }

    function onTap(clickEvent as WatchUi.ClickEvent) as Boolean {
        _view.send("playpause");
        return true;
    }

    function onHold(clickEvent as WatchUi.ClickEvent) as Boolean {
        _view.send("shuffle");
        return true;
    }

    function onKey(keyEvent as WatchUi.KeyEvent) as Boolean {
        var key = keyEvent.getKey();
        if (key == WatchUi.KEY_ENTER) {
            _view.send("playpause");
        } else if (key == WatchUi.KEY_UP) {
            _view.send("next");
        } else if (key == WatchUi.KEY_DOWN) {
            _view.send("prev");
        } else if (key == WatchUi.KEY_ESC) {
            WatchUi.popView(WatchUi.SLIDE_RIGHT);
        } else {
            return false;
        }
        return true;
    }

    function onSwipe(swipeEvent as WatchUi.SwipeEvent) as Boolean {
        var direction = swipeEvent.getDirection();
        if (direction == WatchUi.SWIPE_LEFT) {
            _view.send("next");
        } else if (direction == WatchUi.SWIPE_RIGHT) {
            _view.send("prev");
        } else if (direction == WatchUi.SWIPE_UP) {
            _view.send("favon");
        } else if (direction == WatchUi.SWIPE_DOWN) {
            _view.send("favoff");
        }
        return true;
    }
}

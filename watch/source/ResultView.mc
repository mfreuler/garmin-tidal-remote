import Toybox.Attention;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! Shows the command with dots while waiting, then OK / Failed. Returns to the menu by itself after an OK.
class ResultView extends WatchUi.View {
    private const REPLY_TIMEOUT_MS = 25000;
    private const CLOSE_AFTER_OK_MS = 2500;

    private var _title as String;
    private var _status as String = "...";
    private var _detail as String = "";
    private var _color as Number = Graphics.COLOR_DK_GRAY;
    private var _done as Boolean = false;
    private var _visible as Boolean = false;
    private var _timer as Timer.Timer = new Timer.Timer();
    private var _autoClose as Boolean;

    //! autoClose = false keeps the result on screen until BACK, for things worth reading.
    function initialize(title as String, autoClose as Boolean) {
        View.initialize();
        _title = title;
        _autoClose = autoClose;
        _timer.start(method(:onTimeout), REPLY_TIMEOUT_MS, false);
    }

    function onShow() as Void {
        _visible = true;
    }

    function onHide() as Void {
        _visible = false;
        _timer.stop();
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
        buzz(ok);
        WatchUi.requestUpdate();
        if (ok && _autoClose) {
            _timer.start(method(:close), CLOSE_AFTER_OK_MS, false);
        }
    }

    function onTimeout() as Void {
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

        // Info screens (no auto-close): after an OK only the text matters, so give it the whole screen.
        if (_done && _color == Graphics.COLOR_GREEN && !_autoClose) {
            var text = Graphics.fitTextToArea(_detail, Graphics.FONT_SMALL, w * 0.78, h * 0.6, true);
            dc.drawText(w / 2, h * 0.55, Graphics.FONT_SMALL, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }

        dc.setColor(_color, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.42, Graphics.FONT_MEDIUM, _status, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        if (_detail.length() > 0) {
            var text = Graphics.fitTextToArea(_detail, Graphics.FONT_SMALL, w * 0.75, h * 0.3, true);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.68, Graphics.FONT_SMALL, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
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

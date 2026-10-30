import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! The line shown in the glance list. START opens the full menu.
(:glance)
class TidalGlanceView extends WatchUi.GlanceView {
    function initialize() {
        GlanceView.initialize();
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, h * 0.3, Graphics.FONT_GLANCE, "Tidal Remote", Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        // Some watches (e.g. Venu) have a narrow glance line; fall back to a smaller font there.
        var sub = "Mix, Genre, Radio";
        var font = dc.getTextWidthInPixels(sub, Graphics.FONT_GLANCE) <= dc.getWidth() ? Graphics.FONT_GLANCE : Graphics.FONT_XTINY;
        dc.drawText(0, h * 0.72, font, sub, Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

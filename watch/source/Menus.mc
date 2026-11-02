import Toybox.Application;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! Menu lists: the phone sends them, the watch stores them. Defaults until the first sync.
module Lists {
    const MIXES = "mixes";
    const GENRES = "genres";
    const SEARCH = "search";
    const RADIO_READY = "radioReady";

    function get(key as String) as Array {
        var stored = Application.Storage.getValue(key);
        if (stored instanceof Array && stored.size() > 0) {
            return stored;
        }
        if (key.equals(MIXES)) {
            return ["My Mix 1", "My Mix 2", "My Mix 3", "My Mix 4", "My Mix 5", "My Mix 6", "My Mix 7", "My Mix 8"];
        } else if (key.equals(GENRES)) {
            return ["Dance & Electronic", "Rock / Indie", "Reggae / Dancehall", "R&B / Soul", "Blues", "Jazz"];
        }
        return ["House", "Deep House", "Dancehall", "Rock", "Indie Rock"];
    }

    function save(key as String, value) as Void {
        if (value instanceof Array) {
            Application.Storage.setValue(key, value);
        }
    }
}

//! Menu title with a dot showing whether the phone app answered: grey while waiting, green, or red.
class StatusTitle extends WatchUi.Drawable {
    enum { CHECKING, OK, FAILED }
    public var status as Number = CHECKING;

    function initialize() {
        Drawable.initialize({});
    }

    function draw(dc as Graphics.Dc) as Void {
        var text = "Tidal";
        var font = Graphics.FONT_SMALL;
        var x = dc.getWidth() / 2;
        var y = dc.getHeight() / 2;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, y, font, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        var r = dc.getFontHeight(font) / 4;
        var color = status == OK ? Graphics.COLOR_GREEN : (status == FAILED ? Graphics.COLOR_RED : Graphics.COLOR_LT_GRAY);
        dc.setColor(color, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(x + dc.getTextWidthInPixels(text, font) / 2 + r * 3, y, r);
    }
}

function buildMainMenu(title as StatusTitle) as WatchUi.Menu2 {
    var menu = new WatchUi.Menu2({ :title => title });
    menu.addItem(new WatchUi.MenuItem("My Mix", null, :mix, null));
    menu.addItem(new WatchUi.MenuItem("Favorites", "your playlists", :favorites, null));
    menu.addItem(new WatchUi.MenuItem("Genre", "random playlist", :genre, null));
    menu.addItem(new WatchUi.MenuItem("Search", "random playlist", :search, null));
    // Shown until the phone says otherwise; it then stays hidden until the radio is set up.
    if (Application.Storage.getValue(Lists.RADIO_READY) != false) {
        menu.addItem(new WatchUi.MenuItem("Track radio", "current song", :radio, null));
    }
    menu.addItem(new WatchUi.MenuItem("Now playing", "play, skip, shuffle", :info, null));
    return menu;
}

class MainMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id == :mix) {
            pushList("My Mix", "mix", Lists.MIXES);
        } else if (id == :favorites) {
            getTidalApp().sendCommand(["playlists", "0"], "Favorites");
        } else if (id == :genre) {
            pushList("Genre", "genre", Lists.GENRES);
        } else if (id == :search) {
            pushList("Search", "search", Lists.SEARCH);
        } else if (id == :radio) {
            getTidalApp().sendCommand(["radio"], "Track radio");
        } else if (id == :info) {
            getTidalApp().sendCommand(["info"], "Now playing");
        }
    }

    //! Touching the title area, on watches with API 5.1 or newer: a shortcut to Now playing.
    function onTitle() as Void {
        getTidalApp().sendCommand(["info"], "Now playing");
    }

    private function pushList(title as String, command as String, key as String) as Void {
        var menu = new WatchUi.Menu2({ :title => title });
        var names = Lists.get(key);
        for (var i = 0; i < names.size(); i++) {
            var name = names[i].toString();
            menu.addItem(new WatchUi.MenuItem(name, null, name, null));
        }
        WatchUi.pushView(menu, new ListDelegate(command), WatchUi.SLIDE_LEFT);
        // The stored list is shown at once; the phone's current one replaces it when it arrives.
        getTidalApp().showingList(menu, key);
        Communications.transmit(["menu"], null, new SilentListener());
    }
}

//! One page of the phone's playlists. The ID is the position, because two playlists can have the same name.
function buildPlaylistMenu(names as Array, offset as Number, more as Boolean) as WatchUi.Menu2 {
    var menu = new WatchUi.Menu2({ :title => "Favorites" });
    for (var i = 0; i < names.size(); i++) {
        menu.addItem(new WatchUi.MenuItem(names[i].toString(), null, offset + i, null));
    }
    if (more) {
        menu.addItem(new WatchUi.MenuItem("Next 25", null, :next, null));
    }
    return menu;
}

class PlaylistDelegate extends WatchUi.Menu2InputDelegate {
    private var _nextOffset as Number;

    function initialize(nextOffset as Number) {
        Menu2InputDelegate.initialize();
        _nextOffset = nextOffset;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        if (item.getId() == :next) {
            getTidalApp().nextPlaylists(_nextOffset);
            return;
        }
        var name = item.getLabel() as String;
        getTidalApp().sendCommand(["playlist", item.getId().toString(), name], name);
    }
}

//! A list entry was picked: send [command, name] to the phone.
class ListDelegate extends WatchUi.Menu2InputDelegate {
    private var _command as String;

    function initialize(command as String) {
        Menu2InputDelegate.initialize();
        _command = command;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var name = item.getId() as String;
        getTidalApp().sendCommand([_command, name], name);
    }
}

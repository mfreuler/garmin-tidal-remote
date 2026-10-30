import Toybox.Application;
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

function buildMainMenu() as WatchUi.Menu2 {
    var menu = new WatchUi.Menu2({ :title => "Tidal" });
    menu.addItem(new WatchUi.MenuItem("My Mix", null, :mix, null));
    menu.addItem(new WatchUi.MenuItem("Genre", "random playlist", :genre, null));
    menu.addItem(new WatchUi.MenuItem("Search", "random playlist", :search, null));
    // Shown until the phone says otherwise; it then stays hidden until the radio is set up.
    if (Application.Storage.getValue(Lists.RADIO_READY) != false) {
        menu.addItem(new WatchUi.MenuItem("Track radio", "current song", :radio, null));
    }
    menu.addItem(new WatchUi.MenuItem("Playback", "play, skip, shuffle", :playback, null));
    menu.addItem(new WatchUi.MenuItem("Song info", "artist and title", :info, null));
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
        } else if (id == :genre) {
            pushList("Genre", "genre", Lists.GENRES);
        } else if (id == :search) {
            pushList("Search", "search", Lists.SEARCH);
        } else if (id == :radio) {
            getTidalApp().sendCommand(["radio"], "Track radio");
        } else if (id == :info) {
            getTidalApp().sendCommand(["info"], "Now playing");
        } else if (id == :playback) {
            var menu = new WatchUi.Menu2({ :title => "Playback" });
            menu.addItem(new WatchUi.MenuItem("Play / Pause", null, "playpause", null));
            menu.addItem(new WatchUi.MenuItem("Next track", null, "next", null));
            menu.addItem(new WatchUi.MenuItem("Previous track", null, "prev", null));
            menu.addItem(new WatchUi.MenuItem("Shuffle", "on / off", "shuffle", null));
            WatchUi.pushView(menu, new PlaybackDelegate(), WatchUi.SLIDE_LEFT);
        }
    }

    private function pushList(title as String, command as String, key as String) as Void {
        var menu = new WatchUi.Menu2({ :title => title });
        var names = Lists.get(key);
        for (var i = 0; i < names.size(); i++) {
            var name = names[i].toString();
            menu.addItem(new WatchUi.MenuItem(name, null, name, null));
        }
        WatchUi.pushView(menu, new ListDelegate(command), WatchUi.SLIDE_LEFT);
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

//! Playback entry picked: the item id is the command.
class PlaybackDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        getTidalApp().sendCommand([item.getId() as String], item.getLabel());
    }
}

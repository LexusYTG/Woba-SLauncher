package com.woba.slauncher.sim;

import android.content.Context;

import com.woba.slauncher.sim.apps.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Catálogo de apps simuladas: id, nombre, glifo y fábrica. */
public final class SimRegistry {
    private SimRegistry() {}

    public interface Factory { SimApp create(Context c, SimHost host); }

    public static class Def {
        public final String id, title;
        public final int glyph;
        public final Factory factory;
        /** Aparece en la lista de apps. */
        public boolean listed = true;
        /** Color propio del tile (0 = acento). */
        public int color;
        /** Si no es null, abrir esta acción de Android en lugar de una página simulada. */
        public String directAction;

        Def(String id, String title, int glyph, Factory f) {
            this.id = id;
            this.title = title;
            this.glyph = glyph;
            this.factory = f;
        }
    }

    public static final String PREFIX = "sim:";

    private static final LinkedHashMap<String, Def> MAP = new LinkedHashMap<>();

    static {
        reg("wallet", "Wallet", com.woba.slauncher.ui.Glyph.WALLET, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new WalletApp(c, h); }
        });
        reg("xbox", "Xbox", com.woba.slauncher.ui.Glyph.XBOX, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MiscApps.XboxApp(c, h); }
        }).color = 0xFF107C10;
        reg("help", "Help + Tips", com.woba.slauncher.ui.Glyph.TIPS, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MiscApps.HelpApp(c, h); }
        });
        reg("onedrive", "OneDrive", com.woba.slauncher.ui.Glyph.ONEDRIVE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MiscApps.OneDriveApp(c, h); }
        });
        reg("notes", "OneNote", com.woba.slauncher.ui.Glyph.NOTE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new NotesApp(c, h); }
        });
        reg("office", "Office", com.woba.slauncher.ui.Glyph.WORD, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new OfficeApp(c, h); }
        });
        reg("alarms", "Alarms & Clock", com.woba.slauncher.ui.Glyph.ALARM, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new AlarmsApp(c, h); }
        });
        reg("files", "Files", com.woba.slauncher.ui.Glyph.FOLDER, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new FilesApp(c, h); }
        });
        reg("video", "Movies & TV", com.woba.slauncher.ui.Glyph.VIDEO, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new VideoApp(c, h); }
        });
        reg("radio", "FM Radio", com.woba.slauncher.ui.Glyph.RADIO, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new RadioApp(c, h); }
        });
        reg("music", "Music", com.woba.slauncher.ui.Glyph.MUSIC, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MusicApp(c, h); }
        });
        reg("weather", "Weather", com.woba.slauncher.ui.Glyph.SUN, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new WeatherApp(c, h); }
        });
        reg("store", "Store", com.woba.slauncher.ui.Glyph.STORE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new StoreApp(c, h); }
        });
        reg("maps", "Maps", com.woba.slauncher.ui.Glyph.PIN, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MapsApp(c, h); }
        });
        reg("edge", "Microsoft Edge", com.woba.slauncher.ui.Glyph.GLOBE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new EdgeApp(c, h); }
        });
        reg("mail", "Outlook Mail", com.woba.slauncher.ui.Glyph.MAIL, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MailApp(c, h); }
        });
        reg("calendar", "Calendar", com.woba.slauncher.ui.Glyph.CALENDAR, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new CalendarApp(c, h); }
        });
        reg("photos", "Photos", com.woba.slauncher.ui.Glyph.PHOTOS, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new PhotosApp(c, h); }
        });
        reg("messaging", "Messaging", com.woba.slauncher.ui.Glyph.SPEECH, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new MessagingApp(c, h); }
        });
        reg("phone", "Phone", com.woba.slauncher.ui.Glyph.PHONE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new PhoneApp(c, h); }
        });
        reg("call", "Call", com.woba.slauncher.ui.Glyph.PHONE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new CallApp(c, h); }
        }).listed = false;
        // Cámara propia (Camera2): ya no abre la app de cámara del sistema.
        reg("camera", "Camera", com.woba.slauncher.ui.Glyph.CAMERA, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new CameraApp(c, h); }
        });
        reg("people", "People", com.woba.slauncher.ui.Glyph.PEOPLE, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new PeopleApp(c, h); }
        });
        reg("calculator", "Calculator", com.woba.slauncher.ui.Glyph.CALC, new Factory() {
            @Override public SimApp create(Context c, SimHost h) { return new CalculatorApp(c, h); }
        });
    }

    private static Def reg(String id, String title, int glyph, Factory f) {
        Def d = new Def(id, title, glyph, f);
        MAP.put(id, d);
        return d;
    }

    public static Def get(String id) { return MAP.get(id); }

    public static boolean isSim(String pkg) { return pkg != null && pkg.startsWith(PREFIX); }

    public static String idOf(String pkg) { return pkg.substring(PREFIX.length()); }

    public static List<Def> all() { return new ArrayList<>(MAP.values()); }
}

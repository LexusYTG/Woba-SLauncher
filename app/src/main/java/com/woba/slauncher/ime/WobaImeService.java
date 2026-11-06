package com.woba.slauncher.ime;

import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.ui.Theme;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Teclado WOBA: clon del teclado de Windows Phone. Usa el tema (claro/oscuro y acento) del launcher y los ajustes de
 * Ajustes > Extras > Keyboard (altura, tamaño, sonido, autocorrección...).
 *
 * Autocorrección: al terminar una palabra (espacio, puntuación, Intro) se cambia sola por la corrección si hay una
 * (la del corrector del sistema cuando ya llegó, o la de {@link Dict}). Borrar justo después la deshace y la palabra
 * original se aprende para no volver a corregirla.
 */
public class WobaImeService extends InputMethodService implements WpKeyboardView.Listener {
    private static WobaImeService inst;
    private static String pendingVoice;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WpKeyboardView kv;
    private EditorInfo ei;
    private boolean noSuggest;
    private String word = "";
    private SpellCheckerSession spell;
    private int seq;
    private Dict dict;
    private int dictLang = -1;

    // resultado del corrector del sistema para la palabra actual
    private String pendWord, pendFix, pendAlt;
    private boolean pendOk;
    // última autocorrección, para poder deshacerla con Borrar
    private String fixOrig, fixNew, fixBoundary;

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        Theme.init(this);
        loadTheme();
        dict = new Dict(this);
    }

    private void loadTheme() { Theme.apply(Prefs.loadAccent(this), Prefs.loadDark(this)); }

    private boolean pref(String k) { return KbPrefs.on(this, k); }

    @Override public View onCreateInputView() {
        loadTheme();
        kv = new WpKeyboardView(this, this);
        return kv;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        ei = info;
        if (kv == null) return;
        loadTheme();
        kv.refreshTheme();
        int lang = KbPrefs.level(this, KbPrefs.LANG, 0);
        if (lang != dictLang) {
            dictLang = lang;
            dict.load();
        }
        fixOrig = null;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS, var = info.inputType & InputType.TYPE_MASK_VARIATION;
        boolean pass = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        boolean mail = cls == InputType.TYPE_CLASS_TEXT && (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        boolean uri = cls == InputType.TYPE_CLASS_TEXT && var == InputType.TYPE_TEXT_VARIATION_URI;
        noSuggest = pass || mail || uri || (info.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        kv.setContext(mail, uri);
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME) kv.setMode(WpKeyboardView.MODE_NUM);
        else if (cls == InputType.TYPE_CLASS_PHONE) kv.setMode(WpKeyboardView.MODE_PHONE);
        else kv.setMode(WpKeyboardView.MODE_TEXT);

        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multi = (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        int et = WpKeyboardView.ENTER_NEWLINE;
        if (!multi && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            switch (action) {
                case EditorInfo.IME_ACTION_GO: et = WpKeyboardView.ENTER_GO; break;
                case EditorInfo.IME_ACTION_SEARCH: et = WpKeyboardView.ENTER_SEARCH; break;
                case EditorInfo.IME_ACTION_SEND: et = WpKeyboardView.ENTER_SEND; break;
                case EditorInfo.IME_ACTION_NEXT: et = WpKeyboardView.ENTER_NEXT; break;
                case EditorInfo.IME_ACTION_DONE: et = WpKeyboardView.ENTER_DONE; break;
                default: break;
            }
        }
        kv.setEnter(et);
        kv.setShift(0);
        autoCaps();
        refreshSuggestions();
        if (pendingVoice != null) h.postDelayed(new Runnable() { @Override public void run() { flushVoice(); } }, 250);
    }

    @Override public void onUpdateSelection(int os, int oe, int ns, int ne, int cs, int ce) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce);
        autoCaps();
        refreshSuggestions();
    }

    @Override public void onDestroy() {
        if (inst == this) inst = null;
        if (spell != null) { try { spell.close(); } catch (Exception ignored) { } spell = null; }
        super.onDestroy();
    }

    private void autoCaps() {
        if (kv == null || ei == null || kv.shiftState() == 2) return;
        if (!pref(KbPrefs.AUTOCAP)) return;
        InputConnection ic = getCurrentInputConnection();
        int caps = 0;
        if (ic != null && ei.inputType != InputType.TYPE_NULL) caps = ic.getCursorCapsMode(ei.inputType);
        kv.setShift(caps != 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ dictado por voz

    @Override public void onMic() {
        try {
            Intent i = new Intent(this, VoiceTypeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Exception ignored) { }
    }

    /** Lo llama VoiceTypeActivity con lo que oyó; se escribe en cuanto el campo de texto vuelve a estar activo. */
    public static void deliverVoice(String text) {
        pendingVoice = text;
        if (inst != null) {
            inst.h.postDelayed(new Runnable() { @Override public void run() { if (inst != null) inst.flushVoice(); } }, 600);
        }
    }

    private void flushVoice() {
        String t = pendingVoice;
        InputConnection ic = getCurrentInputConnection();
        if (t == null || ic == null || kv == null) return;
        pendingVoice = null;
        if (kv.shiftState() != 0 && !t.isEmpty()) t = t.substring(0, 1).toUpperCase(Locale.getDefault()) + t.substring(1);
        ic.commitText(t + " ", 1);
        if (kv.shiftState() == 1) kv.setShift(0);
    }

    // ------------------------------------------------------------------ teclas

    @Override public void onText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        fixOrig = null;
        boolean boundary = s.length() == 1 && ".,;:!?)".indexOf(s.charAt(0)) >= 0;
        if (!(boundary && autocorrect(ic, s))) ic.commitText(s, 1);
        if (kv.shiftState() == 1) kv.setShift(0);
    }

    @Override public void onBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (fixOrig != null) {
            String tail = fixNew + fixBoundary;
            CharSequence b = ic.getTextBeforeCursor(tail.length(), 0);
            String orig = fixOrig;
            fixOrig = null;
            if (b != null && b.toString().equals(tail)) {
                // deshacer la autocorrección: vuelve la palabra tal cual se escribió y ya no se corrige más
                ic.beginBatchEdit();
                ic.deleteSurroundingText(tail.length(), 0);
                ic.commitText(orig, 1);
                ic.endBatchEdit();
                dict.learn(orig);
                pendWord = null;
                return;
            }
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    @Override public void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || ei == null) return;
        autocorrect(ic, "");
        fixOrig = null;
        int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean multi = (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        boolean plain = multi || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
        if (plain) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        else ic.performEditorAction(action);
    }

    @Override public void onSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence b = noSuggest ? null : ic.getTextBeforeCursor(2, 0);
        // dos espacios seguidos escriben ". " (como en WP)
        if (pref(KbPrefs.DBLSPACE) && b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
            fixOrig = null;
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
        } else if (!autocorrect(ic, " ")) {
            fixOrig = null;
            ic.commitText(" ", 1);
        }
        if (kv.shiftState() == 1) kv.setShift(0);
    }

    @Override public void onSuggestion(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        fixOrig = null;
        String w = wordBefore(ic);
        if (s.equals(w)) {
            // tocar la palabra tal cual la escribiste: la aceptas y el teclado la aprende
            dict.learn(w);
            pendWord = w;
            pendOk = true;
            pendFix = null;
            ic.commitText(" ", 1);
            return;
        }
        if (!w.isEmpty() && Character.isUpperCase(w.charAt(0)) && !s.isEmpty()) s = s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
        ic.beginBatchEdit();
        ic.deleteSurroundingText(w.length(), 0);
        ic.commitText(s + " ", 1);
        ic.endBatchEdit();
    }

    @Override public void onCursor(int dx, int dy) {
        fixOrig = null;
        for (int i = 0; i < Math.abs(dx); i++) sendDownUpKeyEvents(dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
        for (int i = 0; i < Math.abs(dy); i++) sendDownUpKeyEvents(dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
    }

    // ------------------------------------------------------------------ autocorrección

    /** La palabra que termina en el cursor ("" si el cursor está en medio de una palabra o no hay). */
    private String wordBefore(InputConnection ic) {
        CharSequence after = ic.getTextAfterCursor(1, 0);
        CharSequence before = ic.getTextBeforeCursor(40, 0);
        if (before == null || (after != null && after.length() > 0 && Character.isLetter(after.charAt(0)))) return "";
        int i = before.length();
        while (i > 0 && (Character.isLetter(before.charAt(i - 1)) || before.charAt(i - 1) == '\'')) i--;
        while (i < before.length() && before.charAt(i) == '\'') i++;
        return before.subSequence(i, before.length()).toString();
    }

    private boolean sentenceStart(InputConnection ic, int wordLen) {
        CharSequence b = ic.getTextBeforeCursor(wordLen + 6, 0);
        if (b == null) return true;
        int end = b.length() - wordLen;
        if (end <= 0) return b.length() < wordLen + 6;
        int i = end - 1;
        while (i >= 0 && b.charAt(i) == ' ') i--;
        if (i < 0) return b.length() < wordLen + 6;
        char ch = b.charAt(i);
        return ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == '¿' || ch == '¡';
    }

    /** Cambia la palabra que acaba de terminar por su corrección y escribe el separador. false si no hay nada que corregir. */
    private boolean autocorrect(InputConnection ic, String boundary) {
        fixOrig = null;
        if (noSuggest || !pref(KbPrefs.AUTOCORRECT)) return false;
        String w = wordBefore(ic);
        if (w.length() < 3) return false;
        String lw = w.toLowerCase(Locale.getDefault());
        if (dict.isLearned(lw) || dict.known(lw)) return false;
        String fix = null;
        boolean ss = sentenceStart(ic, w.length());
        boolean skip = w.equals(w.toUpperCase(Locale.getDefault())) || (Character.isUpperCase(w.charAt(0)) && !ss);
        if (skip) return false;
        if (w.equals(pendWord)) {
            if (pendFix != null) fix = pendFix;
            else if (pendOk) return false;
        }
        if (fix == null) fix = dict.fix(w, ss);
        if (fix == null || fix.equals(w)) return false;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(w.length(), 0);
        ic.commitText(fix + boundary, 1);
        ic.endBatchEdit();
        fixOrig = w;
        fixNew = fix;
        fixBoundary = boundary;
        pendWord = null;
        return true;
    }

    // ------------------------------------------------------------------ sugerencias

    private void refreshSuggestions() {
        if (kv == null) return;
        InputConnection ic = getCurrentInputConnection();
        word = "";
        if (ic == null || noSuggest) { kv.setSuggestions(null, null, null); return; }
        word = wordBefore(ic);
        if (word.isEmpty() || !pref(KbPrefs.SUGGEST)) { kv.setSuggestions(null, null, null); return; }
        renderStrip(ic);
        if (!word.equals(pendWord)) {
            pendFix = pendAlt = null;
            pendOk = false;
            askSpell(word);
        }
    }

    /** Pinta la barra: palabra escrita | corrección (la que se aplica sola, en acento) | otra opción. */
    private void renderStrip(InputConnection ic) {
        if (kv == null || word.isEmpty()) return;
        String lw = word.toLowerCase(Locale.getDefault());
        boolean ac = pref(KbPrefs.AUTOCORRECT) && !dict.isLearned(lw) && !dict.known(lw);
        String fix = null;
        boolean sys = word.equals(pendWord);
        if (ac && ic != null) {
            boolean ss = sentenceStart(ic, word.length());
            boolean skip = word.length() < 3 || word.equals(word.toUpperCase(Locale.getDefault())) || (Character.isUpperCase(word.charAt(0)) && !ss);
            if (!skip) {
                if (sys && pendFix != null) fix = pendFix;
                else if (!(sys && pendOk)) fix = dict.fix(word, ss);
            }
        }
        ArrayList<String> opts = new ArrayList<>();
        if (fix != null && !fix.equalsIgnoreCase(word)) opts.add(fix);
        if (sys && pendAlt != null && !pendAlt.equalsIgnoreCase(word) && !opts.contains(pendAlt)) opts.add(pendAlt);
        for (String comp : dict.complete(lw, 3)) {
            if (comp == null || opts.size() >= 2) continue;
            String cc = Dict.matchCase(word, comp);
            if (!cc.equalsIgnoreCase(word) && !opts.contains(cc)) opts.add(cc);
        }
        String b = opts.size() > 0 ? opts.get(0) : null, c = opts.size() > 1 ? opts.get(1) : null;
        kv.setSuggestions(word, b, c, fix != null && b != null && b.equals(fix) ? 1 : -1);
    }

    @SuppressWarnings("deprecation")
    private void askSpell(String w) {
        try {
            if (spell == null) {
                TextServicesManager tsm = (TextServicesManager) getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
                if (tsm == null) return;
                spell = tsm.newSpellCheckerSession(null, Locale.getDefault(), new SpellCheckerSession.SpellCheckerSessionListener() {
                    @Override public void onGetSuggestions(SuggestionsInfo[] r) {
                        if (r == null || r.length == 0 || kv == null) return;
                        SuggestionsInfo si = r[0];
                        if (si.getSequence() != seq || word.isEmpty()) return; // ya se escribió otra cosa
                        int attrs = si.getSuggestionsAttributes();
                        boolean inDict = (attrs & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0;
                        boolean typo = (attrs & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0;
                        String lw = word.toLowerCase(Locale.getDefault());
                        ArrayList<String> sg = new ArrayList<>();
                        for (int k = 0; k < si.getSuggestionsCount(); k++) {
                            String s = si.getSuggestionAt(k);
                            if (s == null || s.isEmpty() || s.equalsIgnoreCase(word)) continue;
                            // una corrección razonable cambia poco la palabra
                            if (Dict.dist(lw, s.toLowerCase(Locale.getDefault()), 2) > 2) continue;
                            sg.add(s);
                        }
                        pendWord = word;
                        pendOk = inDict && !typo;
                        pendFix = pendAlt = null;
                        if (!pendOk && !sg.isEmpty()) {
                            String best = sg.get(0);
                            int bs = dict.scoreOf(best);
                            for (String s : sg) {
                                int sc = dict.scoreOf(s);
                                if (sc > bs) { bs = sc; best = s; }
                            }
                            pendFix = best;
                            for (String s : sg) if (!s.equals(best)) { pendAlt = s; break; }
                        } else if (pendOk && !sg.isEmpty()) {
                            pendAlt = sg.get(0);
                        }
                        renderStrip(getCurrentInputConnection());
                    }
                    @Override public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] r) { }
                }, true);
            }
            if (spell != null) spell.getSuggestions(new TextInfo(w, 0, ++seq), 3);
        } catch (Exception ignored) { }
    }
}

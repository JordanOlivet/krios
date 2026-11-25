package io.lakio.krios.core.integration.rehearsal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import io.lakio.krios.core.client.screen.KriosTerminalScreen;
import io.lakio.krios.core.client.screen.KriosTerminalScreen.Control;
import net.minecraft.client.Minecraft;

/**
 * What the Krios terminals give a client test mod (Rehearsal, optional and
 * never a dependency: it finds this class by the name a test run passes in
 * {@code -Drehearsal.extensions}, and calls these public methods by name,
 * JDK types only). In the jar it does nothing unless such a run asks for it.
 *
 * <p>It adds no knowledge of its own: every terminal of every Krios mod
 * says what it shows ({@link KriosTerminalScreen#readout()}) and names its
 * controls ({@link KriosTerminalScreen#controls()}). A test then reads a
 * machine's state as data and presses "panel.faces" or "codex" instead of
 * a pixel that moves with the layout.
 *
 * <pre>
 * state                      adds "krios": the open terminal's readout and its controls
 * krios press &lt;control&gt;      a left click on that control, scrolled into view first when it needs to be
 * krios set &lt;control&gt; on|off  the same click, only when the toggle is not there yet
 * </pre>
 *
 * Client thread only.
 */
public final class TerminalExtension {

    /** The key of the state, and the first word of the verbs. */
    public String name() {
        return "krios";
    }

    public Map<String, Object> state() {
        Map<String, Object> out = new LinkedHashMap<>();
        KriosTerminalScreen<?> terminal = terminal();
        out.put("open", terminal != null);
        if (terminal != null) {
            out.putAll(terminal.readout());
            Map<String, Object> controls = new LinkedHashMap<>();
            for (Control control : terminal.controls()) {
                controls.put(control.name(), control.on() == null ? "button" : control.on() ? "on" : "off");
            }
            out.put("controls", controls);
        }
        return out;
    }

    /** A verb starting with "krios"; null for any other, which is not ours. */
    public String run(List<String> words) {
        if (words.isEmpty() || !words.get(0).equals("krios")) {
            return null;
        }
        String usage = "krios takes: press <control> | set <control> on|off";
        if (words.size() < 3) {
            throw new IllegalArgumentException(usage);
        }
        KriosTerminalScreen<?> terminal = terminal();
        if (terminal == null) {
            throw new IllegalArgumentException("no Krios terminal is open");
        }
        if (terminal.isBooting()) {
            throw new IllegalArgumentException("the terminal is still booting and would swallow the click: "
                    + "wait a few ticks after it opens");
        }
        // A row of a list that scrolls is brought into view first.
        terminal.reveal(words.get(2));
        Control control = find(terminal, words.get(2));
        switch (words.get(1)) {
            case "press" -> {
                if (words.size() != 3) {
                    throw new IllegalArgumentException(usage);
                }
                click(terminal, control);
                return "pressed " + control.name();
            }
            case "set" -> {
                if (words.size() != 4 || !(words.get(3).equals("on") || words.get(3).equals("off"))) {
                    throw new IllegalArgumentException(usage);
                }
                if (control.on() == null) {
                    throw new IllegalArgumentException(control.name() + " is a button, not a toggle: press it");
                }
                boolean wanted = words.get(3).equals("on");
                if (control.on() == wanted) {
                    return control.name() + " was already " + words.get(3);
                }
                click(terminal, control);
                return control.name() + " set " + words.get(3);
            }
            default -> throw new IllegalArgumentException(usage);
        }
    }

    private static KriosTerminalScreen<?> terminal() {
        return Minecraft.getInstance().screen instanceof KriosTerminalScreen<?> terminal ? terminal : null;
    }

    private static Control find(KriosTerminalScreen<?> terminal, String name) {
        StringJoiner known = new StringJoiner(", ");
        for (Control control : terminal.controls()) {
            if (control.name().equals(name)) {
                return control;
            }
            known.add(control.name());
        }
        throw new IllegalArgumentException("this terminal has no control '" + name + "' (it has: " + known + ")");
    }

    /** The screen's own click handling, at the control's place: what the mouse would reach. */
    private static void click(KriosTerminalScreen<?> terminal, Control control) {
        terminal.mouseClicked(control.x(), control.y(), 0);
        terminal.mouseReleased(control.x(), control.y(), 0);
    }
}

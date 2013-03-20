package think.rpgitems.commands;

import gnu.trove.map.hash.TCharObjectHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.SimpleTimeZone;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import think.rpgitems.Plugin;
import think.rpgitems.data.Locale;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.Quality;
import think.rpgitems.item.RPGItem;

abstract public class Commands {
    private static HashMap<String, ArrayList<CommandDef>> commands = new HashMap<String, ArrayList<CommandDef>>();
    private static TCharObjectHashMap<Class<? extends CommandArgument>> argTypes = new TCharObjectHashMap<Class<? extends CommandArgument>>();

    static {
        argTypes.put('s', ArgumentString.class);
        argTypes.put('i', ArgumentInteger.class);
        argTypes.put('f', ArgumentDouble.class);
        argTypes.put('p', ArgumentPlayer.class);
        argTypes.put('o', ArgumentOption.class);
        argTypes.put('n', ArgumentItem.class);
        argTypes.put('m', ArgumentMaterial.class);
    }

    public static void exec(CommandSender sender, String com) {
        com = com.trim();
        if (com.length() == 0)
            return;
        String comName;
        int pos = com.indexOf(' ');
        if (pos == -1) {
            comName = com;
        } else {
            comName = com.substring(0, pos);
        }
        com = com.substring(pos + 1);

        ArrayList<CommandDef> command = commands.get(comName);
        if (command == null) {
            sender.sendMessage(ChatColor.RED + String.format(Locale.get("MESSAGE_ERROR_UNKNOWN_COMMAND"), comName));
            return;
        }

        if (pos == -1) {
            for (CommandDef c : command) {
                if (c.arguments.length == 0) {
                    c.command.command(sender, null);
                    return;
                }
            }
            // Print usage
            sender.sendMessage(String.format(ChatColor.GREEN + Locale.get("MESSAGE_COMMAND_USAGE"), comName, Plugin.plugin.getDescription().getVersion()));
            for (CommandDef c : command) {
                StringBuilder buf = new StringBuilder();
                buf.append(ChatColor.GREEN).append('/').append(comName);
                for (CommandArgument a : c.arguments) {
                    buf.append(' ');
                    if (a.name.length() != 0) {
                        buf.append(ChatColor.RED);
                        buf.append(Locale.get("COMMAND_INFO_" + a.name)).append(':');
                    }
                    buf.append(a.isConst() ? ChatColor.GREEN : ChatColor.GOLD);
                    buf.append(a.printable());
                }
                sender.sendMessage(buf.toString());
                String note = c.command.getNote();
                if (note != null) {
                    sender.sendMessage(ChatColor.GREEN + "- " + note);
                }
            }
            sender.sendMessage(ChatColor.GREEN + Locale.get("MESSAGE_COMMAND_INFO"));
            return;
        }
        ArrayList<String> args = new ArrayList<String>();
        while (true) {
            int end;
            if (com.length() == 0) {
                break;
            }
            boolean quote = false;
            if (com.charAt(0) == '`') {
                com = com.substring(1);
                end = com.indexOf('`');
                quote = true;
            } else {
                end = com.indexOf(' ');
            }
            if (end == -1) {
                args.add(com);
            } else {
                args.add(com.substring(0, end));
            }
            if (quote) {
                com = com.substring(end + 1);
                end = com.indexOf(' ');
            }
            if (end != -1) {
                com = com.substring(end + 1);
            } else {
                break;
            }
        }
        CommandError lastError = null;
        comLoop: for (CommandDef c : command) {
            if (c.arguments.length != args.size()) {
                if (c.arguments.length != 0 && c.arguments[c.arguments.length - 1] instanceof ArgumentString) {
                    if (args.size() < c.arguments.length)
                        continue;
                } else {
                    continue;
                }
            }
            ArrayList<Object> outArgs = new ArrayList<Object>();
            for (int i = 0; i < c.arguments.length; i++) {
                CommandArgument a = c.arguments[i];
                if (!a.isConst()) {
                    if (i == c.arguments.length - 1) {
                        // Special case for strings so they do not need to be quoted
                        if (a instanceof ArgumentString) {
                            StringBuilder joined = new StringBuilder();
                            for (int j = i; j < args.size(); j++) {
                                joined.append(args.get(j)).append(' ');
                            }
                            args.set(i, joined.toString().trim());
                        }
                    }
                    Object res = a.parse(args.get(i));
                    if (res instanceof CommandError) {
                        lastError = (CommandError) res;
                        continue comLoop;
                    }
                    outArgs.add(res);
                } else {
                    ArgumentConst cst = (ArgumentConst) a;
                    if (!Locale.get("COMMAND_CONST_" + cst.value).equals(args.get(i))) {
                        continue comLoop;
                    }
                }
            }
            c.command.command(sender, outArgs.toArray());
            return;
        }

        if (lastError != null) {
            sender.sendMessage(ChatColor.RED + String.format(Locale.get("MESSAGE_ERROR_COMMAND"), lastError.error));
        } else {
            ArrayList<String> consts = new ArrayList<String>();
            comLoop: for (CommandDef c : command) {
                /*
                 * if (c.arguments.length != args.size()) { if (c.arguments.length != 0 &&
                 * c.arguments[c.arguments.length-1] instanceof ArgumentString) { if (args.size() <
                 * c.arguments.length) continue; } else { continue; } }
                 */
                // ArrayList<Object> outArgs = new ArrayList<Object>();
                for (int i = 0; i < c.arguments.length; i++) {
                    if (i >= args.size())
                        break;
                    CommandArgument a = c.arguments[i];
                    if (!a.isConst()) {
                        if (i == c.arguments.length - 1) {
                            // Special case for strings so they do not need to be quoted
                            if (a instanceof ArgumentString) {
                                StringBuilder joined = new StringBuilder();
                                for (int j = i; j < args.size(); j++) {
                                    joined.append(args.get(j)).append(' ');
                                }
                                args.set(i, joined.toString().trim());
                            }
                        }
                        Object res = a.parse(args.get(i));
                        if (res instanceof CommandError) {
                            lastError = (CommandError) res;
                            continue comLoop;
                        }
                        // outArgs.add(res);
                    } else {
                        ArgumentConst cst = (ArgumentConst) a;
                        if (!Locale.get("COMMAND_CONST_" + cst.value).equals(args.get(i))) {
                            continue comLoop;
                        } else {
                            consts.add(cst.value);
                        }
                    }
                }
                // c.command.command(sender, outArgs.toArray());
            }
            StringBuilder search = new StringBuilder();
            for (String term : consts) {
                search.append(term).append(' ');
            }
            searchHelp(sender, search.toString());
        }
    }

    public static List<String> complete(String com) {
        com = com.trim();
        if (com.length() == 0) {
            return new ArrayList<String>();
        }
        String comName;
        int pos = com.indexOf(' ');
        if (pos == -1) {
            comName = com;
        } else {
            comName = com.substring(0, pos);
        }
        com = com.substring(pos + 1);

        ArrayList<CommandDef> command = commands.get(comName);

        if (command == null) {
            if (pos == -1) {
                ArrayList<String> out = new ArrayList<String>();
                for (String n : commands.keySet()) {
                    if (n.startsWith(comName)) {
                        out.add("/" + n);
                    }
                }
                return out;
            }
            return new ArrayList<String>();
        }
        ArrayList<String> args = new ArrayList<String>();
        while (true) {
            int end;
            if (com.length() == 0) {
                break;
            }
            boolean quote = false;
            if (com.charAt(0) == '`') {
                com = com.substring(1);
                end = com.indexOf('`');
                quote = true;
            } else {
                end = com.indexOf(' ');
            }
            if (end == -1) {
                args.add(com);
            } else {
                args.add(com.substring(0, end));
            }
            if (quote) {
                com = com.substring(end + 1);
                end = com.indexOf(' ');
            }
            if (end != -1) {
                com = com.substring(end + 1);
            } else {
                break;
            }
        }
        HashMap<String, Boolean> out = new HashMap<String, Boolean>();

        comLoop: for (CommandDef c : command) {
            for (int i = 0; i < c.arguments.length; i++) {
                CommandArgument a = c.arguments[i];
                if (i == args.size() - 1) {
                    List<String> res = a.tabComplete(args.get(i));
                    if (res != null) {
                        for (String s : res) {
                            out.put(s, true);
                        }
                        continue comLoop;
                    }
                } else {
                    if (!a.isConst()) {
                        Object res = a.parse(args.get(i));
                        if (res instanceof CommandError) {
                            continue comLoop;
                        }
                    } else {
                        ArgumentConst cst = (ArgumentConst) a;
                        if (!Locale.get("COMMAND_CONST_" + cst.value).equals(args.get(i))) {
                            continue comLoop;
                        }
                    }
                }
            }
        }
        ArrayList<String> outList = new ArrayList<String>();
        for (String s : out.keySet()) {
            outList.add(s);
        }
        return outList;
    }

    public static void add(String com, Commands callback) {
        com = com.trim();
        int pos = com.indexOf(' ');
        String comName;
        if (pos == -1) {
            comName = com;
        } else {
            comName = com.substring(0, pos);
        }

        CommandDef def = new CommandDef();
        def.commandString = com;
        def.command = callback;
        if (!commands.containsKey(comName)) {
            commands.put(comName, new ArrayList<CommandDef>());
        }
        commands.get(comName).add(def);
        if (pos == -1) {
            def.arguments = new CommandArgument[0];
            return;
        }
        com = com.substring(pos + 1);
        ArrayList<CommandArgument> arguments = new ArrayList<CommandArgument>();
        while (true) {
            pos = com.indexOf(' ');
            String a;
            if (pos == -1) {
                a = com;
            } else {
                a = com.substring(0, pos);
                com = com.substring(pos + 1);
            }
            if (a.charAt(0) == '$') { // Variable
                String name = "";
                if (a.contains(":")) {
                    String[] as = a.split(":");
                    name = as[0].substring(1);
                    a = "$" + as[1];
                }
                char t = a.charAt(1);
                Class<? extends CommandArgument> cAT = argTypes.get(t);
                if (cAT == null) {
                    throw new RuntimeException("Invalid command argument type");
                }
                CommandArgument arg;
                try {
                    arg = cAT.newInstance();
                    arg.init(a.substring(3, a.length() - 1));
                    arg.name = name;
                    arguments.add(arg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else { // Const
                arguments.add(new ArgumentConst(a.toUpperCase()));
            }
            if (pos == -1) {
                break;
            }
        }

        def.arguments = new CommandArgument[arguments.size()];
        arguments.toArray(def.arguments);
    }

    static {
        add("rpgitem help $TERMS:s[]", new Commands() {

            @Override
            public String getDocs() {
                return Locale.get("COMMAND_RPGITEM_HELP");
            }

            @Override
            public void command(CommandSender sender, Object[] args) {
                searchHelp(sender, (String) args[0]);
            }
        });
    }

    public static void searchHelp(CommandSender sender, String terms) {
        if (terms.equalsIgnoreCase("_genhelp")) {
            generateHelp();
            return;
        }
        sender.sendMessage(ChatColor.GREEN + String.format(Locale.get("MESSAGE_HELP_FOR"), terms));
        String[] term = terms.toLowerCase().split(" ");
        for (Entry<String, ArrayList<CommandDef>> command : commands.entrySet()) {
            for (CommandDef c : command.getValue()) {
                int count = 0;
                for (String t : term) {
                    if (c.commandString.toLowerCase().contains(t)) {
                        count++;
                    }
                }
                if (count == term.length) {
                    StringBuilder buf = new StringBuilder();
                    buf.append(ChatColor.GREEN).append(ChatColor.BOLD).append('/').append(command.getKey());
                    for (CommandArgument a : c.arguments) {
                        buf.append(' ');
                        if (a.name.length() != 0) {
                            buf.append(ChatColor.RED).append(ChatColor.BOLD);
                            buf.append(Locale.get("COMMAND_INFO_" + a.name)).append(':');
                        }
                        buf.append(a.isConst() ? ChatColor.GREEN : ChatColor.GOLD).append(ChatColor.BOLD);
                        buf.append(a.printable());
                    }
                    sender.sendMessage(buf.toString());
                    String docStr = c.command.getDocs().replaceAll("@", "" + ChatColor.BLUE).replaceAll("#", "" + ChatColor.WHITE);
                    StringBuilder docBuf = new StringBuilder();
                    char[] chars = docStr.toCharArray();
                    docBuf.append(ChatColor.WHITE);
                    for (int i = 0; i < chars.length; i++) {
                        char l = chars[i];
                        if (l == '&') {
                            i++;
                            l = chars[i];
                            switch (l) {
                            case '0':
                                docBuf.append(ChatColor.BLACK);
                                break;
                            case '1':
                                docBuf.append(ChatColor.DARK_BLUE);
                                break;
                            case '2':
                                docBuf.append(ChatColor.DARK_GREEN);
                                break;
                            case '3':
                                docBuf.append(ChatColor.DARK_AQUA);
                                break;
                            case '4':
                                docBuf.append(ChatColor.DARK_RED);
                                break;
                            case '5':
                                docBuf.append(ChatColor.DARK_PURPLE);
                                break;
                            case '6':
                                docBuf.append(ChatColor.GOLD);
                                break;
                            case '7':
                                docBuf.append(ChatColor.GRAY);
                                break;
                            case '8':
                                docBuf.append(ChatColor.DARK_GRAY);
                                break;
                            case '9':
                                docBuf.append(ChatColor.BLUE);
                                break;
                            case 'a':
                                docBuf.append(ChatColor.GREEN);
                                break;
                            case 'b':
                                docBuf.append(ChatColor.AQUA);
                                break;
                            case 'c':
                                docBuf.append(ChatColor.RED);
                                break;
                            case 'd':
                                docBuf.append(ChatColor.LIGHT_PURPLE);
                                break;
                            case 'e':
                                docBuf.append(ChatColor.YELLOW);
                                break;
                            case 'f':
                                docBuf.append(ChatColor.WHITE);
                                break;
                            case 'r':
                                docBuf.append(ChatColor.WHITE);
                                break;
                            }
                        } else {
                            docBuf.append(l);
                        }
                    }
                    sender.sendMessage(docBuf.toString());
                }
            }
        }
    }

    public static void generateHelp() {
        BufferedWriter w = null;
        try {
            File out = new File(Plugin.plugin.getDataFolder(), "help.txt");
            if (out.exists()) {
                out.delete();
            }
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
            for (Entry<String, ArrayList<CommandDef>> command : commands.entrySet()) {
                w.write(String.format("== **Commands /%s** ==", command.getKey()));
                w.write("\n\n\\\\\n");
                for (CommandDef c : command.getValue()) {
                    StringBuilder buf = new StringBuilder();
                    buf.append("=== **/");
                    buf.append(command.getKey()).append(" ");
                    for (CommandArgument a : c.arguments) {
                        if (a.name.length() != 0) {
                            buf.append("<<color 006EFF>>");
                            buf.append(Locale.get("COMMAND_INFO_" + a.name));
                            buf.append("<</color>>:");
                        }
                        if (a.isConst())
                            buf.append("<<color 000000>>");
                        else
                            buf.append("<<color 0000ff>>");
                        buf.append(a.printable());
                        buf.append("<</color>> ");
                    }
                    buf.append("**===\n");
                    String docStr = c.command.getDocs().replaceAll("@", "<<color 0000ff>>").replaceAll("#", "<</color>>");
                    StringBuilder docBuf = new StringBuilder();
                    char[] chars = docStr.toCharArray();
                    for (int i = 0; i < chars.length; i++) {
                        char l = chars[i];
                        if (l == '&') {
                            i++;
                            l = chars[i];
                            if (l != 'r') {
                                docBuf.append("<<color ");
                            }
                            switch (l) {
                            case '0':
                                docBuf.append("000000");
                                break;
                            case '1':
                                docBuf.append("0000aa");
                                break;
                            case '2':
                                docBuf.append("00aa00");
                                break;
                            case '3':
                                docBuf.append("00aaaa");
                                break;
                            case '4':
                                docBuf.append("aa0000");
                                break;
                            case '5':
                                docBuf.append("aa00aa");
                                break;
                            case '6':
                                docBuf.append("ffaa00");
                                break;
                            case '7':
                                docBuf.append("aaaaaa");
                                break;
                            case '8':
                                docBuf.append("555555");
                                break;
                            case '9':
                                docBuf.append("5555ff");
                                break;
                            case 'a':
                                docBuf.append("55ff55");
                                break;
                            case 'b':
                                docBuf.append("55ffff");
                                break;
                            case 'c':
                                docBuf.append("ff5555");
                                break;
                            case 'd':
                                docBuf.append("ff55ff");
                                break;
                            case 'e':
                                docBuf.append("ffff55");
                                break;
                            case 'f':
                                docBuf.append("ffffff");
                                break;
                            case 'r':
                                docBuf.append("<</color");
                                break;
                            }
                            docBuf.append(">>");
                        } else {
                            docBuf.append(l);
                        }
                    }
                    buf.append(docBuf.toString());
                    buf.append("\n\\\\\n");
                    w.write(buf.toString());
                }
            }
            w.write("\n\n\\\\\\\\");
            w.write("Generated at: ");
            SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.setTimeZone(new SimpleTimeZone(0, "GMT"));
            sdf.applyPattern("dd MMM yyyy HH:mm:ss z");
            w.write(sdf.format(new Date()));

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public abstract void command(CommandSender sender, Object[] args);

    public abstract String getDocs();

    public String getNote() {
        return null;
    }
}

class CommandDef {
    public String commandString;
    public Commands command;
    public CommandArgument[] arguments;
}

abstract class CommandArgument {
    public abstract void init(String a);

    public abstract Object parse(String in);

    public abstract List<String> tabComplete(String in);

    public abstract String printable();

    public String name = "";

    public boolean isConst() {
        return false;
    }
}

class CommandError {

    public String error;

    public CommandError(String error) {
        this.error = error;
    }
}

class ArgumentInteger extends CommandArgument {

    private boolean hasLimits;
    private int min;
    private int max;

    @Override
    public void init(String a) {
        if (a.length() == 0) {
            hasLimits = false;
        } else {
            hasLimits = true;
            String[] args = a.split(",");
            if (args.length != 2) {
                throw new RuntimeException("ArgumentInteger limits errror");
            }
            min = Integer.parseInt(args[0]);
            max = Integer.parseInt(args[1]);
        }
    }

    @Override
    public Object parse(String in) {
        if (hasLimits) {
            try {
                int i = Integer.parseInt(in);
                if (i < min || i > max) {
                    return new CommandError(String.format(Locale.get("MESSAGE_ERROR_INTEGER_LIMIT"), min, max));
                }
                return i;
            } catch (NumberFormatException e) {
                return new CommandError(String.format(Locale.get("MESSAGE_ERROR_INTEGER_FORMAT"), in));
            }
        } else {
            try {
                int i = Integer.parseInt(in);
                return i;
            } catch (NumberFormatException e) {
                return new CommandError(String.format(Locale.get("MESSAGE_ERROR_INTEGER_FORMAT"), in));
            }
        }
    }

    @Override
    public List<String> tabComplete(String in) {
        return new ArrayList<String>();
    }

    @Override
    public String printable() {
        if (hasLimits) {
            return String.format(Locale.get("COMMAND_INFO_INTEGER_LIMIT"), min, max);
        }
        return Locale.get("COMMAND_INFO_INTEGER");
    }

}

class ArgumentDouble extends CommandArgument {

    private boolean hasLimits;
    private double min;
    private double max;

    @Override
    public void init(String a) {
        if (a.length() == 0) {
            hasLimits = false;
        } else {
            hasLimits = true;
            String[] args = a.split(",");
            if (args.length != 2) {
                throw new RuntimeException("ArgumentDouble limits errror");
            }
            min = Double.parseDouble(args[0]);
            max = Double.parseDouble(args[1]);
        }
    }

    @Override
    public Object parse(String in) {
        if (hasLimits) {
            try {
                double i = Double.parseDouble(in);
                if (i < min || i > max) {
                    return new CommandError(String.format(Locale.get("MESSAGE_ERROR_DOUBLE_LIMIT"), min, max));
                }
                return i;
            } catch (NumberFormatException e) {
                return new CommandError(String.format(Locale.get("MESSAGE_ERROR_DOUBLE_FORMAT"), in));
            }
        } else {
            try {
                double i = Double.parseDouble(in);
                return i;
            } catch (NumberFormatException e) {
                return new CommandError(String.format(Locale.get("MESSAGE_ERROR_DOUBLE_FORMAT"), in));
            }
        }
    }

    @Override
    public List<String> tabComplete(String in) {
        return new ArrayList<String>();
    }

    @Override
    public String printable() {
        if (hasLimits) {
            return String.format(Locale.get("COMMAND_INFO_DOUBLE_LIMIT"), min, max);
        }
        return Locale.get("COMMAND_INFO_DOUBLE");
    }

}

class ArgumentString extends CommandArgument {

    private int maxLength;

    @Override
    public void init(String a) {
        if (a.length() == 0) {
            maxLength = 0;
        } else {
            maxLength = Integer.parseInt(a);
        }
    }

    @Override
    public Object parse(String in) {
        if (maxLength != 0 && in.length() > maxLength)
            return new CommandError(String.format(Locale.get("MESSAGE_ERROR_STRING_LENGTH"), in, maxLength));
        return in;
    }

    @Override
    public List<String> tabComplete(String in) {
        return new ArrayList<String>();
    }

    @Override
    public String printable() {
        if (maxLength != 0)
            return String.format(Locale.get("COMMAND_INFO_STRING_LIMIT"), maxLength);
        return Locale.get("COMMAND_INFO_STRING");
    }

}

class ArgumentConst extends CommandArgument {

    public String value;

    public ArgumentConst(String v) {
        value = v;
    }

    @Override
    public void init(String a) {
        throw new RuntimeException("Const cannot be init'ed");
    }

    @Override
    public Object parse(String in) {
        return null;
    }

    @Override
    public List<String> tabComplete(String in) {
        ArrayList<String> a = new ArrayList<String>();
        String lValue = Locale.get("COMMAND_CONST_" + value);
        if (lValue.startsWith(in))
            a.add(lValue);
        return a;
    }

    @Override
    public String printable() {
        return Locale.get("COMMAND_CONST_" + value);
    }

    @Override
    public boolean isConst() {
        return true;
    }
}

class ArgumentPlayer extends CommandArgument {

    @Override
    public void init(String a) {
    }

    @Override
    public Object parse(String in) {
        Player player = Bukkit.getPlayer(in);
        if (player == null)
            return new CommandError(String.format(Locale.get("MESSAGE_ERROR_PLAYER"), in));
        return player;
    }

    @Override
    public List<String> tabComplete(String in) {
        List<Player> players = Bukkit.matchPlayer(in);
        ArrayList<String> out = new ArrayList<String>();
        for (Player player : players) {
            out.add(player.getName());
        }
        return out;
    }

    @Override
    public String printable() {
        return Locale.get("COMMAND_INFO_PLAYER");
    }

}

class ArgumentOption extends CommandArgument {

    private String[] options;
    private String shortVersion = "";

    @Override
    public void init(String a) {
        if (a.contains("@")) {
            String[] args = a.split("@");
            shortVersion = args[0];
            a = args[1];

        }
        options = a.split(",");
        for (int i = 0; i < options.length; i++) {
            options[i] = options[i].trim();
        }
    }

    @Override
    public Object parse(String in) {
        for (String o : options) {
            if (o.equalsIgnoreCase(in)) {
                return o;
            }
        }
        return new CommandError(String.format(Locale.get("MESSAGE_ERROR_OPTION"), in));
    }

    @Override
    public List<String> tabComplete(String in) {
        ArrayList<String> out = new ArrayList<String>();
        in = in.toLowerCase();
        for (String o : options) {
            if (o.startsWith(in)) {
                out.add(o);
            }
        }
        return out;
    }

    @Override
    public String printable() {
        if (shortVersion.length() == 0) {
            StringBuilder out = new StringBuilder();
            out.append('[');
            for (int i = 0; i < options.length; i++) {
                out.append(options[i]).append(i == options.length - 1 ? ']' : ',');
            }
            return out.toString();
        } else {
            return "[" + shortVersion + "]";
        }
    }

}

class ArgumentItem extends CommandArgument {

    @Override
    public void init(String a) {

    }

    @Override
    public Object parse(String in) {
        RPGItem item = ItemManager.getItemByName(in);
        if (item == null) {
            return new CommandError(String.format(Locale.get("MESSAGE_ERROR_ITEM"), in));
        }
        return item;
    }

    @Override
    public List<String> tabComplete(String in) {
        ArrayList<String> out = new ArrayList<String>();
        for (String i : ItemManager.itemByName.keySet()) {
            if (i.startsWith(in)) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    public String printable() {
        return Locale.get("COMMAND_INFO_ITEM");
    }

}

class ArgumentMaterial extends CommandArgument {

    @Override
    public void init(String a) {

    }

    @Override
    public Object parse(String in) {
        Material mat = Material.matchMaterial(in);
        if (mat == null) {
            return new CommandError(String.format(Locale.get("MESSAGE_ERROR_MATERIAL"), in));
        }
        return mat;
    }

    @Override
    public List<String> tabComplete(String in) {
        ArrayList<String> out = new ArrayList<String>();
        String it = in.toUpperCase();
        for (Material m : Material.values()) {
            if (m.toString().startsWith(it)) {
                out.add(m.toString());
            }
        }
        return out;
    }

    @Override
    public String printable() {
        return Locale.get("COMMAND_INFO_MATERIAL");
    }

}
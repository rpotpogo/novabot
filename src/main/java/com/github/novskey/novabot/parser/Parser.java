package com.github.novskey.novabot.parser;

import com.github.novskey.novabot.Util.StringLocalizer;
import com.github.novskey.novabot.core.Location;
import com.github.novskey.novabot.core.LocationType;
import com.github.novskey.novabot.core.NovaBot;
import com.github.novskey.novabot.core.TimeUnit;
import com.github.novskey.novabot.maps.GeofenceIdentifier;
import com.github.novskey.novabot.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Parser {
    private static final Pattern PATTERN = Pattern.compile("([a-zA-Z0-9.'-]+)|!?\\w+|<(.*?)>");
    private static final Pattern LEVEL_PATTERN = Pattern.compile("^\\w*l[0-9]+");
    private static final Pattern ONLY_NUMBERS = Pattern.compile("[0-9]+");
    private static final Pattern CP_PATTERN = Pattern.compile("(cp[0-9]+)|([0-9]+cp)");
    private static final Pattern IV_PATTERN = Pattern.compile("iv[0-9]+|([0-9]+iv)|[0-9]+");
    private static final Pattern ATKIV_PATTERN = Pattern.compile("atk[0-9]+|([0-9]+atk)|a[0-9]+|([0-9]+a)");
    private static final Pattern DEFIV_PATTERN = Pattern.compile("def[0-9]+|([0-9]+def)|d[0-9]+|([0-9]+d)");
    private static final Pattern STAIV_PATTERN = Pattern.compile("sta[0-9]+|([0-9]+sta)|s[0-9]+|([0-9]+s)");
    private static final Pattern PVPGREATRANK_PATTERN = Pattern.compile("great[0-9]+|([0-9]+great)|g[0-9]+|([0-9]+g)");
    private static final Pattern PVPULTRARANK_PATTERN = Pattern.compile("ultra[0-9]+|([0-9]+ultra)|u[0-9]+|([0-9]+u)");
    private static final Pattern EGG_PATTERN = Pattern.compile("egg[1-5]");
	private static final Pattern POKE_PATTERN = Pattern.compile("p[0-9]+|pid[0-9]+|poke[0-9]+|pokemon[0-9]+");

    private final NovaBot novaBot;

    public Parser(NovaBot novaBot) {
        this.novaBot = novaBot;
    }

    public UserCommand parseInput(String input) {
        input = input.toLowerCase();
        final UserCommand command = new UserCommand(novaBot);
        final Argument[] args = getArgs(input);
        final String firstArg = (String) args[0].getParams()[0];
        command.setArgs(args);
        if (!novaBot.commands.isCommandWithArgs(firstArg)) {
            command.addException(InputError.InvalidCommand);
            return command;
        }
        final Command cmd = novaBot.commands.get(firstArg);
        for (final Argument arg : args) {
            if (arg.notFullyParsed()) {
                command.addException(InputError.MalformedArg);
                return command;
            }
        }
        if (!cmd.meetsRequirements(args)) {
            command.addException(InputError.MissingRequiredArg);
            return command;
        }
        if (!cmd.allowDuplicateArgs && Argument.containsDuplicates(args)) {
            command.addException(InputError.DuplicateArgs);
            return command;
        }
        if(!cmd.validCombination(command.getArgTypes())){
            command.addException(InputError.InvalidArgCombination);
            return command;
        }

        if (!args[0].getParams()[0].equals("!nest") && command.containsArg(ArgType.Pokemon) && command.containsBlacklisted()) {
            command.addException(InputError.BlacklistedPokemon);
            return command;
        }

        for (final Argument arg : args) {
            if(arg.getType() == ArgType.CommandStr) continue;
            if (!cmd.validArgTypes.contains(arg.getType())) {
                command.addException(InputError.InvalidArg);
                return command;
            }
        }
        return command;
    }

    //Helper routine for getArg
    private boolean getArgSpecificIV(Argument argument, String trimmed) {
    	if (ATKIV_PATTERN.matcher(trimmed).matches()) {
    		argument.setType(ArgType.ATKIV);
    	} else if (DEFIV_PATTERN.matcher(trimmed).matches()) {
    		argument.setType(ArgType.DEFIV);
    	} else if (STAIV_PATTERN.matcher(trimmed).matches()) {
    		argument.setType(ArgType.STAIV);
    	} else {
    		return false;
    	}
        Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
        if (matcher.find()){
            argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
        }
    	return true;
    }

    private Argument getArg(final String s, Command command) {
        String           trimmed  = s.trim();
        final Argument   argument = new Argument();
        HashSet<ArgType> valid    = (command == null) ? new HashSet<>() : command.getValidArgTypes();
        if (novaBot.commands.isCommandWithArgs(trimmed)) {
            argument.setType(ArgType.CommandStr);
            argument.setParams(new Object[]{s});
        } else if (valid.contains(ArgType.Pokemon) && Pokemon.nameToID(trimmed) != 0) {
            argument.setType(ArgType.Pokemon);
            argument.setParams(new Object[]{trimmed});
        } else if (valid.contains(ArgType.Pokemon) && POKE_PATTERN.matcher(trimmed).matches()) {
            argument.setType(ArgType.Pokemon);
            argument.setParams(new Object[]{Pokemon.idToName(getInt(trimmed.replaceAll("[^\\d.]", "")))});
        } else if (valid.contains(ArgType.Egg) && EGG_PATTERN.matcher(trimmed).matches()) {
            argument.setType(ArgType.Egg);
            Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
            if (matcher.find()) {
                argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
            }
        } else {
            final Location location;
            if (valid.contains(ArgType.Locations) && (location = Location.fromString(trimmed, novaBot)) != null) {
                argument.setType(ArgType.Locations);

                ArrayList<Location> locations = new ArrayList<>();

                if (location.locationType == LocationType.Geofence) {
                    locations.addAll(location.geofenceIdentifiers.stream().map(Location::new).collect(Collectors.toList()));
                }

                if (locations.size() == 0) {
                    locations.add(location);
                }
                argument.setParams(locations.toArray());
            } else if (valid.contains(ArgType.GymName) && novaBot.dataManager.getGymNames().contains(trimmed)){
                argument.setType(ArgType.GymName);
                argument.setParams(new Object[]{trimmed});
            } else if (valid.contains(ArgType.CP) && CP_PATTERN.matcher(trimmed).matches()) {
                argument.setType(ArgType.CP);
                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                if (matcher.find()) {
                    argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
                }
            } else if (valid.contains(ArgType.CommandName) && novaBot.commands.validName(trimmed)){
                argument.setType(ArgType.CommandName);
                argument.setParams(new String[]{trimmed});
            } else if (valid.contains(ArgType.Level) && LEVEL_PATTERN.matcher(trimmed).matches()) {
                argument.setType(ArgType.Level);
                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                if (matcher.find()) {
                    argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
                }
            } else if (valid.contains(ArgType.IV) && IV_PATTERN.matcher(trimmed).matches()){
                argument.setType(ArgType.IV);
                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                if (matcher.find()){
                    argument.setParams(new Object[]{Float.valueOf(matcher.group())});
                }
            } else if (valid.contains(ArgType.PVPGreatRank) && PVPGREATRANK_PATTERN.matcher(trimmed).matches()){
                argument.setType(ArgType.PVPGreatRank);
                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                if (matcher.find()){
                    argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
                }
            } else if (valid.contains(ArgType.PVPUltraRank) && PVPULTRARANK_PATTERN.matcher(trimmed).matches()){
                argument.setType(ArgType.PVPUltraRank);
                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                if (matcher.find()){
                    argument.setParams(new Object[]{Integer.valueOf(matcher.group())});
                }
            } else if (valid.contains(ArgType.ATKIV) && getArgSpecificIV(argument, trimmed)){
            } else if (valid.contains(ArgType.TimeUnit) && TimeUnit.fromString(trimmed) != null) {
                argument.setType(ArgType.TimeUnit);
                argument.setParams(new Object[]{TimeUnit.fromString(trimmed)});
            }else if (valid.contains(ArgType.Preset) && novaBot.getConfig().getPresets().get(trimmed) != null) {
                argument.setType(ArgType.Preset);
                argument.setParams(new Object[]{trimmed});
            } else if (valid.contains(ArgType.Int) && getInt(trimmed.replace("iv","")) != null) {
                argument.setType(ArgType.Int);
                argument.setParams(new Object[]{getInt(trimmed.replace("iv",""))});
            } else if (valid.contains(ArgType.Float) && getFloat(trimmed.replace("iv","")) != null) {
                argument.setType(ArgType.Float);
                argument.setParams(new Object[]{getFloat(trimmed.replace("iv",""))});
            } else {
                argument.setType(ArgType.Unknown);
                argument.setParams(new Object[]{null});
                argument.setMalformed(new ArrayList<>(Collections.singletonList(s)));
            }
        }
        return argument;
    }

    private Argument[] getArgs(final String input) {
        final ArrayList<Argument> args    = new ArrayList<>();
        final Matcher             matcher = Parser.PATTERN.matcher(input);
        Command command = null;
        while (matcher.find()) {
            final String group = matcher.group();
            if (group.charAt(0) == '<') {
                args.add(parseList(group, command));
            } else {
                Argument arg = getArg(group, command);
                if (arg.getType() == ArgType.CommandStr){
                    command = novaBot.commands.get(group);
                }
                args.add(getArg(group, command));
            }
        }
        final Argument[] arguments = new Argument[args.size()];
        return args.toArray(arguments);
    }


    private static Integer getInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float getFloat(final String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Argument parseList(final String group, Command command) {
        final Argument argument = new Argument();
        final String toSplit = group.substring(1, group.length() - 1);
        final String[] strings = toSplit.split(",");
        final ArrayList<Object> args = new ArrayList<>();
        final ArrayList<String> malformed = new ArrayList<>();

        for (ArgType argType : command.getValidArgTypes()) {
            args.clear();
            malformed.clear();
            for (String string : strings) {
                String trimmed = string.trim();
                switch (argType) {
                    case Locations:
                        Location loc = Location.fromString(trimmed, novaBot);

                        if (loc == null) {
                            malformed.add(string);
                            args.add(null);
                            continue;
                        }

                        if (loc.locationType == LocationType.Geofence) {
                            for (GeofenceIdentifier geofenceIdentifier : loc.geofenceIdentifiers) {
                                args.add(new Location(geofenceIdentifier));
                            }
                        } else {
                            args.add(loc);
                        }
                        break;
                    case GymName:
                        if (novaBot.dataManager.getGymNames().contains(trimmed)) {
                            args.add(trimmed);
                        }else{
                            malformed.add(trimmed);
                            args.add(null);
                        }
                        break;
                    case Pokemon:
                        final Pokemon pokemon;
                        if (POKE_PATTERN.matcher(trimmed).matches()) {
                            pokemon = new Pokemon(getInt(trimmed.replaceAll("[^\\d.]", "")));
                        }
                        else {
                            pokemon = new Pokemon(trimmed);
                        }
                        if (pokemon.name == null) {
                            malformed.add(string);
                        }
                        args.add(pokemon.name);
                        break;
                    case Preset:
                        String presetFilter = novaBot.getConfig().getPresets().get(string.trim());
                        args.add(string.trim());
                        if (presetFilter == null) {
                            malformed.add(string.trim());
                        }
                        break;
                    case CP: case ATKIV: case DEFIV: case STAIV:
                        if (strings.length == 1 || strings.length == 2) {
                        	Pattern pattern;
                        	pattern = (argType == ArgType.CP) ? CP_PATTERN : 
                        		(argType == ArgType.ATKIV) ? ATKIV_PATTERN : 
                        			(argType == ArgType.DEFIV) ? DEFIV_PATTERN : STAIV_PATTERN;
                            if (pattern.matcher(trimmed).matches()) {
                                argument.setType(argType);
                                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                                if (matcher.find()) {
                                    args.add(Integer.valueOf(matcher.group()));
                                }
                            } else {
                                malformed.add(string);
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                    case Level:
                        if(command.equals(novaBot.commands.get("AddRaidCommand")) || command.equals(novaBot.commands.get("DelRaidCommand"))){
                            if (strings.length > 2){
                                malformed.add(string);
                            }
                        }
                        if (LEVEL_PATTERN.matcher(trimmed).matches()) {
                            argument.setType(ArgType.Level);
                            Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                            if (matcher.find()) {
                                args.add(Integer.valueOf(matcher.group()));
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                    case IV:
                        if (strings.length == 1 || strings.length == 2) {
                            if (IV_PATTERN.matcher(trimmed).matches()) {
                                argument.setType(ArgType.IV);
                                Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                                if (matcher.find()) {
                                    args.add(Float.valueOf(matcher.group()));
                                }
                            } else {
                                malformed.add(string);
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                    case Egg:
                        if (EGG_PATTERN.matcher(trimmed).matches()) {
                            argument.setType(ArgType.Egg);
                            Matcher matcher = ONLY_NUMBERS.matcher(trimmed);
                            if (matcher.find()) {
                                args.add(Integer.valueOf(matcher.group()));
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                    case Float:
                        if (strings.length == 1 || strings.length == 2) {
                            Float aFloat = getFloat(trimmed);
                            if (aFloat != null) {
                                args.add(getFloat(trimmed));
                            } else {
                                malformed.add(string);
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                    case Int:
                        if (strings.length == 1 || strings.length == 2) {
                            Integer integer = getInt(trimmed);
                            if (integer != null) {
                                args.add(getInt(trimmed));
                            } else {
                                malformed.add(string);
                            }
                        } else {
                            malformed.add(string);
                        }
                        break;
                }
            }
            if (args.size() > 0 && strings.length != malformed.size()) {
                argument.setType(argType);
                break;
            }
        }

        if (argument.getType() != null) {
            argument.setParams(args.toArray());
            argument.setMalformed(malformed);
        } else {
            argument.setType(ArgType.Unknown);
            argument.setParams(args.toArray());
            argument.setMalformed(new ArrayList<>(Arrays.asList(strings)));
        }
        return argument;
    }

    private static boolean allNull(final Object[] args) {
        for (final Object arg : args) {
            if (arg != null) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        NovaBot novaBot = new NovaBot();
        novaBot.setup();

        System.out.println(StringLocalizer.getLocalString("StatusDescription"));

        String[] testStrings = new String [] {"!addpokemon marill <10a,15a> <10d,15d> <10h,15h>", "!addpokemon Ralts <90iv, 100iv>","!addpokemon ralts", "!addpokemon ralts 90iv", "!addpokemon ralts 90", "!addpokemon ralts iv90", "!addpokemon ralts <iv90,iv99>","!addraid egg5", "!addraid", "!help", "!help addpokemon"};

        for (String testString : testStrings) {
            System.out.println(testString);
            UserCommand command = novaBot.parser.parseInput(testString);
            System.out.println(command.getExceptions());
        }

        UserCommand command = novaBot.parser.parseInput("!addraid <egg1,level1>");
        System.out.println(command.getExceptions());
        System.out.println(command.buildRaids());

    }

}

package com.haberdashervcs.client.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;
import com.haberdashervcs.client.branch.BranchCommand;
import com.haberdashervcs.client.checkout.CheckoutCommand;
import com.haberdashervcs.client.checkout.SyncCommand;
import com.haberdashervcs.client.commit.CommitCommand;
import com.haberdashervcs.client.diff.DiffCommand;
import com.haberdashervcs.client.git.ImportGitCommand;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalRepoState;
import com.haberdashervcs.client.localdb.sqlite.SqliteLocalDb;
import com.haberdashervcs.client.push.PushCommand;
import com.haberdashervcs.client.rebase.RebaseCommand;
import com.haberdashervcs.common.exceptions.HdNormalError;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;


// Test comment for diff
public final class Commands {

    private static final HdLogger LOG = HdLoggers.create(Commands.class);


    private Commands() {
    }


    public static Command parseFromArgs(String[] args) throws Exception {
        ParsedArgs parsed = parseArgsFromMain(args);

        for (FlagValue fv : parsed.flags) {
            // TODO: Come up with some flags.
            throw new HdNormalError("Unknown flag: --" + fv.key);
        }

        if (parsed.args.size() == 0) {
            return new HelpCommand();
        }

        final String commandWord = parsed.args.get(0);
        List<String> otherArgs = parsed.args.subList(1, parsed.args.size());

        Optional<RepoConfig> config = RepoConfig.find();

        if (commandWord.equals("help")) {
            return new HelpCommand();

        } else if (commandWord.equals("version")) {
            return new VersionCommand();

        } else if (commandWord.equals("init")) {
            if (config.isPresent()) {
                throw new HdNormalError("Can't init: This is already an hd repo.");
            } else {
                return new InitCommand(otherArgs);
            }
        } else if (!config.isPresent()) {
            throw new HdNormalError("This is not an hd repo.");
        }


        LocalDb db = SqliteLocalDb.inRepo(config.get());
        db.startTransaction();

        List<String> checkedOutPaths = db.getGlobalCheckedOutPaths().toList();
        if (checkedOutPaths.size() == 0 && !commandWord.equals("checkout")) {
            throw new HdNormalError("No paths are checked out. Use the 'checkout' command to add a path from the server to your local repo.");
        }

        LocalRepoState repoState = db.getRepoState();
        if (repoState.getState() == LocalRepoState.State.REBASE_IN_PROGRESS) {
            if (!commandWord.equals("rebase") && !commandWord.equals("status") && !commandWord.equals("diff")) {
                throw new HdNormalError("A rebase is currently in progress. Please run 'rebase commit' when you're done, or 'rebase cancel'.");
            } else {
                LOG.info("(Rebase in progress from %s.)\n", repoState.getRebaseCommitBeingIntegrated());
            }
        }

        db.finishTransaction();


        if (commandWord.equals("push")) {
            return new PushCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("commit")) {
            return CommitCommand.fromCommandLine(config.get(), db, otherArgs);

        } else if (commandWord.equals("import_git")) {
            return new ImportGitCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("checkout")) {
            return new CheckoutCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("sync")) {
            return new SyncCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("rebase")) {
            return new RebaseCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("diff")) {
            return new DiffCommand(config.get(), otherArgs, db);

        } else if (commandWord.equals("branch")) {
            return new BranchCommand(config.get(), db, otherArgs);

        } else if (commandWord.equals("status")) {
            return new StatusCommand(config.get(), db, otherArgs);

        } else if (commandWord.equals("log")) {
            return new LogCommand(config.get(), db, otherArgs);

        } else if (commandWord.equals("merge")) {
            return new MergeCommand(config.get(), db, otherArgs);

        } else if (commandWord.equals("inspect")) {
            return new InspectCommand(config.get(), db, otherArgs);
        }

        throw new HdNormalError("Unknown command: " + commandWord);
    }


    private static class FlagValue {
        String key;
        String value;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("key", key)
                    .add("value", value)
                    .toString();
        }
    }

    private static class ParsedArgs {
        List<String> args;
        List<FlagValue> flags;

        private ParsedArgs() {
            args = new ArrayList<>();
            flags = new ArrayList<>();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("args", args)
                    .add("flags", flags)
                    .toString();
        }
    }

    static ParsedArgs parseArgsFromMain(String[] args) {
        ParsedArgs out = new ParsedArgs();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split(Pattern.quote("="), 2);
                FlagValue flag = new FlagValue();
                flag.key = parts[0];
                if (parts.length == 2) {
                    flag.value = parts[1];
                }
                out.flags.add(flag);
            } else {
                out.args.add(arg);
            }
        }
        return out;
    }
}

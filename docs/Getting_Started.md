_Questions? Email us at: [support@haberdashervcs.com](mailto:support@haberdashervcs.com)_

# Getting started with Haberdasher

Haberdasher is a version control system for huge repositories. By "huge" we mean huge files (up to 1GB) and millions of folders and commits. Data is stored on a central server -- ours, or you can self-host.

Generally, you check out _part_ of the repository, i.e. one or more folders. From there, the workflow is mostly like git (commit, push, merge) with some differences we'll go over.

In a nutshell, the Haberdasher workflow is:

- `hd init` to start a local repo, pointing at the server
- `hd checkout` to add a path from the server to your local repo
- `hd branch` to make a new branch off the `main` branch
- `hd commit` to save a local commit on your local branch
- `hd push` to send your local branch to the server
- `hd merge` to merge your local branch to `main` (say, after code review)


## Set-up and Installation

Sign up on the website and make sure you can log in. In your [Settings](/settings) page, generate a CLI access token and save it somewhere. You'll use it to set up your command-line client.

[Download the command-line client](/download). We don't have an installer yet, so just unzip it somewhere, and add it to your path if you like. The main executable is `hd` in the root folder of the archive.

Test it by running `hd version`.


## init

Instead of downloading the entire repo like with `git clone`, you run `hd init` to create a local repo pointing at the server:

```
$ hd init vcs.haberdashervcs.com <repo name> <CLI token from the website>

$ cd <repo name>
```

This creates a dir for your repo, which you should `cd` into. If you'd like, look inside the `.hdlocal` dir there to see what metadata is created:
- `hdlocal.conf` is a YAML file with your server configuration.
- `hdlocal.db` is a SQLite database that stores your commits, branches, files, etc.


## checkout

```
$ hd checkout /
```

"Check out" in Haberdasher means adding a path to your local repo. A newly created repo has only one path, `/`. But later on, as your repo grows, you could check out `/project1`, `/project2`, `/frontend/web_project1`, etc.

In a new repo, `/` is empty, so you won't see any new files. But you can confirm the checkout worked by running:

```
$ hd status
```


## branch

The Haberdasher workflow is:
- Do some work on a local branch.
- Push it to the server (for review or just to save it).
- Merge it into the `main` branch when you're ready.

Initially, your repo is on the main branch. (You can confirm this with `hd status`.) To start making changes, create a new branch for your work:

```
$ hd branch create <name of your branch>
```

And confirm it with `hd status`.


## commit

Add a new file with whatever contents you like. You can view the changes with:
```
$ hd diff
```

You can save your changes with:
```
$ hd commit <message>
```

Confirm the change with any or all of these:

- `hd status`
- `hd log`
- `hd diff`

Commit numbers are increasing integers. Your new repo starts at `main:1`. Branch revisions go 0, 1, 2, 3... So your `status` command should tell you you're at `name_of_your_branch:1` after a single commit.

A branch in Haberdasher has a _base commit_, which is the commit where you branched off of `main`, and a _head commit_, which is the latest commit on the branch itself.


## push

```
$ hd push
```

`push` sends your local branch to the server, where you can review it or others can download it to their own local repos. (See `sync` in "Other commands" below.)

If you go to the website, you should see the branch listed on your homepage now, where you can view its diff against `main`.


## merge

```
$ hd merge
```

When you attempt to merge your changes into `main`, the server will look for conflicting changes since the branch's base commit. If there are conflicts, you'll get an error and instructions on running `hd rebase`. (See "Other commands" below.)

If there are no conflicts, the server updates `main` with a merge commit. Then your local repo is switched to `main` at the new commit. You can see this reflected in `hd status`, in `hd log`, or on the website by browsing `main`.


## Other commands

### sync: Switch to a different revision

```
$ hd sync <branch>:<commit>
```

"Sync" is the Haberdasher verb for switching to a different revision, on this or another branch. Note that when you sync backwards in time, you won't be able to commit or push.

When you sync, the client will download anything it needs from the server, like newer revisions if you sync forward.


### rebase: Move the base commit forward on a branch

```
$ hd rebase main:<commit>
```

If you need to incorporate recent changes from main, due to a merge conflict or just to update your branch, you run `rebase`.

"Rebase" is a bit of a misnomer here, because the command will _merge in_ newer commits from main into your local branch, rather than "replaying" your branch on top of a newer main:base. (Proper replaying or "fast-forward" rebasing is on our roadmap.)

When you run `rebase` with a newer main:commit, the output will list changed files, and any conflicting merges. You'll have to resolve merge conflicts by hand by opening the conflicted files in your editor.

When you're done merging and you'd like to commit the rebase, run:
```
$ hd rebase commit
```

But if you'd rather revert the rebase and go back to your branch head, run:
```
$ hd rebase cancel
```

None of these changes are sent to the server -- you can `hd push` your updated branch when you're ready.


## Other features

### .gitignore?

`.gitignore` is replaced in Haberdasher with `hdconfig` files you can put anywhere in your repo. Each `hdconfig` file configures repo settings for files in or below its directory. Here's an example to ignore some paths:

```
---

ignoredPaths:
- '/ignorable/folder/'
- '/projects/python/*.pyc'
- '*.txt'
- '*/build/*'
```

Paths are from the root of the whole repo, starting with `/`.


### Licensing

Haberdasher is licensed as _source available_ under the [Polyform Internal Use License](https://polyformproject.org/licenses/internal-use/1.0.0/). In a nutshell, this license lets you modify and/or self-host Haberdasher for your team's internal use.

You can also [browse the HD code](/browseHd).

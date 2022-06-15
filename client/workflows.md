## Notes on workflows

Just to sketch out sample commands and figure out the local/client workflow


### init

`hd init <host> <org> <repo>`

- Results in an empty db, nothing checked out.
- Eventually this could store the user's auth credential.

**What commit should I be synced to?**: `headCommitId` from main's BranchEntry?
- This might be a problem because I use that field for take-a-number. But it's in the right ballpark.
- Then be sure to output "Synced to commit XXX [on main]".
    - Wait, when should I pick a branch?... *TODO*!!!


### checkout

This just adds a path to your local copy.

`hd checkout <path>`

- If there's no such path on this branch+commit, give an error message.
- Otherwise, everything gets synced recursively under that path.
    - The _root of the local checkout_ is the root of the whole repository. One day I might change this (as an option), but for now it just makes things easier.


### branch

`hd branch switch <branch name>`
- If local exists, sync to that.
- Otherwise, pull it from the server and sync to that, at the current commit.

`hd branch create <branch name>`
- Does NOT create it on the server.
- Is "create" the best verb?...
- Also switches to it.

??? `hd branch create_on_server <branch name>`
- Or "branch push" ?
- Should there be an explicit create-on-server step to check for a name collision?

`hd branch rename <new name>`
- In case a local branch conflicts with a server branch? Hmm...


### status

`hd status`

Displays:
- Current branch and commit
- Changed files? (from a local scan)


### sync/rebase

`hd sync <commit id>`

No local changes?:
- Update to server version at commit N on the current branch. Syncing other branches will require you to sync first.
    - So the local db _must store the current commit per branch_?

Some local changes?:
- Rebase?
- Including _setting the base commit_ on the local copy/config of the branch.


### push

`hd push`

Sends local changes to the server branch.


### commit

`hd commit '<message>'`

For local changes only.
    - Is there an index? Maybe for simplicity this just scans everything.


### log

`hd log [<path>]`

If not given, the path is `.`


### diff

`hd diff`

Scans everything in the local checkout.

`hd diff <path>`

Restricts the scan to the given path recursively.

`hd diff <commit> [<path>]`

Scans against the given commit, optionally under the given path.


### merge

`hd merge`

The command implies the current branch.

Output: `Merged into main as commit <commit id>.`

Maybe this should be done in the code review web UI, generally.


### inspect

TODO...


### Open questions

- Should I call them _versions_ on the server and _commits_ locally, to keep things distinct?


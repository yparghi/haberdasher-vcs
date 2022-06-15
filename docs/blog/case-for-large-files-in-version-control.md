#META title:Haberdasher Blog: The case for large files in version control

# The case for large files in version control

_Jan 8, 2022_

Only code goes into version control, as the wisdom goes, and code consists of text in text files. But why does this limitation need apply? Why is it _right_?

In fact there are plenty of good reasons to put large files in version control (what does "large" mean anyway?) and I'll try to give a few here.

After all, there's more to a codebase than just text. Your codebase is your whole engineering system -- whatever your programmers do from day to day, whatever decisions they make and whatever solutions they land on, need to be captured in version control.

In other words, version control should be the place for every input to your software system.


### Binary assets

If your software system is a game, it has binary assets like artwork or 3d models or music. These belong in version control -- they're first-class elements of day to day work.

There are practical problems here, for sure. You can't easily diff two versions of a sound file (I think?). But just because these problems exist in one form or another doesn't disqualify these files as versioned parts of your software system.


### Test cases

You're writing a tool to efficiently analyze huge datasets with a low memory footprint. You need a test case to validate the results of parsing a 500MB dataset. You're not just testing correctness in the output -- you're testing correctness in the software's memory management, since your test container is limited to 256MB of RAM. (Oh, and that container configuration is in version control, right?)

That test case is a part of your software system. It belongs in version control. Why should you have to use one database for some parts of the codebase, and another database for other parts?


### Build tools and dependencies

Your product is built with a certain version of Protobuf, or Yarn, or Sass, or whatever. That tool is an input to your codebase, it encapsulates a configuration decision for your software, and it may change in day to day work. It belongs in version control.

You may argue: these dependencies could be configured in a build script that downloads them, or they could be installed on developer workstations, or something like that. And that's fine if you want to do it that way -- but that's just version control with extra steps and risks. There's no reason these dependencies couldn't be included in the codebase, since they're a module of the codebase. So check them in!

By the way, you may also argue that if you're going to check in tools, it should only be to build them from source. I'm sympathetic to this argument -- but unless/until your build system is strong enough to compile (and cache) the transitive closure of every line in your codebase, I think checking in the tools directly is a reasonable practical maneuver.


### Finally: Why not?

Above all, why _not_ check large files into version control? Why does versioning need only apply to small files or text files? We don't grumble about large files in a file system, or large blobs in a database. The distinction is arbitrary and, I suspect, more a rationalization of the limitations in most VCSes, rather than a real design principle.

We don't need our engineering processes, systems, and imaginations to be limited by such a modest practical constraint. The more you treat your codebase with rigor, the more it will reward you with sane and straightforward development processes.

---

_Haberdasher is a version control system for huge repositories. Yash Parghi is its creator._


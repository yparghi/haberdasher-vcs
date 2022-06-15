#META title:Haberdasher Blog: Version control in good developer onboarding

# Version control in good developer onboarding

_June 13, 2022_

"Welcome to Company X! We're excited to have you.

For your first week, please run this list of commands, half of which will fail."

If that sounds familiar to you, then you've probably had a bad developer onboarding experience or two. And yet, many companies still take for granted that setting up a developer's environment must be tedious, or -- worse -- that the tedium is an acceptable one-time cost before the real engineering starts.

But think about the deeper costs of a long, bumpy onboarding:

- You demoralize new hires from the start and ingrain cynicism.
- You take time away from other developers on the new hire's team, who must help them debug.
- You send the message that you don't value quality of life for developers.
- You send the message that complexity is acceptable, and just "part of the job" at your company.

### Make it better: Check in your environment.

A smooth and truly educational onboarding experience requires attention to a lot of areas, like documentation, communication, mentoring, and infrastructure. But there's one powerful lever for unifying these concerns and making onboarding better as a whole: _what's in your version control_.

Imagine if a new developer at your company could, by checking out your team's project, also check out its development environment and be ready to go in the same command. Imagine if the command "give me my team's project" also gave them:

- The project's dependencies and libraries
- The project's build and deployment toolchain, including its compiler(s)
- The project's test database for a local environment

Then your new hire could set up a local test instance in minutes, not days. No `apt-get install`. No `npm install`. No `wget http://compilers.com/golang | sudo`.

Just as importantly, you can tell your new teammate, "Want to understand the project? Just look at what you checked out. That's everything, right there in that folder."

### We expect too much and invest too little.

The traditional way of managing developer environments, where the whole thing is a mishmash of layers of installations with unclear and implicit connections, is simply too complicated. Sure, a typical developer can eventually understand them all -- because _we force them to_ -- but why do we tolerate so much complexity in our field, just because we can barely manage?

When we built Haberdasher (shameless plug incoming) we built it with the design goal of freeing codebases from the assumption that "a codebase is only text files". Because it's not. A codebase is _everything that makes your software_. When you upgrade a library, you've changed your codebase. When you change your compiler version, you've changed the codebase. And all of this deserves to be versioned, and checked out, in the same way as everything else.

Every ounce of investment you put into the simplicity of developer environments is repaid with interest at every level. In the short term new hires get started quicker. In the medium term they learn more, faster. In the long term they learn to demand simplicity, consistency, and a lack of magic in how they design systems.

---

_Haberdasher is a version control system for huge repositories. Yash Parghi is its creator._


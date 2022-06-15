# Haberdasher

[www.haberdashervcs.com](https://www.haberdashervcs.com)

Haberdasher is a version control system for huge repos, bringing a Git-like workflow (local branches and commits, simple pushing and pulling) to large-scale codebases: millions of folders, millions of commits, and files up to 1GB.


## Usage

For a full walkthrough, see the [Getting Started docs](https://www.haberdashervcs.com/docs). But here's the gist:

```
$ hd init vcs.haberdashervcs.com <repo name> <CLI token from the website>
$ cd <repo name>

$ hd checkout /
$ hd status
$ hd branch create <name of your branch>

$ <make changes>
$ hd diff
$ hd commit <message>
$ hd push

$ hd merge
```


## License

Haberdasher is licensed under the [PolyForm Internal Use License](https://polyformproject.org/licenses/internal-use/1.0.0/). This is a source-available license saying you are free to use or modify Haberdasher code "for the internal business operations of you and your company".

In other words, you are free to self-host Haberdasher for your own internal use.


## Why are you hosting the code on GitHub?

> I'm aware of the irony, so don't bother pointing that out.
>
> &mdash; <cite>Sideshow Bob</cite>

We export the code here to GitHub as a mirror and a convenience.


## Contact

- [Website](https://www.haberdashervcs.com)
- [Twitter](https://twitter.com/haberdashervcs)
- [haberdasher-discuss](https://groups.google.com/g/haberdasher-discuss) Google group for the latest news and discussion

Questions? Thoughts? Email us at **hello@haberdashervcs.com**


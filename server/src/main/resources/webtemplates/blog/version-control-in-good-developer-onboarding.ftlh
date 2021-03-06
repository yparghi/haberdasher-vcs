<#assign title = "Haberdasher Blog: Version control in good developer onboarding">

<#-- DO NOT EDIT THIS FILE MANUALLY! It's generated by doc_md_to_html.py -->

<#import "/basefragments.ftlh" as basefragments>
<@basefragments.pageStart title />
<@basefragments.topNavBar />


<div id="mainDocDiv">


<h1 class="docH1">Version control in good developer onboarding</h1>

<p><span class="textItalics">June 13, 2022</span>

<p>"Welcome to Company X! We're excited to have you.

<p>For your first week, please run this list of commands, half of which will fail."

<p>If that sounds familiar to you, then you've probably had a bad developer onboarding experience or two. And yet, many companies still take for granted that setting up a developer's environment must be tedious, or -- worse -- that the tedium is an acceptable one-time cost before the real engineering starts.

<p>But think about the deeper costs of a long, bumpy onboarding:

<ul>
<li>You demoralize new hires from the start and ingrain cynicism.
<li>You take time away from other developers on the new hire's team, who must help them debug.
<li>You send the message that you don't value quality of life for developers.
<li>You send the message that complexity is acceptable, and just "part of the job" at your company.
</ul>
<h3 class="docH3">Make it better: Check in your environment.</h3>

<p>A smooth and truly educational onboarding experience requires attention to a lot of areas, like documentation, communication, mentoring, and infrastructure. But there's one powerful lever for unifying these concerns and making onboarding better as a whole: <span class="textItalics">what's in your version control</span>.

<p>Imagine if a new developer at your company could, by checking out your team's project, also check out its development environment and be ready to go in the same command. Imagine if the command "give me my team's project" also gave them:

<ul>
<li>The project's dependencies and libraries
<li>The project's build and deployment toolchain, including its compiler(s)
<li>The project's test database for a local environment
</ul>
<p>Then your new hire could set up a local test instance in minutes, not days. No <span class="textCode">apt-get install</span>. No <span class="textCode">npm install</span>. No <span class="textCode">wget http://compilers.com/golang | sudo</span>.

<p>Just as importantly, you can tell your new teammate, "Want to understand the project? Just look at what you checked out. That's everything, right there in that folder."

<h3 class="docH3">We expect too much and invest too little.</h3>

<p>The traditional way of managing developer environments, where the whole thing is a mishmash of layers of installations with unclear and implicit connections, is simply too complicated. Sure, a typical developer can eventually understand them all -- because <span class="textItalics">we force them to</span> -- but why do we tolerate so much complexity in our field, just because we can barely manage?

<p>When we built Haberdasher (shameless plug incoming) we built it with the design goal of freeing codebases from the assumption that "a codebase is only text files". Because it's not. A codebase is <span class="textItalics">everything that makes your software</span>. When you upgrade a library, you've changed your codebase. When you change your compiler version, you've changed the codebase. And all of this deserves to be versioned, and checked out, in the same way as everything else.

<p>Every ounce of investment you put into the simplicity of developer environments is repaid with interest at every level. In the short term new hires get started quicker. In the medium term they learn more, faster. In the long term they learn to demand simplicity, consistency, and a lack of magic in how they design systems.

<hr/>

<p><span class="textItalics">Haberdasher is a version control system for huge repositories. Yash Parghi is its creator.</span>



</div> <!-- mainDocDiv -->
<@basefragments.footer />
<@basefragments.pageEnd />

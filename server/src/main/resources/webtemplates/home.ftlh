<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />

<h2 class="repoHeader">Repo: ${repoName}</h2>


<#if showWelcomeMessage>
<div class="welcomeMessage">
    <h3 class="homeHeader">Welcome!</h3>
    <p>To start creating branches and pushing code, <a href="/download">download the client</a> and follow the <a href="/docs">Getting Started guide</a>.</p>
</div>
</#if>


<#list branchEntries as branchEntry>
<div class="branchSection">
    <h3 class="homeHeader">${branchEntry.branchName}</h3>

    <#if branchEntry.branchName == "main">
        <p class="branchSummary"><span class="textCode">${branchEntry.mainBranchHead}</span></p>
    <#else>
        <p class="branchSummary"><span class="textCode">${branchEntry.mainBase}</span> &rarr; <span class="textCode">${branchEntry.branchHead}</span></p>
    </#if>

    <span class="branchEntryLinks">
        <#if branchEntry.branchName != "main">
            <a href="/diff?branchName=${branchEntry.branchName?url}&atCommitId=${branchEntry.headCommitId?url}&baseCommitId=0">Diff</a>
            &middot;
        </#if>
        <a href="/log?branchName=${branchEntry.branchName?url}&path=${"/"?url}">Log</a>
        &middot;
        <a href="/browse?branchName=${branchEntry.branchName?url}&path=${"/"?url}">Browse</a>
    </span>

</div>
</#list>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />

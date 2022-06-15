<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />

<h2 class="logHeader">${repoName}: ${branchName}: Log for ${path}</h2>


<#list logEntries as logEntry>
<div class="logEntryContainer">
    <h3 class="logHeader">${logEntry.commitName}</h3>
    <ul>
        <li>Author: ${logEntry.author}</li>
        <li>Message: ${logEntry.message}</li>
        <li><a href="${logEntry.diffUrl}">Diff</a></li>
        <li><a href="/browse?branchName=${logEntry.branchName?url}&commit=${logEntry.commitId?url}&path=${"/"?url}">Browse</a></li>
    </ul>
</div>
</#list>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />

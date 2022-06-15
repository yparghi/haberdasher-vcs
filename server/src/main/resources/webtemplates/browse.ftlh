<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title=title noindex=true />

<#-- TODO: Find a better way than this to handle public browsing of the HD repo. -->
<#if username??>
<@basefragments.topNavBar />
<#else>
<@basefragments.topNavBar />
</#if>

<@basefragments.vcsContentStart />


<h2 class="browseHeader">${repoName}: ${branchName}:${commitId}</h2>
<h3 class="browsePathHeader">${path}</h3>


<div class="browseListingsContainer">
    <#list browseEntries as entry>
        <div class="browseRow">
            <span class="listingName">
                <#if entry.folder>
                    <a href="/${browsePath}?branchName=${branchName?url}&path=${(path + entry.name + "/")?url}&commitId=${commitId?url}">${entry.name}</a>
                <#else>
                    <a href="/${browsePath}?fileId=${entry.id}&path=${(path + entry.name + "/")?url}">${entry.name}</a>
                </#if>
            </span>
        </div>
    </#list>
</div>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />

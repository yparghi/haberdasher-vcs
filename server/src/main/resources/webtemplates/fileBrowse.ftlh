<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<#-- TODO: Find a better way than this to handle public browsing of the HD repo. -->
<#if username??>
    <@basefragments.topNavBar />
<#else>
    <@basefragments.topNavBar />
</#if>
<@basefragments.vcsContentStart />

<h3 class="fileContentsHeader">${path}</h3>

<div class="fileContentsContainer">
${fileContents}
</div>

<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />

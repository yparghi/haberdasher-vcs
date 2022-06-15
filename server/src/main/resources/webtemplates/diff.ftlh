<#import "basefragments.ftlh" as basefragments>

<@basefragments.pageStart title />
<@basefragments.topNavBar />
<@basefragments.vcsContentStart />

<h2 class="diffRepoHeader">${mainCommitName} &rarr; ${branchCommitName}</h2>


<#list diffFileEntries as fileEntry>
    <div class="diffFileContainer">
    <h3 class="diffFileHeader">${fileEntry.filePath}</h3>

    <#list fileEntry.diffHunkEntries as diffHunkEntry>
        <div class="diffHunkContainer">

        <#list diffHunkEntry.lineEntries as lineEntry>

            <div class="diffLineContainer" onclick="hdReview.newThreadBox(this, '${fileEntry.filePath}', '${lineEntry.lineNumberOld}', '${lineEntry.lineNumberNew}');">
                <span class="diffLineNumberOld">${lineEntry.lineNumberOld?no_esc}</span>
                <span class="diffLineNumberNew">${lineEntry.lineNumberNew?no_esc}</span>

                <#if lineEntry.type == "PLUS">
                    <span class="diffText diffTextAdd">+ ${lineEntry.lineText}</span>
                <#elseif lineEntry.type == "MINUS">
                    <span class="diffText diffTextDelete">- ${lineEntry.lineText}</span>
                <#else>
                    <span class="diffText diffTextSame">  ${lineEntry.lineText}</span>
                </#if>

                <#-- TODO: Is it faster to add/remove these with JS, instead of writing them into every line? -->
                <span class="addCommentOverlay">+ New thread</span>
            </div>

            <#list lineEntry.reviewThreads as reviewThread>
                <div class="reviewThreadContainer" data-hd-review-thread-id="${reviewThread.id}">

                    <#if reviewThread.state == "ACTIVE">

                        <#list reviewThread.comments as comment>
                            <div class="reviewCommentContainer">
                                <p>From: ${comment.author}</p>
                                <p>Comment: ${comment.text}</p>
                            </div>
                        </#list>

                        <p class="threadButtons">
                            <button type="button" onclick="hdReview.resolve(this);">Resolve</button>
                            <button type="button" onClick="hdReview.reply(this);">Reply</button>
                        </p>

                    <#else>
                        <p>Resolved: ${reviewThread.previewText}</p>
                    </#if>

                </div>
            </#list>

        </#list>

        </div> <#-- /diffHunkContainer -->
    </#list>

    </div> <#-- /diffFileContainer -->
</#list>


<@basefragments.vcsContentEnd />
<@basefragments.footer />
<@basefragments.pageEnd />

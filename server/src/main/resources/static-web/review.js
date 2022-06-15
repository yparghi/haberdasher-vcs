class HdReviewState {

    constructor() {
        this.hostUrl = window.location.origin;

        let paramStr = window.location.search;
        let urlParams = new URLSearchParams(paramStr);
        this.branchName = urlParams.get("branchName");
        this.atCommitId = urlParams.get("atCommitId");

        this.commentState = null;

        if (this.branchName === null || this.atCommitId === null) {
            throw new Error("Missing branchName or atCommitId!");
        }
    }


    lineNumberToInt(lineNumber) {
        let s = lineNumber.trim();
        if (s.length === 0) {
            return -1;
        } else {
            return parseInt(s);
        }
    }


    postComment() {
        let xhr = new XMLHttpRequest();
        xhr.open("POST", this.hostUrl + "/postReviewComment", true);
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.responseType = "text";
        xhr.onreadystatechange = function(e) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    let parsedResponse = JSON.parse(xhr.responseText);
                    console.log("Success!", parsedResponse);
                    hdReview.sealCommentBox();

                } else {
                    console.log("Error in xhr", xhr);
                }
            }
        };

        let commentText = document.getElementById("commentText").value.trim();
        let jsonDict = {
            "branchName": this.branchName,
            "atCommitId": this.atCommitId,
            "commentText": commentText,
            ...this.commentState
        };
        let jsonText = JSON.stringify(jsonDict);
        console.log("TEMP! sending text:", jsonText);
        xhr.send(jsonText);
    }


    sealCommentBox() {
        let commentDiv = document.getElementById("reviewComment");
        let threadDiv = commentDiv.closest("div.reviewThreadContainer");
        let commentText = commentDiv.querySelector("textarea#commentText").value.trim();

        commentDiv.remove();

        let sealedCommentDiv = document.createElement("div");
        sealedCommentDiv.setAttribute("class", "reviewCommentContainer");
        sealedCommentDiv.innerHTML = `
            <p>Comment: ${commentText}</p>
        `;

        let threadButtonsP = threadDiv.querySelector("p.threadButtons");
        threadButtonsP.style.display = "block";

        threadDiv.insertBefore(sealedCommentDiv, threadButtonsP);
        this.commentState = null;
    }


    resolve(buttonEl) {
        let threadDiv = buttonEl.closest("div.reviewThreadContainer");
        let threadId = threadDiv.dataset.hdReviewThreadId;

        let xhr = new XMLHttpRequest();
        xhr.open("POST", this.hostUrl + "/resolveThread", true);
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.responseType = "text";
        xhr.onreadystatechange = function(e) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    let parsedResponse = JSON.parse(xhr.responseText);
                    console.log("Success!", parsedResponse);
                    let threadButtonsP = threadDiv.querySelector("p.threadButtons");
                    threadButtonsP.style.display = "none";

                } else {
                    console.log("Error in xhr", xhr);
                }
            }
        };

        let jsonText = `{
            branchName: "${this.branchName}",
            threadId: "${threadId}"
        }`;
        xhr.send(jsonText);
    }


    reply(buttonEl) {
        let threadDiv = buttonEl.closest("div.reviewThreadContainer");
        let threadId = threadDiv.dataset.hdReviewThreadId;
        this.commentState = {
            "threadId": threadId
        };

        let threadButtonsP = threadDiv.querySelector("p.threadButtons");
        threadButtonsP.style.display = "none";

        this.addCommentBox(threadDiv);
    }


    closeCommentBox() {
        let existingCommentBox = document.getElementById("reviewComment");
        if (existingCommentBox == null) {
            return;
        }

        let threadDiv = existingCommentBox.closest("div.reviewThreadContainer");
        let threadId = threadDiv.dataset.hdReviewThreadId;

        if (threadId == null) {
            // New thread was cancelled.
            threadDiv.remove();
        } else {
            existingCommentBox.remove();
        }

        this.commentState = null;
    }


    addCommentBox(threadDiv) {
        this.closeCommentBox();

        let commentDiv = document.createElement("div");
        commentDiv.setAttribute("id", "reviewComment");
        commentDiv.innerHTML = `
            <textarea id="commentText" rows="3"></textarea>
            <p>
                <button type="button" onclick="hdReview.postComment();">Add Comment</button>
                <button type="button" onclick="hdReview.closeCommentBox();">Cancel</button>
            </p>
        `;

        threadDiv.appendChild(commentDiv);
        commentDiv.querySelector("textarea#commentText").focus();
    }


    newThreadBox(diffLineContainer, filePath, lineNumberOld, lineNumberNew) {
        filePath = filePath.trim();
        lineNumberOld = hdReview.lineNumberToInt(lineNumberOld);
        lineNumberNew = hdReview.lineNumberToInt(lineNumberNew);
        this.commentState = {
            "filePath": filePath,
            "lineNumberOld": lineNumberOld,
            "lineNumberNew": lineNumberNew
        };

        this.closeCommentBox();

        var threadDiv = document.createElement("div");
        threadDiv.setAttribute("class", "reviewThreadContainer");
        // TODO: How do I commonize this HTML with the template?
        threadDiv.innerHTML = `
            <p class="threadButtons">
                <button type="button" onclick="hdReview.resolve(this);">Resolve</button>
                <button type="button" onClick="hdReview.reply(this);">Reply</button>
            </p>
        `;
        diffLineContainer.parentNode.insertBefore(threadDiv, diffLineContainer.nextSibling);

        let threadButtonsP = threadDiv.querySelector("p.threadButtons");
        threadButtonsP.style.display = "none";
        this.addCommentBox(threadDiv);
    }

}

let hdReview = new HdReviewState();


// TODO! w.r.t. threading:
// - The comment box should be some js object? it has these attributes:
//     - line numbers old/new
//     - what file path
//     - its thread id, or null for a new thread
// - It's just inserted after a div like now, but if it's a new thread, it also needs a WRAPPING div.reviewThreadContainer...
//     - and if you cancel it, the wrapping div goes away too
//     - and if you submit it, the resolve / reply buttons are restored on the thread div?
//         - how would this work? esp. for a new thread? TODO......
//
//
// So, OPERATIONS:
// 1. new thread:
//     - Creates a new review thread container and the comment box, including HIDDEN resolve + reply buttons
//     - cancel removes the review thread container
//     - submit calls the endpoint, and on success:
//       a) converts the text to a reviewCommentContainer
//       b) sets the thread id given by the server (inc. the data- field)
//       c) sets visibility on the Resolve and Reply buttons
//
// 2. reply to thread:
// - Hide the resolve & reply buttons
// - Add a comment box to the end of the reviewCommentContainer
// - cancel removes the comment box and restores resolve & reply
// - submit converts the comment box, like in case #1. And restores resolve/reply
//     ? TODO: if it fails??
//
// 3. resolve thread:
// - Replace the div.reviewThreadContainer with a template for resolved threads
//
//
// ? use some JS class to track/implement all this?
//     ? functions generally take a div.reviewThreadContainer as their first input?
//

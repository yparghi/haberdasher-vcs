package com.haberdashervcs.server.frontend.review;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.UserAuthToken;
import com.haberdashervcs.common.protobuf.ReviewsProto;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdDatastore;
import com.haberdashervcs.server.frontend.FrontendHttpUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;


public final class DiffPageHandler {

    private static final HdLogger LOG = HdLoggers.create(DiffPageHandler.class);

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>(){}.getType();


    private final HdDatastore datastore;
    private final Configuration templateConfig;
    private final HdUserStore userStore;
    private final Map<String, String> authorUserIdToEmail;

    public DiffPageHandler(HdDatastore datastore, Configuration templateConfig, HdUserStore userStore) {
        this.datastore = datastore;
        this.templateConfig = templateConfig;
        this.userStore = userStore;
        this.authorUserIdToEmail = new HashMap<>();
    }


    public void handleDiff(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken token,
            String org,
            String repo,
            Map<String, String[]> params,
            Map<String, Object> pageData)
            throws Exception {

        String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
        if (branchName == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return;
        }

        // TODO: Set/instantiate this stuff on the handler instance, in handle() in the WebFrontend.
        RepoBrowser browser = datastore.getBrowser(org, repo).get();
        Optional<BranchEntry> branch = browser.getBranch(branchName);
        if (!branch.isPresent()) {
            FrontendHttpUtil.notFound(response);
            return;
        }

        String commitParamStr = FrontendHttpUtil.getOneUrlParam("atCommitId", params);
        if (commitParamStr == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return;
        }
        final long atBranchCommitId = Long.parseLong(commitParamStr);

        String baseCommitIdStr = FrontendHttpUtil.getOneUrlParam("baseCommitId", params);
        if (baseCommitIdStr == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return;
        }
        final long baseCommitId = Long.parseLong(baseCommitIdStr);

        Template template = templateConfig.getTemplate("diff.ftlh");
        pageData.put("title", "Haberdasher: Diff");
        pageData.put("stylesheetThisPage", "/static/diff.css");
        pageData.put("javascriptIncludesThisPage", ImmutableList.of("/static/review.js"));

        HdUser user = userStore.getUserById(token.getUserId()).get();
        pageData.put("username", user.getEmail());

        pageData.put("repoName", repo);
        pageData.put("branchName", branchName);
        pageData.put("mainCommitName", "main:" + branch.get().getBaseCommitId());
        pageData.put("branchCommitName", branchName + ":" + atBranchCommitId);

        Optional<RepoBrowser.ReviewWithOriginalBytes> oReview = browser.getReview(
                org, repo, branchName, "main", branch.get().getBaseCommitId());
        if (oReview.isEmpty()) {
            throw new IllegalStateException("No ReviewContents found for branch!: " + branchName);
        }
        ReviewsProto.ReviewContents review = oReview.get().getReview();

        ReviewDisplay reviewDisplay = ReviewDisplay.forReview(review, baseCommitId, atBranchCommitId);
        List<FileReviewDisplay> fileReviews = reviewDisplay.byFile();

        List<PageDiffFileEntry> diffFileEntries = new ArrayList<>();
        for (FileReviewDisplay fileReview : fileReviews) {
            diffFileEntries.add(new PageDiffFileEntry(
                    review,
                    browser,
                    fileReview,
                    userStore,
                    authorUserIdToEmail));
        }
        pageData.put("diffFileEntries", diffFileEntries);

        template.process(pageData, response.getWriter());
    }


   public void postReviewComment(
           Request baseRequest,
           HttpServletRequest request,
           HttpServletResponse response,
           UserAuthToken userAuthToken,
           String org,
           String repo,
           Map<String, String[]> params, Map<String, Object> pageData)
            throws IOException {

        byte[] requestBytes = request.getInputStream().readNBytes(4096);
        String requestStr = new String(requestBytes, StandardCharsets.UTF_8);
        LOG.info("TEMP: Got post comment text: %s", requestStr);
        Map<String, String> parsedJson = GSON.fromJson(requestStr, MAP_TYPE);

        String branchName = parsedJson.get("branchName");
        if (branchName == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return;
        }

        // TODO: Set/instantiate this stuff on the handler instance, in handle() in the WebFrontend.
        RepoBrowser browser = datastore.getBrowser(org, repo).get();
        Optional<BranchEntry> branch = browser.getBranch(branchName);
        if (!branch.isPresent()) {
            FrontendHttpUtil.notFound(response);
            return;
        }

        RepoBrowser.ReviewWithOriginalBytes reviewWithOriginal = browser.getReview(
                org, repo, branchName, "main", branch.get().getBaseCommitId())
                .get();
        ReviewsProto.ReviewContents review = reviewWithOriginal.getReview();
        LOG.info("TEMP: got review: %s", review);
        ReviewsProto.ReviewContents.Builder updatedReview = ReviewsProto.ReviewContents.newBuilder(review);

        if (parsedJson.containsKey("threadId")) {
            addCommentToThread(updatedReview, parsedJson, userAuthToken.getUserId());
        } else {
            addNewThread(updatedReview, parsedJson, userAuthToken.getUserId());
        }

        ReviewsProto.ReviewContents updatedProto = updatedReview.build();
        browser.updateReview(updatedProto, reviewWithOriginal);
        LOG.info("TEMP: Updated review: %s", updatedProto);

        response.setStatus(HttpStatus.OK_200);
        response.getWriter().print("{}");
    }


    private void addNewThread(
            ReviewsProto.ReviewContents.Builder toUpdate, Map<String, String> json, String userId) {
        Preconditions.checkArgument(!json.containsKey("threadId"));

        long commitId = Long.parseLong(json.get("atCommitId"));
        String filePath = json.get("filePath");
        ReviewsProto.ReviewThread.State state = ReviewsProto.ReviewThread.State.ACTIVE;
        int lineNumberOld = Integer.parseInt(json.get("lineNumberOld"));
        int lineNumberNew = Integer.parseInt(json.get("lineNumberNew"));
        String commentText = json.get("commentText");

        int lineNumber;
        ReviewsProto.ReviewThread.LineNumberType lineNumberType;
        if (lineNumberOld > 0) {
            lineNumber = lineNumberOld;
            lineNumberType = ReviewsProto.ReviewThread.LineNumberType.ORIGINAL;
        } else {
            lineNumber = lineNumberNew;
            lineNumberType = ReviewsProto.ReviewThread.LineNumberType.MODIFIED;
        }

        ReviewsProto.ReviewComment comment = ReviewsProto.ReviewComment.newBuilder()
                .setUserId(userId)
                .setText(commentText)
                .build();

        ReviewsProto.ReviewThread newThread = ReviewsProto.ReviewThread.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setCommitId(commitId)
                .setFilePath(filePath)
                .setState(state)
                .setLineNumberType(lineNumberType)
                .setLineNumber(lineNumber)
                .addComments(comment)
                .build();

        toUpdate.addThreads(newThread);
    }


    private void addCommentToThread(
            ReviewsProto.ReviewContents.Builder toUpdate, Map<String, String> json, String userId) {
        Preconditions.checkArgument(json.containsKey("threadId"));

        String threadId = json.get("threadId");
        String commentText = json.get("commentText");

        ReviewsProto.ReviewComment comment = ReviewsProto.ReviewComment.newBuilder()
                .setUserId(userId)
                .setText(commentText)
                .build();

        List<ReviewsProto.ReviewThread.Builder> mutableThreads = toUpdate.getThreadsBuilderList();
        for (ReviewsProto.ReviewThread.Builder mutableThread : mutableThreads) {
            if (threadId.equals(mutableThread.getId())) {
                mutableThread.addComments(comment);
                break;
            }
        }
    }


    public void resolveThread(
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            UserAuthToken userAuthToken,
            String org,
            String repo,
            Map<String, String[]> params, Map<String, Object> pageData)
            throws IOException {

        // TODO: Commonize this stuff.
        byte[] requestBytes = request.getInputStream().readNBytes(4096);
        String requestStr = new String(requestBytes, StandardCharsets.UTF_8);
        LOG.info("TEMP: Got resolveThread text: %s", requestStr);
        Map<String, String> parsedJson = GSON.fromJson(requestStr, MAP_TYPE);

        String branchName = parsedJson.get("branchName");
        String threadId = parsedJson.get("threadId");
        if (branchName == null || threadId == null) {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            return;
        }

        // TODO: Set/instantiate this stuff on the handler instance, in handle() in the WebFrontend.
        RepoBrowser browser = datastore.getBrowser(org, repo).get();
        Optional<BranchEntry> branch = browser.getBranch(branchName);
        if (!branch.isPresent()) {
            FrontendHttpUtil.notFound(response);
            return;
        }

        RepoBrowser.ReviewWithOriginalBytes reviewWithOriginal = browser.getReview(
                org, repo, branchName, "main", branch.get().getBaseCommitId())
                .get();
        ReviewsProto.ReviewContents review = reviewWithOriginal.getReview();
        LOG.info("TEMP: got review: %s", review);

        ReviewsProto.ReviewContents.Builder updatedReview = ReviewsProto.ReviewContents.newBuilder(review);
        List<ReviewsProto.ReviewThread.Builder> mutableThreads = updatedReview.getThreadsBuilderList();
        for (ReviewsProto.ReviewThread.Builder mutableThread : mutableThreads) {
            if (threadId.equals(mutableThread.getId())) {
                mutableThread.setState(ReviewsProto.ReviewThread.State.RESOLVED);
                break;
            }
        }

        browser.updateReview(updatedReview.build(), reviewWithOriginal);
        LOG.info("TEMP: Updated review: %s", updatedReview);

        response.setStatus(HttpStatus.OK_200);
        response.getWriter().print("{}");
    }

}

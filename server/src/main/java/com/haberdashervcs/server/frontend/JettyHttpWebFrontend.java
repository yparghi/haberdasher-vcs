package com.haberdashervcs.server.frontend;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.FileEntry;
import com.haberdashervcs.common.objects.FolderListing;
import com.haberdashervcs.common.objects.user.AuthResult;
import com.haberdashervcs.common.objects.user.HdAuthenticator;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.SignupResult;
import com.haberdashervcs.common.objects.user.SignupTaskCreationResult;
import com.haberdashervcs.common.objects.user.UserAuthToken;
import com.haberdashervcs.common.protobuf.CommitsProto;
import com.haberdashervcs.common.protobuf.TasksProto;
import com.haberdashervcs.server.browser.ContentsForDisplay;
import com.haberdashervcs.server.browser.FileBrowser;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdDatastore;
import com.haberdashervcs.server.frontend.review.DiffPageHandler;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


public class JettyHttpWebFrontend implements WebFrontend {

    private static final HdLogger LOG = HdLoggers.create(JettyHttpWebFrontend.class);


    public static JettyHttpWebFrontend forDatastore(
            HdDatastore datastore,
            HdUserStore userStore,
            HdAuthenticator authenticator,
            EmailSender emailSender,
            CustomHandler customHandler) {
        return new JettyHttpWebFrontend(datastore, userStore, authenticator, emailSender, customHandler);
    }


    private final HdDatastore datastore;
    private final HdUserStore userStore;
    private final HdAuthenticator authenticator;
    private final EmailSender emailSender;
    private final CustomHandler customHandler;

    private JettyHttpWebFrontend(
            HdDatastore datastore,
            HdUserStore userStore,
            HdAuthenticator authenticator,
            EmailSender emailSender,
            CustomHandler customHandler) {
        this.datastore = datastore;
        this.userStore = userStore;
        this.authenticator = authenticator;
        this.emailSender = emailSender;
        this.customHandler = customHandler;
    }


    @Override
    public void startInBackground() throws Exception {
        QueuedThreadPool threadPool = new QueuedThreadPool(20, 5);
        Server server = new Server(threadPool);

        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.setHost("0.0.0.0");
        httpConnector.setPort(15368);
        server.addConnector(httpConnector);

        ContextHandlerCollection allHandlers = new ContextHandlerCollection();

        URI staticResourceDir = JettyHttpWebFrontend.class.getResource("/static-web/").toURI();
        LOG.info("Resource URL / URI: %s", staticResourceDir);

        ResourceHandler staticResourceHandler = new ResourceHandler();
        staticResourceHandler.setBaseResource(Resource.newResource(staticResourceDir));
        staticResourceHandler.setDirectoriesListed(false);
        ContextHandler staticCH = new ContextHandler("/static");
        staticCH.setHandler(staticResourceHandler);

        RootHandler rootHandler = new RootHandler(datastore, userStore, authenticator, emailSender, customHandler);
        ContextHandler rootCH = new ContextHandler("/");
        rootCH.setHandler(rootHandler);
        rootCH.clearAliasChecks();
        rootCH.setAllowNullPathInfo(true); // TEMP! Remove this?

        allHandlers.addHandler(staticCH);
        allHandlers.addHandler(rootCH);

        GzipHandler gzipWrapper = new GzipHandler();
        gzipWrapper.setHandler(allHandlers);

        server.setHandler(gzipWrapper);
        server.start();
    }


    private static class RootHandler extends AbstractHandler {

        private static final HdLogger LOG = HdLoggers.create(RootHandler.class);

        private static final Splitter PATH_PART_SPLITTER = Splitter.on('/');


        private final HdDatastore datastore;
        private final HdUserStore userStore;
        private final HdAuthenticator authenticator;
        private final EmailSender emailSender;
        private final Configuration templateConfig;
        private final CustomHandler customHandler;

        private RootHandler(
                HdDatastore datastore,
                HdUserStore userStore,
                HdAuthenticator authenticator,
                EmailSender emailSender,
                CustomHandler customHandler) {
            this.datastore = datastore;
            this.userStore = userStore;
            this.authenticator = authenticator;
            this.emailSender = emailSender;
            this.customHandler = customHandler;

            this.templateConfig = new Configuration(Configuration.VERSION_2_3_31);
            templateConfig.setClassForTemplateLoading(this.getClass(), "/webtemplates");
            templateConfig.setDefaultEncoding("UTF-8");
            templateConfig.setOutputEncoding("UTF-8");
            templateConfig.setOutputFormat(HTMLOutputFormat.INSTANCE);
        }


        @Override
        public void handle(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response) {
            try {
                handleInternal(target, baseRequest, request, response);
            } catch (Throwable ex) {
                LOG.exception(ex, "Error handling request");
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                try {
                    response.getWriter().print("<html><body><p>There was an internal error, sorry!</p></body></html>");
                } catch (IOException ioEx) {
                    LOG.exception(ioEx, "Error sending 500 html");
                }
            }
        }


        private void handleInternal(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws Exception {
            baseRequest.setHandled(true);
            final String path = request.getPathInfo().substring(1);

            if (path.equals("health")) {
                response.setStatus(HttpStatus.OK_200);
                return;
            }

            LOG.info("Web: Got path: %s", path);
            Map<String, Object> pageData = new HashMap<>();
            Optional<UserAuthToken> token = getLoginToken(request);
            pageData.put("navBar_isLoggedIn", token.isPresent());

            if (path.equals("")) {
                handleLanding(baseRequest, request, response, pageData);
                return;
            }


            final List<String> parts = PATH_PART_SPLITTER.splitToList(path);
            final Map<String, String[]> params = request.getParameterMap();
            final String op = parts.get(0);


            ///// Unauthenticated pages
            // TODO: Separate unauth vs. auth explicitly into methods.
            if (op.equals("robots.txt")) {
                response.setStatus(HttpStatus.OK_200);
                Template template = templateConfig.getTemplate("robots.ftlh");
                template.process(ImmutableMap.of(), response.getWriter());
                return;

            } else if (op.equals("docs")) {
                handleDocs(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("terms")) {
                handleTerms(baseRequest, request, response, parts, params, pageData);
                return;

            } else if (op.equals("privacy")) {
                handlePrivacy(baseRequest, request, response, parts, params, pageData);
                return;

            } else if (op.equals("blog")) {
                handleBlog(baseRequest, request, response, parts, params, pageData);
                return;

            } else if (op.equals("login")) {
                handleLogin(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("resetPassword")) {
                handleResetPassword(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("requestDemo")) {
                handleRequestDemo(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("signup")) {
                handleSignup(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("signupInitiated")) {
                handleSignupInitiated(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("acceptInvitation")) {
                handleAcceptInvitation(baseRequest, request, response, params, pageData);
                return;

            } else if (op.equals("browseHd")) {
                Map<String, String[]> paramsToUse;
                if (params.isEmpty()) {
                    paramsToUse = ImmutableMap.of(
                            "branchName", new String[]{"main"},
                            "path", new String[]{"/"});
                } else {
                    paramsToUse = params;
                }
                handleBrowse(
                        baseRequest,
                        request,
                        response,
                        Optional.empty(),
                        "haberdasher",
                        "haberdasher",
                        paramsToUse,
                        pageData);
                return;

            } else if (customHandler.matches(op)) {
                customHandler.handle(op, baseRequest, request, response);
                return;
            }


            ////// Authenticated pages

            if (!token.isPresent()) {
                FrontendHttpUtil.redirectToPath(request, response, "/login");
                return;
            }

            final String org = token.get().getOrg();
            // TODO: Separate repos in an org
            final String repo = org;

            final AuthResult authResult = authenticator.canLoginToWeb(token.get(), org, repo);
            if (authResult.getType() == AuthResult.Type.AUTH_EXPIRED) {
                FrontendHttpUtil.notAuthorized(response, "Your login session has expired. Please log in again.");
                return;
            } else if (authResult.getType() == AuthResult.Type.FORBIDDEN) {
                FrontendHttpUtil.notAuthorized(response, authResult.getMessage());
                return;
            } else if (authResult.getType() != AuthResult.Type.PERMITTED) {
                throw new IllegalStateException("Unexpected Auth type!");
            }


            OrgSubscription orgSub = userStore.getSubscription(org).getSub();
            boolean requiresSub = (
                    !op.equals("settings")
                            && !op.equals("changePlan")
                            && !op.equals("logout"));

            // TODO: Different message for non-owners.
            if (requiresSub && orgSub.onExpiredFreeTrial()) {
                FrontendHttpUtil.redirectToPath(request, response, "/settings?freeTrialEnded=true");
            } else if (requiresSub && orgSub.onExpiredPaidPlan()) {
                FrontendHttpUtil.redirectToPath(request, response, "/settings?paidSubEnded=true");
            }


            switch (op) {
                case "home":
                    handleHome(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;
                case "browse":
                    handleBrowse(baseRequest, request, response, token, org, repo, params, pageData);
                    break;
                case "diff":
                    DiffPageHandler diffPageHandler = new DiffPageHandler(datastore, templateConfig, userStore);
                    diffPageHandler.handleDiff(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;
                case "postReviewComment":
                    DiffPageHandler handlerForComment = new DiffPageHandler(datastore, templateConfig, userStore);
                    handlerForComment.postReviewComment(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;
                case "resolveThread":
                    DiffPageHandler handlerForResolve = new DiffPageHandler(datastore, templateConfig, userStore);
                    handlerForResolve.resolveThread(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;
                case "log":
                    handleLog(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;
                case "settings":
                    SettingsPageHandler settingsHandler = new SettingsPageHandler(
                            datastore, templateConfig, userStore, authenticator);
                    settingsHandler.handle(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "changePlan":
                    SettingsPageHandler changePlanHandler = new SettingsPageHandler(
                            datastore, templateConfig, userStore, authenticator);
                    changePlanHandler.showChangePlan(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "doChangePlan":
                    SettingsPageHandler doChangePlanHandler = new SettingsPageHandler(
                            datastore, templateConfig, userStore, authenticator);
                    doChangePlanHandler.doChangePlan(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "saveUserSettings":
                    SettingsPageHandler saveUserSettingsHandler = new SettingsPageHandler(
                            datastore, templateConfig, userStore, authenticator);
                    saveUserSettingsHandler.saveUserSettings(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "download":
                    handleDownloads(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "logout":
                    handleLogout(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "newCliToken":
                    SettingsPageHandler newCliTokenHandler = new SettingsPageHandler(
                            datastore, templateConfig, userStore, authenticator);
                    newCliTokenHandler.handleNewCliToken(
                            baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                case "invite":
                    handleInvite(baseRequest, request, response, token.get(), org, repo, params, pageData);
                    break;

                default:
                    FrontendHttpUtil.notFound(response);
                    break;
            }
        }


        // Public for Freemarker
        public static class HomePageBranchEntry {

            private final BranchEntry branchEntry;

            private HomePageBranchEntry(BranchEntry branchEntry) {
                this.branchEntry = branchEntry;
            }

            public String getBranchName() {
                return branchEntry.getName();
            }

            public long getHeadCommitId() {
                return branchEntry.getHeadCommitId();
            }

            public String getMainBranchHead() {
                Preconditions.checkState(branchEntry.getName().equals("main"));
                return String.format("main:%d", branchEntry.getHeadCommitId());
            }

            public String getMainBase() {
                Preconditions.checkState(!branchEntry.getName().equals("main"));
                return String.format("main:%d", branchEntry.getBaseCommitId());
            }

            public String getBranchHead() {
                Preconditions.checkState(!branchEntry.getName().equals("main"));
                return String.format(
                        "%s:%d", branchEntry.getName(), branchEntry.getHeadCommitId());
            }
        }


        // TODO: Separate classes for these, including HomePageBranchEntry
        private void handleHome(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                UserAuthToken token,
                String org,
                String repo,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("home.ftlh");

            pageData.put("title", "Haberdasher: Home");
            pageData.put("stylesheetThisPage", "/static/home.css");
            pageData.put("navBar_isLoggedIn", true);

            pageData.put("repoName", repo);

            RepoBrowser browser = datastore.getBrowser(org, repo).get();
            List<BranchEntry> branchEntries = browser.getBranches();
            branchEntries = ImmutableList.sortedCopyOf(
                    (b1, b2) -> {
                        if (b1.getName().equals("main")) {
                            return -1;
                        } else if (b2.getName().equals("main")) {
                            return 1;
                        } else {
                            return b1.getName().compareTo(b2.getName());
                        }
                    },
                    branchEntries);
            pageData.put("showWelcomeMessage", (branchEntries.size() <= 1));

            List<HomePageBranchEntry> pageBranchEntries = new ArrayList<>();
            for (BranchEntry b : branchEntries) {
                pageBranchEntries.add(new HomePageBranchEntry(b));
            }
            pageData.put("branchEntries", pageBranchEntries);

            template.process(pageData, response.getWriter());
        }


        public static class LogPageEntry {

            private final CommitEntry commit;
            private final String authorEmail;

            private LogPageEntry(CommitEntry commit, String authorEmail) {
                this.commit = commit;
                this.authorEmail = authorEmail;
            }

            public String getBranchName() {
                return commit.getBranchName();
            }

            public String getCommitName() {
                return String.format("%s:%d", commit.getBranchName(), commit.getCommitId());
            }

            public String getDiffUrl() {
                String format = "/diff?branchName=%s&atCommitId=%d&baseCommitId=0";
                if (commit.getIntegration().isPresent()) {
                    CommitsProto.BranchIntegration integration = commit.getIntegration().get();
                    return String.format(
                            format,
                            FrontendHttpUtil.urlEnc(integration.getBranch()),
                            integration.getCommitId());
                } else {
                    return String.format(
                            format,
                            FrontendHttpUtil.urlEnc(commit.getBranchName()),
                            commit.getCommitId());
                }
            }

            public long getCommitId() {
                return commit.getCommitId();
            }

            public String getAuthor() {
                return authorEmail;
            }

            public String getMessage() {
                return commit.getMessage();
            }
        }


        private void handleLog(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                UserAuthToken token,
                String org,
                String repo,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("log.ftlh");

            pageData.put("title", "Haberdasher: Log");
            pageData.put("stylesheetThisPage", "/static/log.css");
            pageData.put("navBar_isLoggedIn", true);

            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String path = FrontendHttpUtil.getOneUrlParam("path", params);
            pageData.put("repoName", repo);
            pageData.put("branchName", branchName);
            pageData.put("path", path);

            RepoBrowser browser = datastore.getBrowser(org, repo).get();

            String commitIdStr = FrontendHttpUtil.getOneUrlParam("commitId", params);
            final long atCommitId;
            if (commitIdStr == null) {
                BranchEntry thisBranch = browser.getBranch(branchName).get();
                atCommitId = thisBranch.getHeadCommitId();
            } else {
                atCommitId = Long.parseLong(commitIdStr);
            }

            List<CommitEntry> logEntries = browser.getLog(branchName, path, atCommitId);
            Map<String, String> emailsByUserId = new HashMap<>();

            List<LogPageEntry> logPageEntries = new ArrayList<>();
            for (CommitEntry c : logEntries) {
                final String authorDisplayName;
                if (emailsByUserId.containsKey(c.getAuthorUserId())) {
                    authorDisplayName = emailsByUserId.get(c.getAuthorUserId());
                } else {
                    // Handle special author names like "Haberdasher Merge" and such.
                    Optional<HdUser> authorUser = userStore.getUserById(c.getAuthorUserId());
                    if (authorUser.isPresent()) {
                        authorDisplayName = authorUser.get().getEmail();
                    } else {
                        authorDisplayName = c.getAuthorUserId();
                    }
                    emailsByUserId.put(c.getAuthorUserId(), authorDisplayName);
                }
                logPageEntries.add(new LogPageEntry(c, authorDisplayName));
            }
            pageData.put("logEntries", logPageEntries);

            template.process(pageData, response.getWriter());
        }


        private void handleDownloads(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                UserAuthToken token,
                String org,
                String repo,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("downloads.ftlh");

            pageData.put("title", "Haberdasher: Downloads");
            pageData.put("stylesheetThisPage", "/static/downloads.css");
            pageData.put("navBar_isLoggedIn", true);

            template.process(pageData, response.getWriter());
        }


        private void handleLogout(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                UserAuthToken token,
                String org,
                String repo,
                Map<String, String[]> params, Map<String, Object> pageData)
                throws Exception {

            FrontendHttpUtil.setCookie(request, response, "haberdasher.web.authtoken", null);
            FrontendHttpUtil.redirectToPath(request, response, "/login");
        }


        private Optional<UserAuthToken> getLoginToken(HttpServletRequest request) throws IOException {
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals("haberdasher.web.authtoken")) {
                        String tokenId = cookie.getValue();
                        return Optional.of(authenticator.webTokenForId(tokenId));
                    }
                }
            }

            return Optional.empty();
        }


        private void handleLanding(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("landing.ftlh");
            pageData.putAll(ImmutableMap.of(
                    "title", "Haberdasher: Version control for huge repositories",
                    "stylesheetThisPage", "/static/landing/landing.css",
                    "navBar_isLanding", true));
            template.process(pageData, response.getWriter());
        }


        private void handleDocs(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("docs.ftlh");
            pageData.putAll(ImmutableMap.of(
                    "title", "Haberdasher: Overview",
                    "stylesheetThisPage", "/static/docs.css"));
            template.process(pageData, response.getWriter());
        }


        private void handleTerms(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                List<String> pathParts,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("terms.ftlh");
            pageData.putAll(ImmutableMap.of(
                    "title", "Haberdasher: Terms of service",
                    "stylesheetThisPage", "/static/docs.css"));
            template.process(pageData, response.getWriter());
        }


        private void handlePrivacy(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                List<String> pathParts,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);
            Template template = templateConfig.getTemplate("privacy.ftlh");
            pageData.putAll(ImmutableMap.of(
                    "title", "Haberdasher: Privacy policy",
                    "stylesheetThisPage", "/static/docs.css"));
            template.process(pageData, response.getWriter());
        }


        private void handleBlog(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                List<String> pathParts,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            response.setStatus(HttpStatus.OK_200);

            if (pathParts.size() == 1) {
                Template template = templateConfig.getTemplate("blog/blogMain.ftlh");
                pageData.putAll(ImmutableMap.of(
                        "title", "Haberdasher: Blog",
                        "stylesheetThisPage", "/static/docs.css"));
                template.process(pageData, response.getWriter());
                return;
            }

            String postName = pathParts.get(1);
            Template template = templateConfig.getTemplate(String.format(
                    "blog/%s.ftlh", postName));
            pageData.putAll(ImmutableMap.of(
                    "stylesheetThisPage", "/static/docs.css"));
            template.process(pageData, response.getWriter());
            return;
        }


        private void handleLogin(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Optional<UserAuthToken> token = getLoginToken(request);
            if (token.isPresent()) {
                FrontendHttpUtil.redirectToPath(request, response, "/home");
                return;
            }

            if (request.getMethod().equals("GET")) {
                response.setStatus(HttpStatus.OK_200);
                Template template = templateConfig.getTemplate("login.ftlh");
                pageData.put("title", "Haberdasher: Login");
                pageData.put("stylesheetThisPage", "/static/login.css");

                String unknownUserPass = FrontendHttpUtil.getOneUrlParam("unknownUserPass", params);
                pageData.put(
                        "unknownUserPass",
                        (unknownUserPass != null && unknownUserPass.equals("true")));

                String passwordResetSuccessful = FrontendHttpUtil.getOneUrlParam("passwordResetSuccessful", params);
                pageData.put(
                        "passwordResetSuccessful",
                        (passwordResetSuccessful != null && passwordResetSuccessful.equals("true")));

                String createUserSuccessful = FrontendHttpUtil.getOneUrlParam("createUserSuccessful", params);
                pageData.put(
                        "createUserSuccessful",
                        (createUserSuccessful != null && createUserSuccessful.equals("true")));

                String invitationAlreadyAccepted = FrontendHttpUtil.getOneUrlParam("invitationAlreadyAccepted", params);
                pageData.put(
                        "invitationAlreadyAccepted",
                        (invitationAlreadyAccepted != null && invitationAlreadyAccepted.equals("true")));

                template.process(pageData, response.getWriter());

            } else if (request.getMethod().equals("POST") && request.getHeader("Content-Type").equals("application/x-www-form-urlencoded")) {
                response.setStatus(HttpStatus.OK_200);
                String username = FrontendHttpUtil.getOneUrlParam("username", params);
                String password = FrontendHttpUtil.getOneUrlParam("password", params);
                doLogin(request, username, password, response);

            } else if (request.getMethod().equals("POST") && request.getHeader("Content-Type").equals("multipart/form-data")) {
                Collection<Part> formParts = request.getParts();
                LOG.info("Got form parts: %s", formParts);
                response.setStatus(HttpStatus.OK_200);
                response.getWriter().print("<html><body><p>Multi-part form auth TODO</p></body></html>");

            } else {
                FrontendHttpUtil.methodNotAllowed(response);
                return;
            }
        }


        // TODO: Break all this up, in its own class(es).
        private void handleResetPassword(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Optional<UserAuthToken> token = getLoginToken(request);
            if (token.isPresent()) {
                FrontendHttpUtil.redirectToPath(request, response, "/home");
                return;
            }

            if (request.getMethod().equals("GET")) {
                Template template = templateConfig.getTemplate("resetPassword.ftlh");
                pageData.put("title", "Haberdasher: Reset password");

                String resetJustSent = FrontendHttpUtil.getOneUrlParam("resetJustSent", params);
                pageData.put("resetJustSent", (resetJustSent != null && resetJustSent.equals("true")));

                String resetToken = FrontendHttpUtil.getOneUrlParam("resetToken", params);
                // TODO: Verify this token actually exists.
                if (resetToken != null) {
                    pageData.put("doActualReset", true);
                    pageData.put("resetToken", resetToken);
                }

                response.setStatus(HttpStatus.OK_200);
                template.process(pageData, response.getWriter());
                return;


            } else if (request.getMethod().equals("POST")) {
                String resetToken = FrontendHttpUtil.getOneUrlParam("resetToken", params);
                if (resetToken != null) {
                    Optional<String> password = FrontendHttpUtil.getFormParam("password", request, params);
                    Optional<String> confirmPassword = FrontendHttpUtil.getFormParam("confirmPassword", request, params);
                    if (!password.isPresent() || !confirmPassword.isPresent()) {
                        response.setStatus(HttpStatus.BAD_REQUEST_400);
                        return;
                    } else if (!password.get().equals(confirmPassword.get())) {
                        response.setStatus(HttpStatus.BAD_REQUEST_400);
                        // TODO: Improve this.
                        response.getWriter().write("<html><body><p>Passwords don't match.</p></body></html>");
                        return;
                    } else {
                        userStore.performPasswordReset(resetToken, password.get());
                        FrontendHttpUtil.redirectToPath(request, response, "/login?passwordResetSuccessful=true");
                        return;
                    }
                }


                final String email;
                if (request.getHeader("Content-Type").equals("application/x-www-form-urlencoded")) {
                    email = FrontendHttpUtil.getOneUrlParam("email", params);
                    if (email == null) {
                        response.setStatus(HttpStatus.BAD_REQUEST_400);
                        return;
                    }

                } else if (request.getHeader("Content-Type").equals("multipart/form-data")) {
                    Collection<Part> formParts = request.getParts();
                    Part part = Iterables.getOnlyElement(formParts);
                    if (part.getName().equals("email")) {
                        byte[] bytes = part.getInputStream().readAllBytes();
                        email = new String(bytes, StandardCharsets.UTF_8);
                    } else {
                        throw new IllegalArgumentException("Unexpected field");
                    }

                } else {
                    throw new UnsupportedOperationException("Unknown content type");
                }

                Optional<String> generatedResetToken = userStore.generatePasswordResetRequest(email);
                if (generatedResetToken.isPresent()) {
                    EmailSender.Result sendResult = emailSender.send(
                            WebEmails.forPasswordResetRequest(request, email, generatedResetToken.get()));
                    LOG.info("Result sending password reset email: %s", sendResult);
                }

                FrontendHttpUtil.redirectToPath(request, response, "/resetPassword?resetJustSent=true");
                return;


            } else {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
        }


        private void handleRequestDemo(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Template template = templateConfig.getTemplate("requestDemo.ftlh");
            pageData.put("title", "Haberdasher: Request Demo");
            pageData.put("stylesheetThisPage", "/static/requestDemo.css");
            FrontendHttpUtil.putBooleanUrlParam("missingFields", params, pageData);
            FrontendHttpUtil.putBooleanUrlParam("demoEmailSent", params, pageData);

            if (request.getMethod().equals("GET")) {
                response.setStatus(HttpStatus.OK_200);
                template.process(pageData, response.getWriter());
                return;

            } else if (!request.getMethod().equals("POST")) {
                FrontendHttpUtil.methodNotAllowed(response);
                return;
            }


            Optional<String> name = FrontendHttpUtil.getFormParam("name", request, params);
            Optional<String> email = FrontendHttpUtil.getFormParam("email", request, params);
            Optional<String> company = FrontendHttpUtil.getFormParam("company", request, params);
            Optional<String> interest = FrontendHttpUtil.getFormParam("interest", request, params);

            // Company is optional.
            if (!optionalStringsPresent(name, email, interest)) {
                FrontendHttpUtil.redirectToPath(request, response, "/requestDemo?missingFields=true");
                return;
            }

            Email signupEmail = WebEmails.forDemoRequest(
                    name.get(), email.get(), company.orElse("N/A"), interest.get());
            emailSender.send(signupEmail);

            FrontendHttpUtil.redirectToPath(request, response, "/requestDemo?demoEmailSent=true");
            return;
        }


        private boolean optionalStringsPresent(Optional<String>... oStrings) {
            for (Optional<String> oString : oStrings) {
                if (oString.isEmpty() || oString.get().isEmpty()) {
                    return false;
                }
            }
            return true;
        }


        private void handleSignup(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Optional<UserAuthToken> token = getLoginToken(request);
            if (token.isPresent()) {
                FrontendHttpUtil.redirectToPath(request, response, "/home");
                return;
            }

            Template template = templateConfig.getTemplate("signup.ftlh");
            pageData.put("title", "Haberdasher: Sign Up");
            pageData.put("stylesheetThisPage", "/static/signup.css");

            if (request.getMethod().equals("GET")) {
                String signupToken = FrontendHttpUtil.getOneUrlParam("signupToken", params);
                if (signupToken != null) {
                    // We can't create the repo and the user atomically, so we create the repo first since we can always
                    // add a user to the repo later.
                    TasksProto.SignupTask signupTask = userStore.getSignupTask(signupToken);
                    if (signupTask.getState() == TasksProto.SignupTask.State.COMPLETED) {
                        FrontendHttpUtil.redirectToPath(request, response, "/login?createUserSuccessful=true");
                        return;
                    }

                    String repo = signupTask.getRepoName();
                    String org = repo;
                    datastore.createRepo(org, repo);

                    SignupResult signupResult = userStore.performSignup(signupToken);
                    if (signupResult.getStatus() == SignupResult.Status.OK) {
                        FrontendHttpUtil.redirectToPath(request, response, "/login?createUserSuccessful=true");
                    } else {
                        pageData.put("errorMessages", signupResult.getErrorMessages());
                        response.setStatus(HttpStatus.OK_200);
                        template.process(pageData, response.getWriter());
                    }
                    return;

                } else {
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

            } else if (request.getMethod().equals("POST")) {
                // TODO: Put given values back into the form's text fields through the template, so that users don't
                // have to retype them if there's an error.
                Optional<String> email = FrontendHttpUtil.getFormParam("email", request, params);
                Optional<String> repoName = FrontendHttpUtil.getFormParam("repoName", request, params);
                Optional<String> password = FrontendHttpUtil.getFormParam("password", request, params);
                Optional<String> confirmPassword = FrontendHttpUtil.getFormParam("confirmPassword", request, params);
                Optional<String> agreeToTerms = FrontendHttpUtil.getFormParam("agreeToTerms", request, params);
                Optional<String> agreeToProductEmails = FrontendHttpUtil.getFormParam("agreeToProductEmails", request, params);
                if (email.isEmpty() || repoName.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                if (!password.get().equals(confirmPassword.get())) {
                    pageData.put("errorMessages", ImmutableList.of("Passwords don't match."));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;

                } else if (agreeToTerms.isEmpty() || !agreeToTerms.get().equals("yes")) {
                    pageData.put("errorMessages", ImmutableList.of("You must accept the Terms of service and Privacy policy to sign up."));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

                Optional<RepoBrowser> browser = datastore.getBrowser(repoName.get(), repoName.get());
                if (browser.isPresent()) {
                    pageData.put("errorMessages", ImmutableList.of("A repo with this name already exists."));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

                Map<String, String> metadata = new HashMap<>();
                if (agreeToProductEmails.isPresent() && agreeToProductEmails.get().equals("yes")) {
                    metadata.put("agreeToProductEmails", "true");
                } else {
                    metadata.put("agreeToProductEmails", "false");
                }

                SignupTaskCreationResult result = userStore.generateSignupTask(
                        email.get(), repoName.get(), password.get(), metadata);

                if (result.getStatus() == SignupTaskCreationResult.Status.OK) {
                    Email signupEmail = WebEmails.forSignupRequest(request, email.get(), result.getTaskToken());
                    emailSender.send(signupEmail);
                    FrontendHttpUtil.redirectToPath(request, response, "/signupInitiated");
                    return;

                } else {
                    pageData.put("errorMessages", result.getErrorMessages());
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

            } else {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
        }


        private void handleSignupInitiated(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Optional<UserAuthToken> token = getLoginToken(request);
            if (token.isPresent()) {
                FrontendHttpUtil.redirectToPath(request, response, "/home");
                return;
            }

            Template template = templateConfig.getTemplate("signupInitiated.ftlh");
            pageData.put("title", "Haberdasher: Success");
            pageData.put("stylesheetThisPage", "/static/signup.css");
            response.setStatus(HttpStatus.OK_200);
            template.process(pageData, response.getWriter());
        }


        private void doLogin(
                HttpServletRequest request,
                String username,
                String password,
                HttpServletResponse response)
                throws IOException {
            Optional<String> tokenUuid = authenticator.loginToWeb(username, password);
            if (!tokenUuid.isPresent()) {
                response.setStatus(HttpStatus.UNAUTHORIZED_401);
                FrontendHttpUtil.redirectToPath(request, response, "/login?unknownUserPass=true");
                return;
            }

            FrontendHttpUtil.setCookie(request, response, "haberdasher.web.authtoken", tokenUuid.get());
            FrontendHttpUtil.redirectToPath(request, response, "/home");
        }


        public static class PageBrowseEntry {

            private final FolderListing.Entry entry;

            public PageBrowseEntry(FolderListing.Entry entry) {
                this.entry = entry;
            }

            public String getName() {
                return entry.getName();
            }

            public String getId() {
                Preconditions.checkState(entry.getType() == FolderListing.Entry.Type.FILE);
                return entry.getId();
            }

            public boolean isFolder() {
                return (entry.getType() == FolderListing.Entry.Type.FOLDER);
            }
        }


        private void handleBrowse(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Optional<UserAuthToken> token,
                String org,
                String repo,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            RepoBrowser browser = datastore.getBrowser(org, repo).get();
            pageData.put("title", "Haberdasher: Browse");
            pageData.put("stylesheetThisPage", "/static/browse.css");
            if (token.isPresent()) {
                pageData.put("navBar_isLoggedIn", true);
            }


            String fileId = FrontendHttpUtil.getOneUrlParam("fileId", params);
            if (fileId != null) {
                String path = FrontendHttpUtil.getOneUrlParam("path", params);
                if (path == null) {
                    response.setStatus(HttpStatus.BAD_REQUEST_400);
                    return;
                }
                Template template = templateConfig.getTemplate("fileBrowse.ftlh");
                pageData.put("title", "Haberdasher: Browse");
                pageData.put("stylesheetThisPage", "/static/browse.css");

                pageData.put("path", path);

                FileEntry file = browser.getFile(fileId).get();
                FileBrowser fileBrowser = browser.browseFile(file);
                ContentsForDisplay contents = ContentsForDisplay.forContents(fileBrowser.getWholeContents());
                pageData.put("fileContents", contents.getDisplay());

                response.setStatus(HttpStatus.OK_200);
                template.process(pageData, response.getWriter());
                return;
            }


            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String path = FrontendHttpUtil.getOneUrlParam("path", params);
            if (branchName == null || path == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            Optional<BranchEntry> branch = browser.getBranch(branchName);
            if (!branch.isPresent()) {
                FrontendHttpUtil.notFound(response);
                return;
            }

            String commitIdStr = FrontendHttpUtil.getOneUrlParam("commitId", params);
            final long commitId;
            if (commitIdStr == null) {
                commitId = branch.get().getHeadCommitId();
            } else {
                commitId = Long.parseLong(commitIdStr);
            }

            Template template = templateConfig.getTemplate("browse.ftlh");

            pageData.put("repoName", repo);
            pageData.put("branchName", branchName);
            pageData.put("path", path);
            pageData.put("commitId", commitId);
            pageData.put("browsePath", (token.isPresent() ? "browse" : "browseHd"));

            Optional<FolderListing> rootListing = browser.getFolderAt(branchName, path, commitId);
            if (rootListing.isEmpty()) {
                FrontendHttpUtil.notFound(response);
                return;
            }

            List<PageBrowseEntry> browseEntries = new ArrayList<>();

            for (FolderListing.Entry entry : rootListing.get().getEntries()) {
                browseEntries.add(new PageBrowseEntry(entry));
            }
            pageData.put("browseEntries", browseEntries);

            response.setStatus(HttpStatus.OK_200);
            template.process(pageData, response.getWriter());
        }


        private void handleInvite(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                UserAuthToken token,
                String org,
                String repo,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {
            Template template = templateConfig.getTemplate("invite.ftlh");

            pageData.put("title", "Haberdasher: Invite a new author");
            pageData.put("stylesheetThisPage", "/static/invite.css");
            pageData.put("navBar_isLoggedIn", true);

            HdUser user = userStore.getUserById(token.getUserId()).get();
            // TODO: Some role-checking interface with methods like canInviteAuthor()
            if (user.getRole() != HdUser.Role.ADMIN && user.getRole() != HdUser.Role.OWNER) {
                FrontendHttpUtil.notAuthorized(response, "Invalid permissions");
                return;
            }

            OrgSubscription orgSub = userStore.getSubscription(org).getSub();
            if (!orgSub.canInviteNewUsers()) {
                FrontendHttpUtil.notAuthorized(response, "This account can't add more users.");
                return;
            }


            if (request.getMethod().equals("GET")) {
                response.setStatus(HttpStatus.OK_200);
                template.process(pageData, response.getWriter());
                return;


            } else if (request.getMethod().equals("POST")) {
                Optional<String> email = FrontendHttpUtil.getFormParam("email", request, params);
                if (email.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                HdUserStore.InvitationResult result = userStore.inviteUser(org, repo, email.get());
                if (result.getStatus() == HdUserStore.InvitationResult.Status.FAILED) {
                    pageData.put("errorMessages", ImmutableList.of(result.getMessage()));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

                Optional<String> taskTokenToSend = result.getTaskTokenToSend();
                if (taskTokenToSend.isPresent()) {
                    Email signupEmail = WebEmails.forSignupInvite(request, email.get(), repo, taskTokenToSend.get());
                    emailSender.send(signupEmail);
                }

                FrontendHttpUtil.redirectToPath(request, response, "/settings?inviteSent=true");
                return;

            } else {
                FrontendHttpUtil.methodNotAllowed(response);
                return;
            }
        }


        private void handleAcceptInvitation(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                Map<String, String[]> params,
                Map<String, Object> pageData)
                throws Exception {

            Template template = templateConfig.getTemplate("acceptInvitation.ftlh");
            pageData.put("title", "Haberdasher: Accept invitation");
            pageData.put("stylesheetThisPage", "/static/invite.css");

            String taskToken = FrontendHttpUtil.getOneUrlParam("token", params);
            if (taskToken == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            TasksProto.SignupTask task = userStore.getSignupTask(taskToken);
            if (task.getState() == TasksProto.SignupTask.State.COMPLETED) {
                FrontendHttpUtil.redirectToPath(request, response, "/login?invitationAlreadyAccepted=true");
                return;
            }

            pageData.put("repoName", task.getRepoName());
            pageData.put("inviteEmail", task.getEmail());
            pageData.put("taskTokenForFormPost", taskToken);

            if (request.getMethod().equals("GET")) {
                response.setStatus(HttpStatus.OK_200);
                template.process(pageData, response.getWriter());
                return;


            } else if (request.getMethod().equals("POST")) {

                Optional<String> password = FrontendHttpUtil.getFormParam("password", request, params);
                Optional<String> confirmPassword = FrontendHttpUtil.getFormParam("confirmPassword", request, params);
                Optional<String> agreeToTerms = FrontendHttpUtil.getFormParam("agreeToTerms", request, params);
                Optional<String> agreeToProductEmails = FrontendHttpUtil.getFormParam("agreeToProductEmails", request, params);

                if (password.isEmpty() || confirmPassword.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;

                } else if (!password.get().equals(confirmPassword.get())) {
                    pageData.put("errorMessages", ImmutableList.of("Passwords don't match."));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;

                } else if (agreeToTerms.isEmpty() || !agreeToTerms.get().equals("yes")) {
                    pageData.put("errorMessages", ImmutableList.of("You must accept the Terms of service and Privacy policy to sign up."));
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }

                Map<String, String> metadata = new HashMap<>();
                if (agreeToProductEmails.isPresent() && agreeToProductEmails.get().equals("yes")) {
                    metadata.put("agreeToProductEmails", "true");
                } else {
                    metadata.put("agreeToProductEmails", "false");
                }

                SignupResult signupResult = userStore.performInviteAcceptance(taskToken, password.get(), metadata);
                if (signupResult.getStatus() != SignupResult.Status.OK) {
                    pageData.put("errorMessages", signupResult.getErrorMessages());
                    response.setStatus(HttpStatus.OK_200);
                    template.process(pageData, response.getWriter());
                    return;
                }


                Optional<String> tokenUuid = authenticator.loginToWeb(task.getEmail(), password.get());
                if (!tokenUuid.isPresent()) {
                    response.setStatus(HttpStatus.UNAUTHORIZED_401);
                    FrontendHttpUtil.redirectToPath(request, response, "/login?unknownUserPass=true");
                } else {
                    FrontendHttpUtil.setCookie(request, response, "haberdasher.web.authtoken", tokenUuid.get());
                    FrontendHttpUtil.redirectToPath(request, response, "/home");
                }
                return;


            } else {
                FrontendHttpUtil.methodNotAllowed(response);
                return;
            }
        }

    }  // RootHandler

}

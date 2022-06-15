package com.haberdashervcs.server.frontend;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.HdObjectOutputStream;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectInputStream;
import com.haberdashervcs.common.io.ProtobufObjectOutputStream;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.BranchEntry;
import com.haberdashervcs.common.objects.CheckoutPathSet;
import com.haberdashervcs.common.objects.CommitEntry;
import com.haberdashervcs.common.objects.MergeResult;
import com.haberdashervcs.common.objects.server.ClientCheckoutSpec;
import com.haberdashervcs.common.objects.server.ServerCheckoutSpec;
import com.haberdashervcs.common.objects.user.AuthResult;
import com.haberdashervcs.common.objects.user.HdAuthenticator;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.UserAuthToken;
import com.haberdashervcs.common.protobuf.ServerProto;
import com.haberdashervcs.server.browser.RepoBrowser;
import com.haberdashervcs.server.datastore.HdDatastore;
import com.haberdashervcs.server.operations.checkout.CheckoutResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


public class JettyHttpVcsFrontend implements VcsFrontend {

    private static final HdLogger LOG = HdLoggers.create(JettyHttpVcsFrontend.class);

    private static final Splitter PATH_PARAM_SPLITTER = Splitter.on(':');

    private static final Pattern VERSION_REGEX = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final int MIN_VER_MAJOR = 1;
    private static final int MIN_VER_MINOR = 0;
    private static final int MIN_VER_PATCH = 1;


    public static JettyHttpVcsFrontend forDatastore(
            HdDatastore datastore, HdAuthenticator authenticator, HdUserStore userStore) {
        return new JettyHttpVcsFrontend(datastore, authenticator, userStore);
    }


    private final HdDatastore datastore;
    private final HdAuthenticator authenticator;
    private final HdUserStore userStore;

    private JettyHttpVcsFrontend(HdDatastore datastore, HdAuthenticator authenticator, HdUserStore userStore) {
        this.datastore = datastore;
        this.authenticator = authenticator;
        this.userStore = userStore;
    }

    @Override
    public void startInBackground() throws Exception {
        QueuedThreadPool threadPool = new QueuedThreadPool(20, 5);
        Server server = new Server(threadPool);

        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.setHost("0.0.0.0");
        // $ echo -n "haberdasher" | md5
        // 6b07689d9997423c2abd564445ac3c07
        // 3c07 as decimal: 15367
        httpConnector.setPort(15367);
        server.addConnector(httpConnector);

        ContextHandlerCollection allHandlers = new ContextHandlerCollection();

        RootHandler rootHandler = new RootHandler(datastore, authenticator, userStore);
        ContextHandler rootCH = new ContextHandler("/vcs");
        rootCH.setHandler(rootHandler);
        rootCH.clearAliasChecks();
        rootCH.setAllowNullPathInfo(true); // TEMP! Remove this?

        allHandlers.addHandler(rootCH);

        server.setHandler(allHandlers);
        server.start();
    }


    // TODO:
    // - General exception handler -- output some json envelope?
    //
    // $ curl -v 'localhost:15367/some_org/some_org/checkout?path=%2Fsomepath&commit=xxx'
    // $ curl -v 'localhost:15367/some_org/some_org/getBranch?branchName=xxx'
    private static class RootHandler extends AbstractHandler {

        private static final HdLogger LOG = HdLoggers.create(RootHandler.class);

        private static final Splitter PATH_PART_SPLITTER = Splitter.on('/');


        private final HdDatastore datastore;
        private final HdAuthenticator authenticator;
        private final HdUserStore userStore;
        private final HdObjectByteConverter byteConv;

        private RootHandler(HdDatastore datastore, HdAuthenticator authenticator, HdUserStore userStore) {
            this.datastore = datastore;
            this.authenticator = authenticator;
            this.userStore = userStore;
            // TODO: Pass this in.
            this.byteConv = ProtobufObjectByteConverter.getInstance();
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
                String messageHeader;
                if (ex.getMessage() != null) {
                    messageHeader = ex.getMessage();
                } else {
                    messageHeader = "(Unknown server error)";
                }
                response.setHeader("X-Haberdasher-Error", messageHeader);
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


            String clientVersion = request.getHeader("X-Haberdasher-Client-Version");
            if (clientVersion == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setHeader("X-Haberdasher-Error", "No X-Haberdasher-Client-Version header was given");
                return;
            } else if (!isValidClientVersion(clientVersion)) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setHeader(
                        "X-Haberdasher-Error",
                        String.format(
                                "This server only accepts clients since version %s.%s.%s. Please update to the newest version of the client.",
                                MIN_VER_MAJOR, MIN_VER_MINOR, MIN_VER_PATCH));
                return;
            }

            LOG.info("Got path: %s", path);

            final Optional<UserAuthToken> authToken;
            String authTokenId = request.getHeader("X-Haberdasher-Cli-Token");
            if (authTokenId == null) {
                notAuthorized(response, "No auth token was provided.");
                return;
            } else {
                authToken = authenticator.getCliTokenForId(authTokenId);
                if (!authToken.isPresent()) {
                    notAuthorized(response, "No such token found");
                    return;
                }
            }

            final List<String> parts = PATH_PART_SPLITTER.splitToList(path);
            final Map<String, String[]> params = request.getParameterMap();
            if (parts.size() != 3) {
                LOG.info("Got parts: %s", String.join(", ", parts));
                response.setStatus(HttpStatus.NOT_FOUND_404);
                return;
            }

            final String org = parts.get(0);
            final String repo = parts.get(1);
            final String op = parts.get(2);
            LOG.info("Org %s, Repo %s, op %s", org, repo, op);

            AuthResult authResult = authenticator.canPerformVcsOperations(authToken.get(), org, repo);
            if (authResult.getType() == AuthResult.Type.AUTH_EXPIRED) {
                // TODO: Does this ever actually happen?
                notAuthorized(response, "This auth token has expired.");
                return;
            } else if (authResult.getType() == AuthResult.Type.FORBIDDEN) {
                notAuthorized(response, authResult.getMessage());
                return;
            }

            switch (op) {
                case "checkoutQuery":
                    handleCheckoutQuery(baseRequest, request, response, org, repo, params);
                    break;
                case "checkout":
                    handleCheckout(baseRequest, request, response, org, repo, params);
                    break;
                case "pushQuery":
                    handlePushQuery(authToken.get(), baseRequest, request, response, org, repo, params);
                    break;
                case "push":
                    handlePush(authToken.get(), baseRequest, request, response, org, repo, params);
                    break;
                case "getBranch":
                    handleGetBranch(response, org, repo, params);
                    break;
                case "log":
                    handleLog(baseRequest, request, response, org, repo, params);
                    break;
                case "merge":
                    handleMerge(baseRequest, request, response, org, repo, params);
                    break;
                default:
                    notFound(response);
                    break;
            }
        }


        private boolean isValidClientVersion(String clientVersion) {
            Matcher matcher = VERSION_REGEX.matcher(clientVersion);
            if (!matcher.matches()) {
                return false;
            }
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));

            if (major > MIN_VER_MAJOR) {
                return true;
            } else if (major < MIN_VER_MAJOR) {
                return false;
            } else if (minor > MIN_VER_MINOR) {
                return true;
            } else if (minor < MIN_VER_MINOR) {
                return false;
            } else if (patch >= MIN_VER_PATCH) {
                return true;
            } else {
                return false;
            }
        }


        private void handleCheckoutQuery(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws Exception {

            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String commitId = FrontendHttpUtil.getOneUrlParam("commitId", params);
            String pathsStr = FrontendHttpUtil.getOneUrlParam("paths", params);
            if (branchName == null || commitId == null || pathsStr == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            CheckoutPathSet paths = CheckoutPathSet.fromStrings(PATH_PARAM_SPLITTER.splitToList(pathsStr));
            ServerCheckoutSpec checkoutSpec = datastore.computeCheckout(
                    org, repo, branchName, Long.parseLong(commitId), paths);
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/octet-stream");
            response.getOutputStream().write(byteConv.serverCheckoutSpecToBytes(checkoutSpec));
        }


        private void handleCheckout(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws Exception {

            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String commitId = FrontendHttpUtil.getOneUrlParam("commitId", params);
            String pathsStr = FrontendHttpUtil.getOneUrlParam("paths", params);
            if (branchName == null || commitId == null || pathsStr == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            CheckoutPathSet paths = CheckoutPathSet.fromStrings(PATH_PARAM_SPLITTER.splitToList(pathsStr));
            byte[] clientSpecBytes = request.getInputStream().readAllBytes();
            ClientCheckoutSpec clientSpec = byteConv.clientCheckoutSpecFromBytes(clientSpecBytes);

            response.setContentType("application/octet-stream");
            response.setHeader("Transfer-Encoding", "chunked");
            response.setBufferSize(16384);  // TODO: Look into whether we need this.

            HdObjectOutputStream objectsOut = ProtobufObjectOutputStream.forOutputStream(response.getOutputStream());
            CheckoutResult result = datastore.doCheckout(
                    org, repo, branchName, Long.parseLong(commitId), paths, clientSpec, objectsOut);
            if (result.getStatus() == CheckoutResult.Status.OK) {
                LOG.info("Checkout: Done ok.");
                response.setStatus(HttpStatus.OK_200);
            } else {
                LOG.info("Checkout: Failure: %s", result.getErrorMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setHeader("X-Haberdasher-Error", result.getErrorMessage());
            }
        }


        private void handlePushQuery(
                UserAuthToken token,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws Exception {
            // TODO: Use an object + byteConv.
            ServerProto.PushQuery pushQuery = ServerProto.PushQuery.parseFrom(request.getInputStream());

            OrgSubscription orgSub = userStore.getSubscription(org).getSub();
            ServerProto.PushQueryResponse pushQueryResponse = datastore.handlePushQuery(pushQuery, orgSub);

            response.setContentType("application/octet-stream");
            response.setStatus(HttpStatus.OK_200);
            response.getOutputStream().write(pushQueryResponse.toByteArray());
        }


        private void handlePush(
                UserAuthToken token,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws Exception {

            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/octet-stream");

            OrgSubscription orgSub = userStore.getSubscription(org).getSub();
            datastore.writeObjectsFromPush(
                    token.getUserId(),
                    ProtobufObjectInputStream.forInputStream(request.getInputStream()),
                    orgSub);
        }


        private void handleLog(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws IOException {

            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String path = FrontendHttpUtil.getOneUrlParam("path", params);
            String commitId = FrontendHttpUtil.getOneUrlParam("commitId", params);
            if (branchName == null || path == null || commitId == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            RepoBrowser browser = datastore.getBrowser(org, repo).get();
            List<CommitEntry> commits = browser.getLog(branchName, path, Long.valueOf(commitId));
            Map<String, String> emailsByUserId = new HashMap<>();

            response.setContentType("application/octet-stream");
            response.setStatus(HttpStatus.OK_200);
            ProtobufObjectOutputStream objectsOut = ProtobufObjectOutputStream.forOutputStream(response.getOutputStream());
            for (CommitEntry commit : commits) {
                final String displayAuthor;
                if (emailsByUserId.containsKey(commit.getAuthorUserId())) {
                    displayAuthor = emailsByUserId.get(commit.getAuthorUserId());
                } else {
                    Optional<HdUser> author = userStore.getUserById(commit.getAuthorUserId());
                    if (author.isPresent()) {
                        displayAuthor = author.get().getEmail();
                    } else {
                        displayAuthor = commit.getAuthorUserId();
                    }
                    emailsByUserId.put(commit.getAuthorUserId(), displayAuthor);
                }
                CommitEntry commitWithAuthor = commit.withAuthor(displayAuthor);
                objectsOut.writeCommit(String.valueOf(commitWithAuthor.getCommitId()), commitWithAuthor);
            }
        }


        private void handleGetBranch(
                HttpServletResponse response, String org, String repo, Map<String, String[]> params)
                throws IOException {
            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            if (branchName == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            Optional<BranchEntry> branchEntry = datastore.getBranch(org, repo, branchName);
            response.setContentType("application/octet-stream");
            if (branchEntry.isPresent()) {
                response.setStatus(HttpStatus.OK_200);
                response.getOutputStream().write(byteConv.branchToBytes(branchEntry.get()));

            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
            }
        }


        private void handleMerge(
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response,
                String org,
                String repo,
                Map<String, String[]> params)
                throws IOException {

            String branchName = FrontendHttpUtil.getOneUrlParam("branchName", params);
            String headCommitIdStr = FrontendHttpUtil.getOneUrlParam("headCommitId", params);
            if (branchName == null || headCommitIdStr == null) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                return;
            }

            MergeResult result = datastore.merge(org, repo, branchName, Long.parseLong(headCommitIdStr));

            response.setContentType("application/octet-stream");
            response.setStatus(HttpStatus.OK_200);
            ProtobufObjectOutputStream objectsOut = ProtobufObjectOutputStream.forOutputStream(
                    response.getOutputStream());
            objectsOut.writeMergeResult(result);
        }


        private void notFound(HttpServletResponse response) throws IOException {
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.getWriter().print("<html><body><p>404 Not Found</p></body></html>");
        }


        private void notAuthorized(HttpServletResponse response, String message) throws IOException {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setHeader("X-Haberdasher-Error", message);
        }
    }

}

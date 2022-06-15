package com.haberdashervcs.client.talker;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.client.ClientVersionNumber;
import com.haberdashervcs.client.commands.RepoConfig;
import com.haberdashervcs.client.localdb.LocalDb;
import com.haberdashervcs.client.localdb.objects.LocalBranchState;
import com.haberdashervcs.client.push.PushObjectSet;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.HdObjectId;
import com.haberdashervcs.common.io.HdObjectInputStream;
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
import com.haberdashervcs.common.protobuf.ServerProto;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BytesRequestContent;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.OutputStreamRequestContent;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;


public final class JettyServerTalker implements ServerTalker {

    private static final HdLogger LOG = HdLoggers.create(JettyServerTalker.class);

    private static final Joiner PATH_PARAM_JOINER = Joiner.on(':');


    public static JettyServerTalker forConfig(RepoConfig config) {
        return new JettyServerTalker(config);
    }


    private final RepoConfig config;
    private final HdObjectByteConverter byteConv;

    private JettyServerTalker(RepoConfig config) {
        this.config = config;
        // TODO: Pass this in
        this.byteConv = ProtobufObjectByteConverter.getInstance();
    }


    @Override
    public Optional<BranchEntry> getBranch(String branchName) throws Exception {
        String serverUrl = String.format(
                "%s/vcs/%s/%s/getBranch?branchName=%s",
                getHostString(), config.getOrg(), config.getRepo(), URLEncoder.encode(branchName, StandardCharsets.UTF_8));

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(serverUrl)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .send(listener);

        try {
            Response response = listener.get(10, TimeUnit.SECONDS);
            if (response.getStatus() == HttpStatus.NOT_FOUND_404) {
                return Optional.empty();

            } else if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {
                byte[] branchBytes = listener.getInputStream().readAllBytes();
                return Optional.of(byteConv.branchFromBytes(branchBytes));
            }

        } finally {
            httpClient.stop();
        }
    }


    @Override
    public ServerCheckoutSpec queryForCheckout(
            String branchName, long commitId, CheckoutPathSet allPaths)
            throws Exception {
        String serverUrl = String.format(
                "%s/vcs/%s/%s/checkoutQuery?branchName=%s&commitId=%d&paths=%s",
                getHostString(),
                config.getOrg(),
                config.getRepo(),
                urlEnc(branchName),
                commitId,
                urlEnc(PATH_PARAM_JOINER.join(allPaths.toList())));
        LOG.debug("Checkout query url: %s", serverUrl);

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(serverUrl)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .send(listener);

        try {
            Response response = listener.get(30, TimeUnit.SECONDS);
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {
                byte[] bytesForSpec = listener.getInputStream().readAllBytes();
                return byteConv.serverCheckoutSpecFromBytes(bytesForSpec);
            }

        } finally {
            httpClient.stop();
        }
    }


    @Override
    public void checkout(
            String branchName, CheckoutPathSet allPaths, long commitId, LocalDb db, ClientCheckoutSpec clientSpec)
            throws Exception {
        String serverUrl = String.format(
                "%s/vcs/%s/%s/checkout?branchName=%s&commitId=%d&paths=%s",
                getHostString(),
                config.getOrg(),
                config.getRepo(),
                urlEnc(branchName),
                commitId,
                urlEnc(PATH_PARAM_JOINER.join(allPaths.toList())));
        LOG.debug("Checkout url: %s", serverUrl);

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        byte[] clientSpecBody = byteConv.clientCheckoutSpecToBytes(clientSpec);
        httpClient.newRequest(serverUrl)
                .method(HttpMethod.POST)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .body(new BytesRequestContent("application/octet-stream", clientSpecBody))
                .send(listener);

        try {
            Response response = listener.get(2 * 60, TimeUnit.SECONDS);
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {
                HdObjectInputStream objectsIn = ProtobufObjectInputStream.forInputStream(listener.getInputStream());
                ClientCheckoutObjectHandler handler = new ClientCheckoutObjectHandler(objectsIn, db, branchName);
                handler.handle();
            }

        } finally {
            httpClient.stop();
        }
    }


    @Override
    public ServerProto.PushQueryResponse queryForPush(
            LocalBranchState localBranch,
            PushObjectSet pushQuery)
            throws Exception {
        ServerProto.PushQuery query = ServerProto.PushQuery.newBuilder()
                .setOrg(config.getOrg())
                .setRepo(config.getRepo())
                .setBranch(localBranch.getBranchName())
                .setBaseCommitId(localBranch.getBaseCommitId())
                .setNewHeadCommitId(localBranch.getHeadCommitId())
                .addAllFileIdsClientWantsToPush(pushQuery.getMaybeNewFileIds())
                .build();

        String serverUrl = String.format(
                "%s/vcs/%s/%s/pushQuery",
                getHostString(), config.getOrg(), config.getRepo());
        LOG.debug("pushQuery url: %s", serverUrl);

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(serverUrl)
                .method(HttpMethod.POST)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .body(new BytesRequestContent("application/octet-stream", query.toByteArray()))
                .send(listener);

        try {
            Response response = listener.get(10, TimeUnit.SECONDS);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {
                byte[] responseBody = listener.getInputStream().readAllBytes();
                return ServerProto.PushQueryResponse.parseFrom(responseBody);
            }
        } finally {
            httpClient.stop();
        }
    }


    @Override
    public void push(
            LocalBranchState localBranch,
            PushObjectSet objectsToPush,
            LocalDb db)
            throws Exception {

        ServerProto.PushSpec pushSpec = ServerProto.PushSpec.newBuilder()
                .setOrg(config.getOrg())
                .setRepo(config.getRepo())
                .setBranch(localBranch.getBranchName())
                .setBaseCommitId(localBranch.getBaseCommitId())
                .setNewHeadCommitId(localBranch.getHeadCommitId())
                .build();

        String serverUrl = String.format(
                "%s/vcs/%s/%s/push",
                getHostString(), config.getOrg(), config.getRepo());
        LOG.debug("push url: %s", serverUrl);

        HttpClient httpClient = new HttpClient();
        httpClient.start();
        OutputStreamRequestContent outStream = new OutputStreamRequestContent();

        try {
            InputStreamResponseListener listener = new InputStreamResponseListener();
            httpClient.newRequest(serverUrl)
                    .method("POST")
                    .headers(httpFields -> addCommonHeaders(httpFields))
                    .body(outStream)
                    .send(listener);

            HdObjectOutputStream objectsOut = ProtobufObjectOutputStream.forOutputStream(outStream.getOutputStream());
            objectsOut.writePushSpec(pushSpec);
            objectsToPush.writeToStream(objectsOut);

            outStream.close();
            Response response = listener.get(60, TimeUnit.SECONDS);
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));
            }

        } finally {
            httpClient.stop();
        }
    }


    @Override
    public List<CommitEntry> log(String branchName, String path, long atCommitId) throws Exception {
        String serverUrl = String.format(
                "%s/vcs/%s/%s/log?branchName=%s&path=%s&commitId=%d",
                getHostString(), config.getOrg(), config.getRepo(), urlEnc(branchName), urlEnc(path), atCommitId);

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(serverUrl)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .send(listener);

        try {
            Response response = listener.get(10, TimeUnit.SECONDS);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {
                ImmutableList.Builder<CommitEntry> commits = ImmutableList.builder();
                HdObjectInputStream inStream = ProtobufObjectInputStream.forInputStream(listener.getInputStream());
                Optional<HdObjectId> next;
                while ((next = inStream.next()).isPresent()) {
                    if (next.get().getType() != HdObjectId.ObjectType.COMMIT) {
                        throw new IllegalStateException(
                                "Unexpected object in the server reponse: " + next.get().getType());
                    }
                    CommitEntry commit = inStream.getCommit();
                    commits.add(commit);
                }
                return commits.build();
            }
        } finally {
            httpClient.stop();
        }
    }


    @Override
    public MergeResult merge(LocalBranchState branch) throws Exception {
        String serverUrl = String.format(
                "%s/vcs/%s/%s/merge?branchName=%s&headCommitId=%d",
                getHostString(),
                config.getOrg(),
                config.getRepo(),
                urlEnc(branch.getBranchName()),
                branch.getHeadCommitId());

        HttpClient httpClient = new HttpClient();
        httpClient.start();

        InputStreamResponseListener listener = new InputStreamResponseListener();
        httpClient.newRequest(serverUrl)
                .headers(httpFields -> addCommonHeaders(httpFields))
                .send(listener);

        try {
            Response response = listener.get(10, TimeUnit.SECONDS);

            if (response.getStatus() != HttpStatus.OK_200) {
                throw new RuntimeException(errorMessageFromResponse(response));

            } else {

                HdObjectInputStream inStream = ProtobufObjectInputStream.forInputStream(listener.getInputStream());
                Optional<HdObjectId> next = inStream.next();
                if (!next.isPresent() || next.get().getType() != HdObjectId.ObjectType.MERGE_RESULT) {
                    throw new IllegalStateException("Failed to get merge result from the server response body");
                }

                MergeResult result = inStream.getMergeResult();

                if ((next = inStream.next()).isPresent()) {
                    throw new IllegalStateException("Unexpected extra data in server merge response");
                }

                return result;
            }

        } finally {
            httpClient.stop();
        }
    }


    private static String errorMessageFromResponse(Response response) {
        String error = "Request failed with response status: " + response.getStatus();
        String reasonFromHeader = response.getHeaders().get("X-Haberdasher-Error");
        if (reasonFromHeader != null) {
            error += " / Message: " + reasonFromHeader;
        }
        return error;
    }

    // TODO: Move this into the config object?
    private String getHostString() {
        String host = config.getHost();
        if (host.startsWith("localhost")) {
            return "http://" + host;
        } else {
            return "https://" + host;
        }
    }

    // TODO: Move this into the config object?
    private String getWebsocketHostString() {
        String host = config.getHost();
        if (host.startsWith("localhost")) {
            return "ws://" + host;
        } else {
            return "wss://" + host;
        }
    }


    private String urlEnc(String in) {
        return URLEncoder.encode(in, StandardCharsets.UTF_8);
    }


    private void addCommonHeaders(HttpFields.Mutable httpFields) {
        httpFields.add("X-Haberdasher-Client-Version", ClientVersionNumber.getVersion());
        httpFields.add("X-Haberdasher-Cli-Token", config.getCliToken());
    }

}

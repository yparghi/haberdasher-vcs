package com.haberdashervcs.server.datastore.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.haberdashervcs.common.diff.HdHasher;
import com.haberdashervcs.common.io.HdObjectByteConverter;
import com.haberdashervcs.common.io.ProtobufObjectByteConverter;
import com.haberdashervcs.common.logging.HdLogger;
import com.haberdashervcs.common.logging.HdLoggers;
import com.haberdashervcs.common.objects.user.AuthResult;
import com.haberdashervcs.common.objects.user.BCrypter;
import com.haberdashervcs.common.objects.user.HdAuthenticator;
import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.HdUserWithPassword;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.common.objects.user.UserAuthToken;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


public final class HBaseAuthenticator implements HdAuthenticator {

    private static final HdLogger LOG = HdLoggers.create(HBaseAuthenticator.class);


    public static HBaseAuthenticator forConnection(Connection conn, HdUserStore userStore) {
        return new HBaseAuthenticator(conn, userStore);
    }


    private final Connection conn;
    private final HdUserStore userStore;
    private final HdObjectByteConverter byteConv;

    private HBaseAuthenticator(Connection conn, HdUserStore userStore) {
        this.conn = conn;
        this.userStore = userStore;
        this.byteConv = ProtobufObjectByteConverter.getInstance();
    }


    @Override
    public void start() throws Exception {

    }


    @Override
    public Optional<String> loginToWeb(String email, String password) throws IOException {
        Optional<HdUserWithPassword> userByEmail = userStore.getUserByEmail(email);
        if (!userByEmail.isPresent()) {
            return Optional.empty();
        }

        if (!BCrypter.passwordMatches(password, userByEmail.get().getBcryptedPassword())) {
            return Optional.empty();
        }

        HdUser user = userByEmail.get().getUser();
        String tokenUuid = UUID.randomUUID().toString();
        String tokenSha = HdHasher.sha256HashString(tokenUuid);
        UserAuthToken token = UserAuthToken.forWeb(
                tokenSha,
                user.getUserId(),
                user.getOrg(),
                System.currentTimeMillis(),
                UserAuthToken.TokenState.ACTIVE);
        putToken(tokenSha, token);
        return Optional.of(tokenUuid);
    }


    private void putToken(String tokenSha, UserAuthToken token) throws IOException {
        Table tokensTable = conn.getTable(TableName.valueOf("Tokens"));

        byte[] byTokenShaRowKey = tokenSha.getBytes(StandardCharsets.UTF_8);
        byte[] byUserAndToken = String.format("%s:%s", token.getUserId(), tokenSha)
                .getBytes(StandardCharsets.UTF_8);

        Put tokenPut = new Put(byTokenShaRowKey);
        tokenPut.addColumn(
                Bytes.toBytes("cfMain"),
                Bytes.toBytes("token"),
                byteConv.userAuthTokenToBytes(token));
        tokensTable.put(tokenPut);

        Put userAndTokenPut = new Put(byUserAndToken);
        userAndTokenPut.addColumn(
                Bytes.toBytes("cfMain"),
                Bytes.toBytes("token"),
                byteConv.userAuthTokenToBytes(token));
        tokensTable.put(userAndTokenPut);
    }


    @Override
    public UserAuthToken createCliToken(String userId, String tokenUuid) throws IOException {
        List<UserAuthToken> allActiveTokens = getActiveCliTokensForUser(userId);
        for (UserAuthToken toExpire : allActiveTokens) {
            UserAuthToken expired = toExpire.expired();
            putToken(expired.getTokenSha(), expired);
        }

        String newTokenSha = HdHasher.sha256HashString(tokenUuid);
        HdUser user = userStore.getUserById(userId).get();
        UserAuthToken token = UserAuthToken.forCli(
                newTokenSha,
                user.getUserId(),
                user.getOrg(),
                System.currentTimeMillis(),
                UserAuthToken.TokenState.ACTIVE);
        putToken(newTokenSha, token);
        return token;
    }


    @Override
    public UserAuthToken webTokenForId(String tokenUuid) throws IOException {
        Optional<UserAuthToken> token = getFromTable(tokenUuid);
        if (!token.isPresent()) {
            throw new IllegalStateException("No such token found.");
        } else if (token.get().getType() != UserAuthToken.Type.WEB) {
            throw new IllegalStateException("Invalid token");
        } else {
            return token.get();
        }
    }


    @Override
    public Optional<UserAuthToken> getCliTokenForId(String tokenUuid) throws IOException {
        Optional<UserAuthToken> token = getFromTable(tokenUuid);
        if (!token.isPresent()) {
            return Optional.empty();
        }

        if (token.get().getType() != UserAuthToken.Type.CLI) {
            throw new IllegalStateException("Expected CLI token, got type: " + token.get().getType());
        } else if (token.get().getState() == UserAuthToken.TokenState.EXPIRED) {
            return Optional.empty();
        } else {
            return token;
        }
    }


    @Override
    public Optional<UserAuthToken> getCliTokenForUser(String userId) throws IOException {
        // TODO: Can this be more efficient? Delete old token rows when new ones are made? Sort rows by timestamp?
        List<UserAuthToken> allActiveTokens = getActiveCliTokensForUser(userId);
        if (allActiveTokens.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(allActiveTokens.get(0));
        }
    }


    /**
     * Returns tokens newest to oldest.
     */
    private List<UserAuthToken> getActiveCliTokensForUser(String userId) throws IOException {
        final Table tokensTable = conn.getTable(TableName.valueOf("Tokens"));
        final String columnFamilyName = "cfMain";
        final String columnName = "token";

        String rowPrefix = String.format("%s:", userId);
        Scan scan = new Scan()
                .setRowPrefixFilter(rowPrefix.getBytes(StandardCharsets.UTF_8));

        ResultScanner scanner = tokensTable.getScanner(scan);
        Result result;
        ArrayList<UserAuthToken> out = new ArrayList<>();
        while ((result = scanner.next()) != null) {
            byte[] rowBytes = result.getValue(
                    Bytes.toBytes(columnFamilyName), Bytes.toBytes(columnName));
            UserAuthToken thisToken = byteConv.userAuthTokenFromBytes(rowBytes);
            if (thisToken.getType() != UserAuthToken.Type.CLI) {
                continue;
            } else if (thisToken.getState() == UserAuthToken.TokenState.ACTIVE) {
                out.add(thisToken);
            }
        }

        Collections.sort(out, (t1, t2) -> {
            if (t1.getCreationTimestampMillis() < t2.getCreationTimestampMillis()) {
                return 1;
            } else {
                return -1;
            }
        });
        return out;
    }


    private Optional<UserAuthToken> getFromTable(String tokenUuid) throws IOException {
        final Table tokensTable = conn.getTable(TableName.valueOf("Tokens"));
        final String columnFamilyName = "cfMain";
        String tokenSha = HdHasher.sha256HashString(tokenUuid);
        byte[] rowKey = tokenSha.getBytes(StandardCharsets.UTF_8);
        Get get = new Get(rowKey);
        Result result = tokensTable.get(get);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        byte[] value = result.getValue(
                Bytes.toBytes(columnFamilyName), Bytes.toBytes("token"));
        return Optional.of(byteConv.userAuthTokenFromBytes(value));
    }


    @Override
    public AuthResult canLoginToWeb(UserAuthToken authToken, String org, String repo) throws IOException {
        return tokenMatchesOrg(authToken, org);
    }


    @Override
    public AuthResult canPerformVcsOperations(UserAuthToken authToken, String org, String repo) throws IOException {
        OrgSubscription sub = userStore.getSubscription(org).getSub();
        if (!sub.canPerformVcsOperations()) {
            return AuthResult.of(AuthResult.Type.FORBIDDEN, "Subscription has ended.");
        }

        return tokenMatchesOrg(authToken, org);
    }


    private AuthResult tokenMatchesOrg(UserAuthToken authToken, String org) {
        if (authToken.getOrg().equals(org)) {
            return AuthResult.of(AuthResult.Type.PERMITTED, "Ok");
        } else {
            return AuthResult.of(AuthResult.Type.FORBIDDEN, "You cannot access this repo.");
        }
    }

}

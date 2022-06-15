package com.haberdashervcs.common.objects.user;

import java.io.IOException;
import java.util.Optional;


public interface HdAuthenticator {

    void start() throws Exception;

    /**
     * Returns the newly created token UUID.
     */
    Optional<String> loginToWeb(String email, String password) throws IOException;

    /**
     * Also removes/expires any existing CLI tokens for this user.
     */
    UserAuthToken createCliToken(String userId, String tokenUuid) throws IOException;

    // TODO: Should these be scoped to a user id? So that you pass a user id + token id? (username + secret)
    UserAuthToken webTokenForId(String tokenUuid) throws IOException;
    Optional<UserAuthToken> getCliTokenForId(String tokenUuid) throws IOException;
    Optional<UserAuthToken> getCliTokenForUser(String userId) throws IOException;

    AuthResult canLoginToWeb(UserAuthToken authToken, String org, String repo) throws IOException;
    AuthResult canPerformVcsOperations(UserAuthToken authToken, String org, String repo) throws IOException;

}

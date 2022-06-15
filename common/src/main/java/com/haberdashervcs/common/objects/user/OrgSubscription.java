package com.haberdashervcs.common.objects.user;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.haberdashervcs.common.protobuf.ReposProto;


public final class OrgSubscription {


    public static final class WithOriginalBytes {

        public static WithOriginalBytes of(OrgSubscription sub, byte[] originalBytes) {
            return new WithOriginalBytes(sub, originalBytes);
        }


        private final OrgSubscription sub;
        private final byte[] originalBytes;

        private WithOriginalBytes(OrgSubscription sub, byte[] originalBytes) {
            this.sub = sub;
            this.originalBytes = originalBytes;
        }

        public OrgSubscription getSub() {
            return sub;
        }

        public byte[] getOriginalBytes() {
            return Arrays.copyOf(originalBytes, originalBytes.length);
        }
    }


    public static OrgSubscription of(
            String org,
            State state,
            List<OrgUserEntry> users,
            BillingPlan billingPlan,
            ReposProto.BillingState billingState) {
        return new OrgSubscription(org, state, users, billingPlan, billingState);
    }


    public enum State {
        ON_FREE_TRIAL,
        PAID,
        GRACE_PERIOD,
        // IDEA: Do I need a distinction like "lapsed" vs. "cancelled"?
        ENDED
    }


    public enum BillingPlan {
        FREE_TRIAL,
        SMALL,
        MEDIUM,
        LARGE
    }


    public static final class OrgUserEntry {

        public static OrgUserEntry of(String org, String userId, OrgUserEntry.State state) {
            return new OrgUserEntry(org, userId, state);
        }


        public enum State {
            ACTIVE,
            REMOVED
        }


        private final String org;
        private final String userId;
        private final State state;

        private OrgUserEntry(String org, String userId, State state) {
            this.org = org;
            this.userId = userId;
            this.state = state;
        }

        public String getOrg() {
            return org;
        }

        public String getUserId() {
            return userId;
        }

        public State getState() {
            return state;
        }
    }


    private final String org;
    private final State state;
    private final List<OrgUserEntry> users;
    private final BillingPlan billingPlan;
    // TODO: Either convert this to a pojo, or pass around OrgSubscription as a proto. Maybe it's not worth it to
    // maintain a proto<->pojo pair for something like this, with nested types needing mutation.
    private final ReposProto.BillingState billingState;


    private OrgSubscription(
            String org,
            State state,
            List<OrgUserEntry> users,
            BillingPlan billingPlan,
            ReposProto.BillingState billingState) {
        this.org = org;
        this.state = state;
        this.users = ImmutableList.copyOf(users);
        this.billingPlan = billingPlan;
        this.billingState = billingState;
    }

    public String getOrg() {
        return org;
    }

    public State getState() {
        return state;
    }

    public List<OrgUserEntry> getUsers() {
        return users;
    }

    public BillingPlan getBillingPlan() {
        return billingPlan;
    }

    public ReposProto.BillingState getBillingState() {
        return billingState;
    }

    public int numActiveUsers() {
        int numActiveUsers = 0;
        for (OrgUserEntry orgUser : users) {
            if (orgUser.getState() == OrgSubscription.OrgUserEntry.State.ACTIVE) {
                numActiveUsers += 1;
            }
        }
        return numActiveUsers;
    }


    public boolean canInviteNewUsers() {
        switch (state) {
            case ON_FREE_TRIAL:
                return (numActiveUsers() < 3);

            case PAID:
                return true;

            case GRACE_PERIOD:
            case ENDED:
            default:
                return false;
        }
    }


    private static final long GIGABYTE = 1 * 1024 * 1024 * 1024;

    public long getRepoMaxSizeBytes() {
        switch (billingPlan) {
            case FREE_TRIAL:
                return 1 * GIGABYTE;
            case SMALL:
                return 50 * GIGABYTE;
            case MEDIUM:
                return 200 * GIGABYTE;
            case LARGE:
                return 1000 * GIGABYTE;
            default:
                throw new IllegalStateException("Unknown billing plan: " + billingPlan);
        }
    }


    public boolean onExpiredFreeTrial() {
        return (state == State.ON_FREE_TRIAL
                && System.currentTimeMillis() > getFreeTrialEndDateMillis());
    }

    public long getFreeTrialEndDateMillis() {
        long dayInMillis = 1000 * 60 * 60 * 24;
        String trialStartStr = billingState.getFieldsOrThrow("freeTrialStartDate");
        long trialStartMillis = Long.parseLong(trialStartStr);
        return trialStartMillis + (30 * dayInMillis);
    }

    public boolean onExpiredPaidPlan() {
        return (state == State.ENDED);
    }

    public boolean canPerformVcsOperations() {
        return (!onExpiredFreeTrial() && !onExpiredPaidPlan());
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("org", org)
                .add("state", state)
                .add("users", users)
                .add("billingPlan", billingPlan)
                .add("billingState", billingState)
                .toString();
    }

}

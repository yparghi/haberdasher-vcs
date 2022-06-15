package com.haberdashervcs.server.example;

import java.io.IOException;

import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.OrgSubscription;
import com.haberdashervcs.server.user.HdBillingManager;


final class StubBillingManager implements HdBillingManager {


    @Override
    public String getName() {
        return "stub";
    }


    @Override
    public HdUserStore.InitiateUpgradeResult initiateUpgrade(
            HdUser upgradingUser, OrgSubscription orgSub, OrgSubscription.BillingPlan newPlan)
            throws IOException {
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public HdUserStore.ChangePlanResult changeBillingPlan(
            OrgSubscription orgSub, OrgSubscription.BillingPlan newPlan)
            throws IOException {
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public HdUserStore.ChangePlanResult cancelBillingPlan(
            OrgSubscription orgSub)
            throws IOException {
        throw new UnsupportedOperationException("TODO");
    }


    @Override
    public HdUserStore.ChangePlanResult updateSubscriptionSeats(OrgSubscription updatedOrgSub) throws Exception {
        throw new UnsupportedOperationException("TODO");
    }

}

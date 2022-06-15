package com.haberdashervcs.server.user;

import com.haberdashervcs.common.objects.user.HdUser;
import com.haberdashervcs.common.objects.user.HdUserStore;
import com.haberdashervcs.common.objects.user.OrgSubscription;


public interface HdBillingManager {

    /**
     * Returns a short db-friendly name for this vendor/provider.
     */
    // TODO: Do we need 'name' in the BillingState proto? Couldn't it be some "billingManager" key in the fields?
    String getName();

    HdUserStore.InitiateUpgradeResult initiateUpgrade(
            HdUser upgradingUser, OrgSubscription orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception;

    HdUserStore.ChangePlanResult changeBillingPlan(
            OrgSubscription orgSub, OrgSubscription.BillingPlan newPlan)
            throws Exception;

    HdUserStore.ChangePlanResult cancelBillingPlan(
            OrgSubscription orgSub)
            throws Exception;

    HdUserStore.ChangePlanResult updateSubscriptionSeats(
            OrgSubscription updatedOrgSub)
            throws Exception;
}

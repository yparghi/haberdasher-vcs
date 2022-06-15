package com.haberdashervcs.server.config;

import javax.annotation.Nullable;

import com.haberdashervcs.server.datastore.HdDatastore;
import com.haberdashervcs.server.frontend.VcsFrontend;
import com.haberdashervcs.server.frontend.WebFrontend;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;


public final class HaberdasherServer {

    public static final class Builder {
        private HdDatastore datastore = null;
        private VcsFrontend vcsFrontend = null;
        private WebFrontend webFrontend = null;

        private Builder() {}

        public Builder withDatastore(HdDatastore datastore) {
            checkState(this.datastore == null);
            this.datastore = checkNotNull(datastore);
            return this;
        }

        public Builder withVcsFrontend(VcsFrontend vcsFrontend) {
            checkState(this.vcsFrontend == null);
            this.vcsFrontend = checkNotNull(vcsFrontend);
            return this;
        }

        public Builder withWebFrontend(WebFrontend webFrontend) {
            checkState(this.webFrontend == null);
            this.webFrontend = checkNotNull(webFrontend);
            return this;
        }

        public HaberdasherServer build() {
            return new HaberdasherServer(datastore, vcsFrontend, webFrontend);
        }
    }

    public static Builder builder() {
        return new Builder();
    }


    private final HdDatastore datastore;
    private final VcsFrontend vcsFrontend;
    private final @Nullable WebFrontend webFrontend;

    private HaberdasherServer(HdDatastore datastore, VcsFrontend vcsFrontend, WebFrontend webFrontend) {
        this.datastore = checkNotNull(datastore);
        this.vcsFrontend = checkNotNull(vcsFrontend);
        this.webFrontend = webFrontend;
    }

    // TODO should this be synch or asynch?
    public void start() throws Exception {
        vcsFrontend.startInBackground();
        if (webFrontend != null) {
            webFrontend.startInBackground();
        }
    }
}

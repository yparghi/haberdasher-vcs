package com.haberdashervcs.common.io;


// TODO rename this, ugh.
public class HdObjectId {

    public enum ObjectType {
        FILE,
        FOLDER,
        COMMIT,
        LARGE_FILE_CONTENTS,
        MERGE_RESULT,
        AUTH_REQUEST,
        CHECKOUT_REQUEST,
        PUSH_REQUEST,
        INFO_REQUEST,
        INFO_RESPONSE,
        PUSH_SPEC
    }


    private final ObjectType type;
    private final String id;

    public HdObjectId(ObjectType type, String id) {
        this.type = type;
        this.id = id;
    }

    public ObjectType getType() {
        return type;
    }

    public String getId() {
        return id;
    }
}

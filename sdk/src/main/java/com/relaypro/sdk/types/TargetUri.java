// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

public class TargetUri {
    private final String[] uris;

        public String[] uris() {
            return uris;
        }
        
        public TargetUri(String[] uris) {
            this.uris = uris;
        }
}

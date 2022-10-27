// Copyright © 2022 Relay Inc.

package com.relaypro.sdk.types;

/**
 * A target URI object used in most Relay API functions to send to the server.
 */
public class TargetUri {
    private final String[] uris;

        public String[] uris() {
            return uris;
        }
        
        public TargetUri(String[] uris) {
            this.uris = uris;
        }
}

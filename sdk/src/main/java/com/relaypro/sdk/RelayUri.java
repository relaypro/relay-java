package com.relaypro.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RelayUri {

    private static final Logger logger = LoggerFactory.getLogger(Relay.class);
    public static final String SCHEME = "urn";
    public static final String ROOT = "relay-resource";
    public static final String GROUP = "group";
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DEVICE = "device";
    public static final String DEVICE_PATTERN = "?device=";
    public static final String INTERACTION_URI_NAME = "urn:relay-resource:name:interaction";
    public static final String INTERACTION_URI_ID = "urn:relay-resource:id:interaction";

    public static String construct(String resourceType, String idType, String idOrName) {
        return SCHEME + ":" + ROOT + ":" + idType + ":" + resourceType + ":" + idOrName;
    }

    public static String groupId(String id) {
        return construct(GROUP, ID, URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    public static String groupName(String name) {
        return construct(GROUP, NAME, URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    public static String groupMember(String group, String device) {
        return SCHEME + ":" + ROOT + ":" + NAME + ":" + GROUP + ":" + URLEncoder.encode(group, StandardCharsets.UTF_8).replace("+", "%20") + DEVICE_PATTERN +
                URLEncoder.encode(SCHEME + ":" + ROOT + ":" + NAME + ":" + DEVICE + ":" + device, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String deviceId(String id) {
        return construct(DEVICE, ID, URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    public static String deviceName(String name) {
        return construct(DEVICE, NAME, URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    public static String parseGroupName(String uri) {
            String[] components = URLDecoder.decode(uri, StandardCharsets.UTF_8).split(":");
            if(components[2].equals(NAME) && components[3].equals(GROUP)) {
                return components[4];
            }
        return null;
    }

    public static String parseGroupId(String uri) {
        String[] components = URLDecoder.decode(uri, StandardCharsets.UTF_8).split(":");
        if(components[2].equals(ID) && components[3].equals(GROUP)) {
            return components[4];
        }
        return null;
    }

    public static String parseDeviceName(String uri) {
            String uriDecoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
            if(!isInteractionUri(uriDecoded)) {
                String[] components = uriDecoded.split(":");
                if(components[2].equals(NAME)) {
                    return components[4];
                }
            }
            else if (isInteractionUri(uriDecoded)) {
                String[] components = uriDecoded.split(":");
                if(components[2].equals(NAME) && components[6].equals(NAME)) {
                    return components[8];
                }
            }
            else {
                logger.debug("Invalid device URN.");
            }
        return null;
    }

    public static String parseDeviceId(String uri) {
            String uriDecoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
            if(!isInteractionUri(uriDecoded)) {
                String[] components = uriDecoded.split(":");
                if(components[2].equals(ID)) {
                    return components[4];
                }
            }
            else if (isInteractionUri(uriDecoded)) {
                String[] components = uriDecoded.split(":");
                if(components[2].equals(ID) && components[6].equals(ID)) {
                    return components[8];
                }
            }
            else {
                logger.debug("Invalid device URN.");
            }
        return null;
    }

    public static String parseInteraction(String uri) {
            String uriDecoded = URLDecoder.decode(uri, StandardCharsets.UTF_8);
            if(isInteractionUri(uriDecoded)) {
                String[] components = uriDecoded.split(":");
                String[] interactionName = components[4].split("\\?");
                return interactionName[0];
            }
            logger.debug("Not an interaction URN.");
        return null;
    }

    public static boolean isInteractionUri(String uri) {
        return uri.contains(INTERACTION_URI_NAME) || uri.contains(INTERACTION_URI_ID);
    }

    public static boolean isRelayUri(String uri) {
        return uri.startsWith(SCHEME + ":" + ROOT);
    }

}

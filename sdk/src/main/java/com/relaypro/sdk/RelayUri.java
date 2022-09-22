// Copyright © 2022 Relay Inc.

package com.relaypro.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utilities for working with URIs (aka URNs) for Relay devices, groups, and interactions.
 */
public class RelayUri {

    private static final Logger logger = LoggerFactory.getLogger(Relay.class);

    // The scheme used for creating a URN.
    public static final String SCHEME = "urn";

    // The root used for creating a URN.
    public static final String ROOT = "relay-resource";

    // Used to specify that the URN is for a group.
    public static final String GROUP = "group";

    // Used to specify that the URN is used for an ID.
    public static final String ID = "id";

    // Used to specify that the URN is used for a name.
    public static final String NAME = "name";

    // Used to specify that the URN is used for a device.
    public static final String DEVICE = "device";

    // Pattern used when creating an interaction URN.
    public static final String DEVICE_PATTERN = "?device=";

    // Used to specify that the URN is used for an interaction.
    public static final String INTERACTION = "interaction";

    // Beginning of  an interaction URN that uses the name of a device.
    public static final String INTERACTION_URI_NAME = "urn:relay-resource:name:interaction";

    // Beginning of an interaction URN that uses the ID of a device.
    public static final String INTERACTION_URI_ID = "urn:relay-resource:id:interaction";

    private static String construct(String resourceType, String idType, String idOrName) {
        return SCHEME + ":" + ROOT + ":" + idType + ":" + resourceType + ":" + idOrName;
    }

    /**
     * Creates a URN from a group ID.
     * @param id the ID of the group.
     * @return the newly constructed URN.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String groupId(String id) {
        return construct(GROUP, ID, URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Creates a URN from a group name.
     * @param name the name of the group.
     * @return the newly constructed URN.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String groupName(String name) {
        return construct(GROUP, NAME, URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Creates a URN for a group member.
     * @param group the name of the group that the device belongs to.
     * @param device the newly constructed URN.
     * @return
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String groupMember(String group, String device) {
        return SCHEME + ":" + ROOT + ":" + NAME + ":" + GROUP + ":" + URLEncoder.encode(group, StandardCharsets.UTF_8).replace("+", "%20") + DEVICE_PATTERN +
                URLEncoder.encode(SCHEME + ":" + ROOT + ":" + NAME + ":" + DEVICE + ":" + device, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Creates a URN from a device ID.
     * @param id the ID of the device.
     * @return the newly constructed URN.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String deviceId(String id) {
        return construct(DEVICE, ID, URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Creates a URN from a device name.
     * @param name the name of the device.
     * @return the newly constructed URN.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String deviceName(String name) {
        return construct(DEVICE, NAME, URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Creates a URN from an interaction name.
     * @param name the name of the interaction.
     * @return the newly constructed URN.
     */
    public static String interactionName(String name) {
        return construct(INTERACTION, NAME, URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Parses out a group name from a group URN.
     * @param uri the URN that you would like to extract the group name from.
     * @return
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String parseGroupName(String uri) {
            String[] components = URLDecoder.decode(uri, StandardCharsets.UTF_8).split(":");
            if(components[2].equals(NAME) && components[3].equals(GROUP)) {
                return components[4];
            }
        return null;
    }

    /**
     * Parses out a group ID from a group URN.
     * @param uri the URN that you would like to extract the group ID from.
     * @return the group ID.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static String parseGroupId(String uri) {
        String[] components = URLDecoder.decode(uri, StandardCharsets.UTF_8).split(":");
        if(components[2].equals(ID) && components[3].equals(GROUP)) {
            return components[4];
        }
        return null;
    }

    /**
     * Parses out a device name from a device or interaction URN.
     * @param uri the device or interaction URN that you would liek to extract the device name from.
     * @return the device name.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
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

    /**
     * Parses out a device ID from a device or interaction URN.
     * @param uri the device or interaction URN that you would like to extract the device ID from.
     * @return the device ID.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
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

    /**
     * Parses out the name of an interaction from an interaction URN.
     * @param uri the interaction URN that you would like to parse the interaction from.
     * @return the name of the interaction.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
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

    /**
     * Checks if the URN is for an interaction.
     * @param uri the device, group, or interaction URN.
     * @return true if the URN is a Relay URN, false otherwise.
     */
    public static boolean isInteractionUri(String uri) {
        return uri.contains(INTERACTION_URI_NAME) || uri.contains(INTERACTION_URI_ID);
    }

    /**
     * Checks if the URN is a Relay URN.
     * @param uri the device, group, or interaction URN.
     * @return true if the URN is a Relay URN, false otherwise.
     */
    @SuppressWarnings({"unused", "UnusedReturnValue"})
    public static boolean isRelayUri(String uri) {
        return uri.startsWith(SCHEME + ":" + ROOT);
    }

}
